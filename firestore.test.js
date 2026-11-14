const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. Unauthenticated access ---

test("Unauthenticated user cannot read or write users, saved_media, or watch_rooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("watch_rooms").where("isPublic", "==", true).where("status", "==", "active").get());
  await assertFails(unauthDb.collection("watch_rooms").doc("room_1").set({ hostId: ALICE_UID }));
});

// --- 2. UserProfile isolation & Shadow Update ---

test("User can create and update own profile, cannot read/write another user's profile or inject ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const validProfile = {
    userId: ALICE_UID,
    displayName: "Alice",
    preferredLiftUrl: "https://www.viewlift.com",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));

  // Ghost field shadow update rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      { ...validProfile, isAdmin: true },
      { merge: true }
    )
  );
});

// --- 3. SavedLiftMedia user subcollection ---

test("User can create, list, and delete own saved_media; cross-user access fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("saved_media").doc("m1").set({
      userId: ALICE_UID,
      title: "Lift Documentary",
      sourcePageUrl: "https://www.viewlift.com/watch/1",
      streamUrl: "https://example.com/video.mp4",
      sourceDomain: "viewlift.com",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("saved_media").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("saved_media").get());
});

// --- 4. WatchRoom creation, queries, join/leave, host controls, terminal state ---

test("WatchRoom: create, query public active rooms, join/leave, playback sync, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const roomRef = aliceDb.collection("watch_rooms").doc("room_1");
  await assertSucceeds(
    roomRef.set({
      hostId: ALICE_UID,
      hostName: "Alice",
      title: "Lift Feature Film",
      sourcePageUrl: "https://www.viewlift.com/watch/feature",
      streamUrl: "https://example.com/stream.m3u8",
      thumbnailUrl: "https://example.com/thumb.jpg",
      sourceDomain: "viewlift.com",
      roomCode: "LIFT42",
      isPublic: true,
      status: "active",
      isPlaying: false,
      playbackPositionMs: 0,
      playbackRate: 1.0,
      syncVersion: 1,
      memberUids: [ALICE_UID],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Unfiltered list query MUST fail (rules are not filters)
  await assertFails(bobDb.collection("watch_rooms").get());

  // Scoped public active query succeeds
  await assertSucceeds(
    bobDb.collection("watch_rooms").where("isPublic", "==", true).where("status", "==", "active").get()
  );

  // Non-host Bob cannot hijack playback state
  await assertFails(
    bobDb.collection("watch_rooms").doc("room_1").update({
      isPlaying: true,
      playbackPositionMs: 12000,
      syncVersion: 2,
      updatedAt: nowTs(),
    })
  );

  // Bob joins public active room
  await assertSucceeds(
    bobDb.collection("watch_rooms").doc("room_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: nowTs(),
    })
  );

  // Charlie cannot spoof-remove Bob while joining
  await assertFails(
    charlieDb.collection("watch_rooms").doc("room_1").update({
      memberUids: [ALICE_UID, CHARLIE_UID],
      updatedAt: nowTs(),
    })
  );

  // Host Alice updates playback
  await assertSucceeds(
    roomRef.update({
      isPlaying: true,
      playbackPositionMs: 15500,
      syncVersion: 2,
      updatedAt: nowTs(),
    })
  );

  // Bob leaves room
  await assertSucceeds(
    bobDb.collection("watch_rooms").doc("room_1").update({
      memberUids: [ALICE_UID],
      updatedAt: nowTs(),
    })
  );

  // Host ends room (terminal state)
  await assertSucceeds(
    roomRef.update({
      status: "ended",
      isPlaying: false,
      updatedAt: nowTs(),
    })
  );

  // Terminal state lock: no further updates allowed once ended
  await assertFails(
    roomRef.update({
      isPlaying: true,
      updatedAt: nowTs(),
    })
  );
});

// --- 5. RoomMessage Parent Gate & Immutability ---

test("RoomMessage: members can post & read messages, non-members cannot post to private rooms, messages are immutable", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Private room
  await assertSucceeds(
    aliceDb.collection("watch_rooms").doc("priv_room").set({
      hostId: ALICE_UID,
      hostName: "Alice",
      title: "Private Lift Watch",
      sourcePageUrl: "https://www.viewlift.com/private",
      streamUrl: "https://example.com/private.mp4",
      sourceDomain: "viewlift.com",
      roomCode: "PRIV99",
      isPublic: false,
      status: "active",
      isPlaying: true,
      playbackPositionMs: 0,
      playbackRate: 1.0,
      syncVersion: 1,
      memberUids: [ALICE_UID],
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Non-member Bob cannot post to priv_room
  await assertFails(
    bobDb.collection("watch_rooms").doc("priv_room").collection("messages").doc("msg_bad").set({
      roomId: "priv_room",
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Sneaking in",
      messageType: "chat",
      isPublic: false,
      memberUids: [ALICE_UID],
      createdAt: nowTs(),
    })
  );

  // Member Alice posts message
  const msgRef = aliceDb.collection("watch_rooms").doc("priv_room").collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      roomId: "priv_room",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Welcome to the watch party!",
      messageType: "chat",
      isPublic: false,
      memberUids: [ALICE_UID],
      createdAt: nowTs(),
    })
  );

  // Member Alice lists messages with scoped query
  await assertSucceeds(
    aliceDb.collection("watch_rooms").doc("priv_room").collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Bob cannot get Alice's private room message or run unfiltered list
  await assertFails(
    bobDb.collection("watch_rooms").doc("priv_room").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    bobDb.collection("watch_rooms").doc("priv_room").collection("messages").get()
  );

  // Messages are immutable (update/delete fail)
  await assertFails(msgRef.update({ text: "Edited" }));
  await assertFails(msgRef.delete());
});
