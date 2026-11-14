package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.LiftMediaItem
import com.example.data.model.RoomMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class WatchPartyRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun userProfile_and_savedMedia_isolatedPerUser() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = WatchPartyRepository(firestore, auth)

        val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", null, "https://www.viewlift.com")
        }
        assertTrue(profileResult.isSuccess)

        val mediaId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveLiftMedia(
                LiftMediaItem(
                    id = UUID.randomUUID().toString(),
                    title = "Lift Showcase",
                    description = "Sample fetched Lift item",
                    sourcePageUrl = "https://www.viewlift.com/watch/showcase",
                    streamUrl = "https://example.com/showcase.m3u8",
                    thumbnailUrl = "https://example.com/thumb.jpg",
                    sourceDomain = "viewlift.com",
                )
            ).getOrThrow()
        }

        val savedList = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeSavedMedia().first { it.any { item -> item.id == mediaId } }
        }
        assertTrue(savedList.any { it.id == mediaId })

        // Cross-user isolation: Bob has his own empty saved_media list and cannot read Alice's
        signInTestUser(BOB_EMAIL)
        val bobRepo = WatchPartyRepository(firestore, auth)
        val bobSaved = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeSavedMedia().first()
        }
        assertTrue(bobSaved.none { it.id == mediaId })
    }

    @Test
    fun watchRoom_createJoinSyncMessageAndEnd_enforcesRules() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = WatchPartyRepository(firestore, auth)

        val room = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createWatchRoom(
                title = "Lift Live Premiere ${UUID.randomUUID().toString().take(6)}",
                sourcePageUrl = "https://www.viewlift.com/watch/premiere",
                streamUrl = "https://example.com/premiere.m3u8",
                thumbnailUrl = "",
                sourceDomain = "viewlift.com",
                hostName = "Alice",
                isPublic = true,
            ).getOrThrow()
        }

        // Bob can discover public active room and join
        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = WatchPartyRepository(firestore, auth)

        val publicRooms = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observePublicActiveRooms().first { list -> list.any { it.id == room.id } }
        }
        assertTrue(publicRooms.any { it.id == room.id })

        val joined = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinRoom(room.id).getOrThrow()
        }
        assertTrue(bobUid in joined.memberUids)

        // Non-host Bob cannot sync playback (host-only action)
        val nonHostSync = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.syncPlayback(room.id, isPlaying = false, playbackPositionMs = 9999L, playbackRate = 1.0, nextSyncVersion = 2L)
        }
        assertTrue(nonHostSync.isFailure)
        val syncEx = nonHostSync.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, syncEx?.code)

        // Joined member Bob can send chat message
        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.sendMessage(
                roomId = room.id,
                senderName = "Bob",
                text = "Awesome stream!",
                messageType = RoomMessage.TYPE_CHAT,
                isPublic = true,
                memberUids = joined.memberUids,
            ).getOrThrow()
        }
        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeMessages(room.id, isPublic = true).first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.text == "Awesome stream!" })

        // Host Alice can sync playback and end room
        signInTestUser(ALICE_EMAIL)
        val hostSync = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.syncPlayback(room.id, isPlaying = true, playbackPositionMs = 45000L, playbackRate = 1.0, nextSyncVersion = 2L)
        }
        assertTrue(hostSync.isSuccess)

        val endResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.endRoom(room.id)
        }
        assertTrue(endResult.isSuccess)

        // Ended room is locked against further updates
        val postEndSync = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.syncPlayback(room.id, isPlaying = false, playbackPositionMs = 50000L, playbackRate = 1.0, nextSyncVersion = 3L)
        }
        assertTrue(postEndSync.isFailure)
        val postEndEx = postEndSync.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, postEndEx?.code)
    }

    @Test
    fun observePublicActiveRooms_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = WatchPartyRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observePublicActiveRooms().first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertTrue(caught is IllegalStateException || caught?.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_lift@test.com"
        const val BOB_EMAIL = "bob_lift@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
