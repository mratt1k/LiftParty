package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.RoomMessage
import com.example.data.model.WatchRoom
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.WatchRoomViewModel
import kotlinx.coroutines.delay
import java.net.URI
import kotlin.math.abs

private val REACTION_EMOJIS = listOf("🔥", "😂", "😮", "❤️", "👏", "🍿")

@OptIn(UnstableApi::class)
@Composable
fun WatchRoomScreen(
    viewModel: WatchRoomViewModel,
    onBack: () -> Unit,
    onOpenSourceInBrowser: (String) -> Unit,
) {
    val roomUiState by viewModel.roomState.collectAsStateWithLifecycle()
    val messagesUiState by viewModel.messagesState.collectAsStateWithLifecycle()
    val notice by viewModel.roomNotice.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
    }

    val exoPlayer = remember(context, httpDataSourceFactory) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build()
    }

    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var localPositionMs by remember { mutableLongStateOf(0L) }
    var localDurationMs by remember { mutableLongStateOf(0L) }
    var loadedStreamUrl by remember { mutableStateOf("") }
    var lastAppliedSyncVersion by remember { mutableLongStateOf(-1L) }

    BackHandler {
        viewModel.leaveOrEndParty(onBack)
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                    localDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.localizedMessage ?: "Unable to play this stream."
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            localPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) localDurationMs = dur
            delay(500L)
        }
    }

    val room = (roomUiState as? UiState.Success)?.data
    val isHost = room?.hostId == viewModel.currentUserId

    // Sync ExoPlayer with Firestore WatchRoom updates
    LaunchedEffect(room?.streamUrl, room?.syncVersion, room?.isPlaying, room?.status) {
        val activeRoom = room ?: return@LaunchedEffect
        if (activeRoom.status == WatchRoom.STATUS_ENDED) {
            exoPlayer.pause()
            return@LaunchedEffect
        }
        if (activeRoom.streamUrl.isNotBlank() && activeRoom.streamUrl != loadedStreamUrl) {
            loadedStreamUrl = activeRoom.streamUrl
            playerError = null
            isBuffering = true
            val headers = buildMap {
                val pageUrl = activeRoom.sourcePageUrl
                if (pageUrl.startsWith("http", ignoreCase = true)) {
                    put("Referer", pageUrl)
                    runCatching {
                        val u = URI(pageUrl)
                        if (!u.scheme.isNullOrBlank() && !u.host.isNullOrBlank()) {
                            put("Origin", "${u.scheme}://${u.host}")
                        }
                    }
                }
            }
            httpDataSourceFactory.setDefaultRequestProperties(headers)
            exoPlayer.setMediaItem(MediaItem.fromUri(activeRoom.streamUrl))
            exoPlayer.prepare()
            if (activeRoom.playbackPositionMs > 0L) {
                exoPlayer.seekTo(activeRoom.playbackPositionMs)
            }
        }

        val shouldSyncState = !isHost || lastAppliedSyncVersion == -1L
        if (shouldSyncState && activeRoom.syncVersion != lastAppliedSyncVersion) {
            lastAppliedSyncVersion = activeRoom.syncVersion
            val drift = abs(exoPlayer.currentPosition - activeRoom.playbackPositionMs)
            if (drift > 1800L) {
                exoPlayer.seekTo(activeRoom.playbackPositionMs)
            }
            exoPlayer.setPlaybackSpeed(activeRoom.playbackRate.toFloat().coerceIn(0.25f, 4.0f))
            if (activeRoom.isPlaying) {
                exoPlayer.play()
            } else {
                exoPlayer.pause()
            }
        }
    }

    // Periodic host heartbeat so late joiners land near live host timestamp
    LaunchedEffect(room?.id, isHost, room?.isPlaying, room?.status) {
        val activeRoom = room ?: return@LaunchedEffect
        if (!isHost || !activeRoom.isPlaying || activeRoom.status == WatchRoom.STATUS_ENDED) return@LaunchedEffect
        while (true) {
            delay(10_000L)
            if (exoPlayer.isPlaying) {
                viewModel.syncHostPlayback(
                    isPlaying = true,
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    rate = activeRoom.playbackRate,
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Top Room Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { viewModel.leaveOrEndParty(onBack) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("room_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Leave watch party",
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room?.title ?: "Watch Party",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (isHost) stringResource(R.string.room_host_badge) else stringResource(R.string.room_synced_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHost) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    )
                    if (room != null) {
                        Text(
                            text = "• Code: ${room.roomCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "${room.memberUids.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (room != null) {
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my LiftParty watch room \"${room.title}\" with code ${room.roomCode}! Watching from ${room.sourcePageUrl}",
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Invite Friends"))
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.room_share_invite),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }

                TextButton(
                    onClick = { viewModel.leaveOrEndParty(onBack) },
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(
                        text = stringResource(if (isHost) R.string.room_end_party else R.string.room_leave_party),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (notice != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = notice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::clearNotice) {
                        Text("OK")
                    }
                }
            }
        }

        when (val state = roomUiState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            is UiState.Success -> {
                val activeRoom = state.data
                if (activeRoom == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Watch party not found.")
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWide = maxWidth >= 720.dp
                        if (isWide) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .weight(1.35f)
                                        .fillMaxHeight()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    VideoTheaterSection(
                                        exoPlayer = exoPlayer,
                                        room = activeRoom,
                                        isHost = isHost,
                                        isBuffering = isBuffering,
                                        playerError = playerError,
                                        positionMs = localPositionMs,
                                        durationMs = localDurationMs,
                                        onHostPlayPause = { play ->
                                            if (play) exoPlayer.play() else exoPlayer.pause()
                                            viewModel.syncHostPlayback(play, exoPlayer.currentPosition, activeRoom.playbackRate)
                                        },
                                        onHostSeek = { targetMs ->
                                            exoPlayer.seekTo(targetMs)
                                            viewModel.syncHostPlayback(exoPlayer.isPlaying, targetMs, activeRoom.playbackRate)
                                        },
                                        onGuestResync = {
                                            exoPlayer.seekTo(activeRoom.playbackPositionMs)
                                            if (activeRoom.isPlaying) exoPlayer.play() else exoPlayer.pause()
                                        },
                                        onOpenInBrowser = { onOpenSourceInBrowser(activeRoom.sourcePageUrl) },
                                    )
                                }

                                LoungeChatSection(
                                    messagesState = messagesUiState,
                                    currentUserId = viewModel.currentUserId,
                                    isEnded = activeRoom.status == WatchRoom.STATUS_ENDED,
                                    onSendMessage = viewModel::sendChatMessage,
                                    onSendReaction = viewModel::sendReaction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(1.dp, MaterialTheme.colorScheme.outline),
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                VideoTheaterSection(
                                    exoPlayer = exoPlayer,
                                    room = activeRoom,
                                    isHost = isHost,
                                    isBuffering = isBuffering,
                                    playerError = playerError,
                                    positionMs = localPositionMs,
                                    durationMs = localDurationMs,
                                    onHostPlayPause = { play ->
                                        if (play) exoPlayer.play() else exoPlayer.pause()
                                        viewModel.syncHostPlayback(play, exoPlayer.currentPosition, activeRoom.playbackRate)
                                    },
                                    onHostSeek = { targetMs ->
                                        exoPlayer.seekTo(targetMs)
                                        viewModel.syncHostPlayback(exoPlayer.isPlaying, targetMs, activeRoom.playbackRate)
                                    },
                                    onGuestResync = {
                                        exoPlayer.seekTo(activeRoom.playbackPositionMs)
                                        if (activeRoom.isPlaying) exoPlayer.play() else exoPlayer.pause()
                                    },
                                    onOpenInBrowser = { onOpenSourceInBrowser(activeRoom.sourcePageUrl) },
                                )

                                LoungeChatSection(
                                    messagesState = messagesUiState,
                                    currentUserId = viewModel.currentUserId,
                                    isEnded = activeRoom.status == WatchRoom.STATUS_ENDED,
                                    onSendMessage = viewModel::sendChatMessage,
                                    onSendReaction = viewModel::sendReaction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoTheaterSection(
    exoPlayer: ExoPlayer,
    room: WatchRoom,
    isHost: Boolean,
    isBuffering: Boolean,
    playerError: String?,
    positionMs: Long,
    durationMs: Long,
    onHostPlayPause: (Boolean) -> Unit,
    onHostSeek: (Long) -> Unit,
    onGuestResync: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        keepScreenOn = true
                    }
                },
                update = { playerView ->
                    if (playerView.player !== exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
            )

            if (isBuffering && playerError == null) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (room.status == WatchRoom.STATUS_ENDED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.room_ended_banner),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            } else if (playerError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.86f))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Stream Playback Issue",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = playerError,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text("Open Lift Page in Explorer")
                    }
                }
            }
        }

        // Custom Sync Controls Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val safeDuration = durationMs.coerceAtLeast(1L)
            if (isHost) {
                Slider(
                    value = positionMs.coerceIn(0L, safeDuration).toFloat(),
                    onValueChange = { onHostSeek(it.toLong()) },
                    valueRange = 0f..safeDuration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(
                    progress = { (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isHost) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = { onHostSeek((positionMs - 10_000L).coerceAtLeast(0L)) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10 seconds")
                        }
                        Button(
                            onClick = { onHostPlayPause(!room.isPlaying) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("host_play_pause_button"),
                        ) {
                            Icon(
                                imageVector = if (room.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (room.isPlaying) "Pause for all" else "Play for all",
                            )
                        }
                        IconButton(
                            onClick = { onHostSeek(positionMs + 10_000L) },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = onGuestResync,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.room_resync))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoungeChatSection(
    messagesState: UiState<List<RoomMessage>>,
    currentUserId: String,
    isEnded: Boolean,
    onSendMessage: (String) -> Unit,
    onSendReaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var chatDraft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        // Reaction Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(REACTION_EMOJIS) { emoji ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp, 40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isEnded) { onSendReaction(emoji) },
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // Messages Feed
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is UiState.Error -> {
                    Text(
                        text = messagesState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                }

                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Text(
                            text = "No messages yet — drop a reaction or say hi!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                ChatBubble(
                                    message = msg,
                                    isMine = msg.senderId == currentUserId,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = chatDraft,
                onValueChange = { chatDraft = it.take(400) },
                enabled = !isEnded,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.room_chat_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (chatDraft.isNotBlank()) {
                            onSendMessage(chatDraft)
                            chatDraft = ""
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
            )

            Button(
                onClick = {
                    if (chatDraft.isNotBlank()) {
                        onSendMessage(chatDraft)
                        chatDraft = ""
                    }
                },
                enabled = !isEnded && chatDraft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("send_chat_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send chat message",
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: RoomMessage,
    isMine: Boolean,
) {
    if (message.messageType == RoomMessage.TYPE_REACTION) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "${message.senderName} ${message.reactionEmoji ?: "🔥"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        return
    }

    val bubbleColor = when {
        message.messageType == RoomMessage.TYPE_SYSTEM -> MaterialTheme.colorScheme.surfaceContainerHigh
        isMine -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!isMine) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hours = minutes / 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes % 60L, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
