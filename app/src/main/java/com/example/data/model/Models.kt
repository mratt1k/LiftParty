package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val preferredLiftUrl: String = DEFAULT_LIFT_URL,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val DEFAULT_LIFT_URL = "https://lift.ws"
    }
}

@IgnoreExtraProperties
data class SavedLiftMedia(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val sourcePageUrl: String = "",
    val streamUrl: String = "",
    val thumbnailUrl: String = "",
    val sourceDomain: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class WatchRoom(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "",
    val sourcePageUrl: String = "",
    val streamUrl: String = "",
    val thumbnailUrl: String = "",
    val sourceDomain: String = "",
    val roomCode: String = "",
    val isPublic: Boolean = true,
    val status: String = STATUS_ACTIVE,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val playbackRate: Double = 1.0,
    val syncVersion: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_ENDED = "ended"
    }
}

@IgnoreExtraProperties
data class RoomMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val reactionEmoji: String? = null,
    val messageType: String = TYPE_CHAT,
    val isPublic: Boolean = true,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    companion object {
        const val TYPE_CHAT = "chat"
        const val TYPE_REACTION = "reaction"
        const val TYPE_SYSTEM = "system"
    }
}

data class LiftMediaItem(
    val id: String,
    val title: String,
    val description: String,
    val sourcePageUrl: String,
    val streamUrl: String,
    val thumbnailUrl: String,
    val sourceDomain: String,
    val mediaType: String = "video",
)

data class LiftFetchResult(
    val requestedUrl: String,
    val finalUrl: String,
    val pageTitle: String,
    val siteDescription: String,
    val primaryStreamUrl: String,
    val items: List<LiftMediaItem>,
    val detectedStreamUrls: List<String>,
)
