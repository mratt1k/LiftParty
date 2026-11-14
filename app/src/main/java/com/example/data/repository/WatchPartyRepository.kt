package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.LiftMediaItem
import com.example.data.model.RoomMessage
import com.example.data.model.SavedLiftMedia
import com.example.data.model.UserProfile
import com.example.data.model.WatchRoom
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WatchPartyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    // --- User Profile ---

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        photoUrl: String?,
        preferredLiftUrl: String = UserProfile.DEFAULT_LIFT_URL,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val safeName = displayName.trim().ifBlank { "Viewer" }.take(50)
        val safeLiftUrl = preferredLiftUrl.trim().ifBlank { UserProfile.DEFAULT_LIFT_URL }.take(300)
        val safePhoto = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    put("preferredLiftUrl", safeLiftUrl)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (safePhoto != null) put("photoUrl", safePhoto)
                }
                docRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("displayName", safeName)
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (safePhoto != null) put("photoUrl", safePhoto)
                }
                docRef.update(updates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updatePreferredLiftUrl(preferredLiftUrl: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val cleanUrl = preferredLiftUrl.trim().take(300)
        require(cleanUrl.isNotEmpty()) { "Lift URL cannot be empty." }
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val displayName = auth.currentUser?.displayName?.take(50)?.ifBlank { "Viewer" } ?: "Viewer"
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "preferredLiftUrl" to cleanUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "preferredLiftUrl" to cleanUrl,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Saved Lift Media ---

    fun observeSavedMedia(): Flow<List<SavedLiftMedia>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("saved_media")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SavedLiftMedia::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveLiftMedia(item: LiftMediaItem): Result<String> = runCatching {
        val uid = requireUserId()
        val mediaId = "media_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("users").document(uid).collection("saved_media").document(mediaId)
        val payload = buildMap<String, Any> {
            put("userId", uid)
            put("title", item.title.trim().ifBlank { "Lift Media" }.take(160))
            put("sourcePageUrl", item.sourcePageUrl.trim().take(600))
            put("sourceDomain", item.sourceDomain.trim().ifBlank { "lift" }.take(120))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
            if (item.description.isNotBlank()) put("description", item.description.trim().take(500))
            if (item.streamUrl.isNotBlank()) put("streamUrl", item.streamUrl.trim().take(1000))
            if (item.thumbnailUrl.isNotBlank()) put("thumbnailUrl", item.thumbnailUrl.trim().take(600))
        }
        try {
            docRef.set(payload).await()
            mediaId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun removeSavedMedia(mediaId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(mediaId.isNotBlank()) { "Media ID cannot be blank." }
        val docRef = db.collection("users").document(uid).collection("saved_media").document(mediaId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Watch Party Rooms ---

    fun observePublicActiveRooms(): Flow<List<WatchRoom>> = flow {
        val uid = requireUserId()
        val query = db.collection("watch_rooms")
            .whereEqualTo("isPublic", true)
            .whereEqualTo("status", WatchRoom.STATUS_ACTIVE)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "watch_rooms")
                    }
                    throw error
                }
        )
    }

    fun observeMyRooms(): Flow<List<WatchRoom>> = flow {
        val uid = requireUserId()
        val query = db.collection("watch_rooms")
            .whereArrayContains("memberUids", uid)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WatchRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "watch_rooms")
                    }
                    throw error
                }
        )
    }

    fun observeRoom(roomId: String): Flow<WatchRoom?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("watch_rooms").document(roomId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            WatchRoom::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun createWatchRoom(
        title: String,
        sourcePageUrl: String,
        streamUrl: String,
        thumbnailUrl: String,
        sourceDomain: String,
        hostName: String,
        isPublic: Boolean = true,
    ): Result<WatchRoom> = runCatching {
        val uid = requireUserId()
        val roomId = "room_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val roomCode = generateRoomCode()
        val docRef = db.collection("watch_rooms").document(roomId)

        val safeTitle = title.trim().ifBlank { "Lift Watch Party" }.take(140)
        val safeHost = hostName.trim().ifBlank { "Host" }.take(50)
        val safeSourcePage = sourcePageUrl.trim().take(600)
        val safeStreamUrl = streamUrl.trim().take(1000)
        val safeDomain = sourceDomain.trim().ifBlank { "lift" }.take(120)
        val safeThumb = thumbnailUrl.trim().take(600)

        require(safeSourcePage.isNotEmpty()) { "Source page URL is required." }
        require(safeStreamUrl.isNotEmpty()) { "Playable stream URL is required." }

        val payload = buildMap<String, Any> {
            put("hostId", uid)
            put("hostName", safeHost)
            put("title", safeTitle)
            put("sourcePageUrl", safeSourcePage)
            put("streamUrl", safeStreamUrl)
            if (safeThumb.isNotEmpty()) put("thumbnailUrl", safeThumb)
            put("sourceDomain", safeDomain)
            put("roomCode", roomCode)
            put("isPublic", isPublic)
            put("status", WatchRoom.STATUS_ACTIVE)
            put("isPlaying", true)
            put("playbackPositionMs", 0L)
            put("playbackRate", 1.0)
            put("syncVersion", 1L)
            put("memberUids", listOf(uid))
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }

        try {
            docRef.set(payload).await()
            WatchRoom(
                id = roomId,
                hostId = uid,
                hostName = safeHost,
                title = safeTitle,
                sourcePageUrl = safeSourcePage,
                streamUrl = safeStreamUrl,
                thumbnailUrl = safeThumb,
                sourceDomain = safeDomain,
                roomCode = roomCode,
                isPublic = isPublic,
                status = WatchRoom.STATUS_ACTIVE,
                isPlaying = true,
                playbackPositionMs = 0L,
                playbackRate = 1.0,
                syncVersion = 1L,
                memberUids = listOf(uid),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun joinRoom(roomId: String): Result<WatchRoom> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("watch_rooms").document(roomId)
        try {
            val snapshot = docRef.get().await()
            val room = snapshot.toObject(
                WatchRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: throw IllegalStateException("Watch party room not found.")

            if (room.status == WatchRoom.STATUS_ENDED) {
                throw IllegalStateException("This watch party has already ended.")
            }

            if (uid !in room.memberUids) {
                val updatedMembers = (room.memberUids + uid).distinct()
                docRef.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                room.copy(memberUids = updatedMembers)
            } else {
                room
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun joinRoomByCode(roomCode: String): Result<WatchRoom> = runCatching {
        val uid = requireUserId()
        val cleanCode = roomCode.trim().uppercase()
        require(cleanCode.isNotEmpty()) { "Enter a room code." }
        try {
            val snapshot = db.collection("watch_rooms")
                .whereEqualTo("isPublic", true)
                .whereEqualTo("status", WatchRoom.STATUS_ACTIVE)
                .get()
                .await()
            val rooms = snapshot.toObjects(
                WatchRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val target = rooms.firstOrNull { it.roomCode.equals(cleanCode, ignoreCase = true) }
                ?: throw IllegalStateException("No active public room found with code $cleanCode.")

            if (uid !in target.memberUids) {
                joinRoom(target.id).getOrThrow()
            } else {
                target
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "watch_rooms")
            throw e
        }
    }

    suspend fun leaveRoom(roomId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("watch_rooms").document(roomId)
        try {
            val snapshot = docRef.get().await()
            val room = snapshot.toObject(
                WatchRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@runCatching

            if (room.hostId == uid || room.status == WatchRoom.STATUS_ENDED) return@runCatching
            if (uid in room.memberUids) {
                val updatedMembers = room.memberUids.filterNot { it == uid }
                docRef.update(
                    mapOf(
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun syncPlayback(
        roomId: String,
        isPlaying: Boolean,
        playbackPositionMs: Long,
        playbackRate: Double,
        nextSyncVersion: Long,
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("watch_rooms").document(roomId)
        try {
            docRef.update(
                mapOf(
                    "isPlaying" to isPlaying,
                    "playbackPositionMs" to playbackPositionMs.coerceAtLeast(0L),
                    "playbackRate" to playbackRate.coerceIn(0.25, 4.0),
                    "syncVersion" to nextSyncVersion.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateRoomMedia(
        roomId: String,
        title: String,
        sourcePageUrl: String,
        streamUrl: String,
        thumbnailUrl: String,
        nextSyncVersion: Long,
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("watch_rooms").document(roomId)
        val updates = buildMap<String, Any> {
            put("title", title.trim().ifBlank { "Lift Media" }.take(140))
            put("sourcePageUrl", sourcePageUrl.trim().take(600))
            put("streamUrl", streamUrl.trim().take(1000))
            if (thumbnailUrl.isNotBlank()) put("thumbnailUrl", thumbnailUrl.trim().take(600))
            put("playbackPositionMs", 0L)
            put("isPlaying", true)
            put("syncVersion", nextSyncVersion.coerceAtLeast(0L))
            put("updatedAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun endRoom(roomId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("watch_rooms").document(roomId)
        try {
            docRef.update(
                mapOf(
                    "status" to WatchRoom.STATUS_ENDED,
                    "isPlaying" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Live Room Messages ---

    fun observeMessages(roomId: String, isPublic: Boolean): Flow<List<RoomMessage>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("watch_rooms").document(roomId).collection("messages")
        val query = if (isPublic) {
            colRef.whereEqualTo("isPublic", true)
        } else {
            colRef.whereArrayContains("memberUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RoomMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreException(error)
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendMessage(
        roomId: String,
        senderName: String,
        text: String,
        reactionEmoji: String? = null,
        messageType: String = RoomMessage.TYPE_CHAT,
        isPublic: Boolean,
        memberUids: List<String>,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("watch_rooms").document(roomId).collection("messages").document(msgId)
        val cleanText = text.trim().take(400)
        require(cleanText.isNotEmpty()) { "Message cannot be empty." }

        val safeMembers = (if (uid in memberUids) memberUids else memberUids + uid).distinct().take(25)
        val payload = buildMap<String, Any> {
            put("roomId", roomId)
            put("senderId", uid)
            put("senderName", senderName.trim().ifBlank { "Viewer" }.take(50))
            put("text", cleanText)
            if (!reactionEmoji.isNullOrBlank()) put("reactionEmoji", reactionEmoji.take(16))
            put("messageType", messageType)
            put("isPublic", isPublic)
            put("memberUids", safeMembers)
            put("createdAt", FieldValue.serverTimestamp())
        }
        try {
            docRef.set(payload).await()
            msgId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) { append(chars.random()) }
        }
    }
}
