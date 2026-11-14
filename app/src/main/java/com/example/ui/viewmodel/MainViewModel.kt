package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LiftFetchResult
import com.example.data.model.LiftMediaItem
import com.example.data.model.SavedLiftMedia
import com.example.data.model.UserProfile
import com.example.data.model.WatchRoom
import com.example.data.repository.LiftContentRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.repository.WatchPartyRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface LiftFetchUiState {
    data object Idle : LiftFetchUiState
    data object Loading : LiftFetchUiState
    data class Success(val result: LiftFetchResult) : LiftFetchUiState
    data class Error(val message: String) : LiftFetchUiState
}

class MainViewModel(
    private val watchPartyRepository: WatchPartyRepository,
    private val liftContentRepository: LiftContentRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val currentUserId: String,
    private val currentUserDisplayName: String,
    private val currentUserPhotoUrl: String?,
) : ViewModel() {

    val userProfileState: StateFlow<UiState<UserProfile?>> = watchPartyRepository.observeUserProfile()
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val savedMediaState: StateFlow<UiState<List<SavedLiftMedia>>> = watchPartyRepository.observeSavedMedia()
        .map<List<SavedLiftMedia>, UiState<List<SavedLiftMedia>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing saved media", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load saved media"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val publicRoomsState: StateFlow<UiState<List<WatchRoom>>> = watchPartyRepository.observePublicActiveRooms()
        .map<List<WatchRoom>, UiState<List<WatchRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing public rooms", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load active rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val myRoomsState: StateFlow<UiState<List<WatchRoom>>> = watchPartyRepository.observeMyRooms()
        .map<List<WatchRoom>, UiState<List<WatchRoom>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = unwrapFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing my rooms", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load your rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _liftSourceUrl = MutableStateFlow(UserProfile.DEFAULT_LIFT_URL)
    val liftSourceUrl: StateFlow<String> = _liftSourceUrl.asStateFlow()

    private val _liftFetchState = MutableStateFlow<LiftFetchUiState>(LiftFetchUiState.Idle)
    val liftFetchState: StateFlow<LiftFetchUiState> = _liftFetchState.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl = preferencesRepository.preferredLiftUrl.first()
            _liftSourceUrl.value = savedUrl
            preferencesRepository.setPreferredLiftUrl(savedUrl)
            watchPartyRepository.ensureUserProfile(
                displayName = currentUserDisplayName,
                photoUrl = currentUserPhotoUrl,
                preferredLiftUrl = savedUrl,
            )
            watchPartyRepository.updatePreferredLiftUrl(savedUrl)
            fetchLiftSite(savedUrl)
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun updateLiftSourceUrl(newUrl: String, fetchImmediately: Boolean = true) {
        val clean = runCatching { liftContentRepository.normalizeUrl(newUrl) }.getOrElse {
            _statusBanner.value = it.message ?: "Invalid Lift website URL."
            return
        }
        _liftSourceUrl.value = clean
        viewModelScope.launch {
            preferencesRepository.setPreferredLiftUrl(clean)
            watchPartyRepository.updatePreferredLiftUrl(clean)
        }
        if (fetchImmediately) {
            fetchLiftSite(clean)
        }
    }

    fun fetchLiftSite(url: String = _liftSourceUrl.value) {
        viewModelScope.launch {
            _liftFetchState.value = LiftFetchUiState.Loading
            val normalized = runCatching { liftContentRepository.normalizeUrl(url) }.getOrElse { err ->
                _liftFetchState.value = LiftFetchUiState.Error(err.message ?: "Invalid URL")
                return@launch
            }
            _liftSourceUrl.value = normalized
            val result = liftContentRepository.fetchFromLift(normalized)
            result.onSuccess { fetched ->
                _liftFetchState.value = LiftFetchUiState.Success(fetched)
            }.onFailure { err ->
                _liftFetchState.value = LiftFetchUiState.Error(
                    err.message ?: "Unable to fetch content from Lift."
                )
            }
        }
    }

    fun resolveItemAndCreateParty(
        item: LiftMediaItem,
        isPublic: Boolean,
        onRoomCreated: (String) -> Unit,
        onNeedsBrowserExtraction: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                var streamUrl = item.streamUrl
                var title = item.title
                var thumb = item.thumbnailUrl

                if (streamUrl.isBlank()) {
                    val subFetch = liftContentRepository.fetchFromLift(item.sourcePageUrl).getOrNull()
                    if (subFetch != null && subFetch.primaryStreamUrl.isNotBlank()) {
                        streamUrl = subFetch.primaryStreamUrl
                        if (title.isBlank()) title = subFetch.pageTitle
                    }
                }

                if (streamUrl.isBlank()) {
                    _statusBanner.value = "Opening in Lift Explorer to detect video stream…"
                    onNeedsBrowserExtraction(item.sourcePageUrl)
                    return@launch
                }

                val room = watchPartyRepository.createWatchRoom(
                    title = title,
                    sourcePageUrl = item.sourcePageUrl,
                    streamUrl = streamUrl,
                    thumbnailUrl = thumb,
                    sourceDomain = item.sourceDomain,
                    hostName = currentUserDisplayName,
                    isPublic = isPublic,
                ).getOrThrow()

                onRoomCreated(room.id)
            } catch (e: Exception) {
                _statusBanner.value = e.message ?: "Could not start watch party."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createPartyFromManualOrBrowserUrl(
        title: String,
        sourcePageUrl: String,
        streamUrl: String,
        thumbnailUrl: String = "",
        isPublic: Boolean = true,
        onRoomCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val safePage = liftContentRepository.normalizeUrl(sourcePageUrl.ifBlank { streamUrl })
                val safeStream = if (streamUrl.isNotBlank()) {
                    liftContentRepository.normalizeUrl(streamUrl)
                } else {
                    val fetched = liftContentRepository.fetchFromLift(safePage).getOrThrow()
                    fetched.primaryStreamUrl.ifBlank {
                        throw IllegalStateException("No direct video stream found on that page. Try opening it in Lift Explorer.")
                    }
                }
                val domain = runCatching {
                    java.net.URI(safePage).host?.removePrefix("www.") ?: "lift"
                }.getOrDefault("lift")

                val room = watchPartyRepository.createWatchRoom(
                    title = title.ifBlank { "Lift Watch Party" },
                    sourcePageUrl = safePage,
                    streamUrl = safeStream,
                    thumbnailUrl = thumbnailUrl,
                    sourceDomain = domain,
                    hostName = currentUserDisplayName,
                    isPublic = isPublic,
                ).getOrThrow()

                onRoomCreated(room.id)
            } catch (e: Exception) {
                _statusBanner.value = e.message ?: "Failed to create watch party."
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun joinRoom(roomId: String, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            watchPartyRepository.joinRoom(roomId)
                .onSuccess { onJoined(it.id) }
                .onFailure { _statusBanner.value = it.message ?: "Could not join room." }
            _isBusy.value = false
        }
    }

    fun joinRoomByCode(code: String, onJoined: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            watchPartyRepository.joinRoomByCode(code)
                .onSuccess { onJoined(it.id) }
                .onFailure { _statusBanner.value = it.message ?: "No active room matches that code." }
            _isBusy.value = false
        }
    }

    fun saveMedia(item: LiftMediaItem) {
        viewModelScope.launch {
            watchPartyRepository.saveLiftMedia(item)
                .onSuccess { _statusBanner.value = "Saved \"${item.title.take(32)}\" to Watchlist" }
                .onFailure { _statusBanner.value = it.message ?: "Failed to save media" }
        }
    }

    fun removeSavedMedia(mediaId: String) {
        viewModelScope.launch {
            watchPartyRepository.removeSavedMedia(mediaId)
                .onFailure { _statusBanner.value = it.message ?: "Failed to remove item" }
        }
    }

    private fun unwrapFirestoreException(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "MainVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
