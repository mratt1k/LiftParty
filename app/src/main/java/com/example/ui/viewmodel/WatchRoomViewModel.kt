package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RoomMessage
import com.example.data.model.WatchRoom
import com.example.data.repository.WatchPartyRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WatchRoomViewModel(
    private val repository: WatchPartyRepository,
    private val roomId: String,
    val currentUserId: String,
    private val currentUserDisplayName: String,
) : ViewModel() {

    val roomState: StateFlow<UiState<WatchRoom?>> = repository.observeRoom(roomId)
        .map<WatchRoom?, UiState<WatchRoom?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing room", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to observe watch party"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<RoomMessage>>> = roomState
        .map { state -> (state as? UiState.Success)?.data?.isPublic }
        .distinctUntilChanged()
        .flatMapLatest { isPublic ->
            if (isPublic == null) {
                flowOf(UiState.Loading)
            } else {
                repository.observeMessages(roomId, isPublic)
                    .map<List<RoomMessage>, UiState<List<RoomMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = unwrapFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing messages", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to load chat"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _roomNotice = MutableStateFlow<String?>(null)
    val roomNotice: StateFlow<String?> = _roomNotice.asStateFlow()

    fun clearNotice() {
        _roomNotice.value = null
    }

    fun syncHostPlayback(
        isPlaying: Boolean,
        positionMs: Long,
        rate: Double = 1.0,
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        if (room.hostId != currentUserId || room.status == WatchRoom.STATUS_ENDED) return
        viewModelScope.launch {
            repository.syncPlayback(
                roomId = roomId,
                isPlaying = isPlaying,
                playbackPositionMs = positionMs,
                playbackRate = rate,
                nextSyncVersion = room.syncVersion + 1,
            ).onFailure {
                _roomNotice.value = it.message ?: "Playback sync failed"
            }
        }
    }

    fun switchLiftMedia(
        title: String,
        sourcePageUrl: String,
        streamUrl: String,
        thumbnailUrl: String = "",
    ) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        if (room.hostId != currentUserId || room.status == WatchRoom.STATUS_ENDED) return
        viewModelScope.launch {
            repository.updateRoomMedia(
                roomId = roomId,
                title = title,
                sourcePageUrl = sourcePageUrl,
                streamUrl = streamUrl,
                thumbnailUrl = thumbnailUrl,
                nextSyncVersion = room.syncVersion + 1,
            ).onSuccess {
                repository.sendMessage(
                    roomId = roomId,
                    senderName = currentUserDisplayName,
                    text = "Switched media to $title",
                    messageType = RoomMessage.TYPE_SYSTEM,
                    isPublic = room.isPublic,
                    memberUids = room.memberUids,
                )
            }.onFailure {
                _roomNotice.value = it.message ?: "Could not switch media"
            }
        }
    }

    fun sendChatMessage(text: String) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        if (text.isBlank() || room.status == WatchRoom.STATUS_ENDED) return
        viewModelScope.launch {
            repository.sendMessage(
                roomId = roomId,
                senderName = currentUserDisplayName,
                text = text,
                messageType = RoomMessage.TYPE_CHAT,
                isPublic = room.isPublic,
                memberUids = room.memberUids,
            ).onFailure {
                _roomNotice.value = it.message ?: "Could not send message"
            }
        }
    }

    fun sendReaction(emoji: String) {
        val room = (roomState.value as? UiState.Success)?.data ?: return
        if (room.status == WatchRoom.STATUS_ENDED) return
        viewModelScope.launch {
            repository.sendMessage(
                roomId = roomId,
                senderName = currentUserDisplayName,
                text = "reacted $emoji",
                reactionEmoji = emoji,
                messageType = RoomMessage.TYPE_REACTION,
                isPublic = room.isPublic,
                memberUids = room.memberUids,
            )
        }
    }

    fun leaveOrEndParty(onComplete: () -> Unit) {
        val room = (roomState.value as? UiState.Success)?.data
        viewModelScope.launch {
            if (room != null && room.status != WatchRoom.STATUS_ENDED) {
                if (room.hostId == currentUserId) {
                    repository.endRoom(roomId)
                } else {
                    repository.leaveRoom(roomId)
                }
            }
            onComplete()
        }
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "WatchRoomVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
