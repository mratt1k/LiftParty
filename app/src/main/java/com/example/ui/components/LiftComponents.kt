package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LiftMediaItem
import com.example.data.model.WatchRoom

@Composable
fun LiftMediaCard(
    item: LiftMediaItem,
    isSaved: Boolean,
    onWatchTogether: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenInExplorer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (item.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer,
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = item.sourceDomain,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }

                if (item.streamUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = if (item.mediaType == "hls") "HLS STREAM" else "DIRECT VIDEO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onWatchTogether,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("watch_together_card_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_watch_together),
                            maxLines = 1,
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenInExplorer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = stringResource(R.string.action_open_browser),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = stringResource(
                                if (isSaved) R.string.action_saved else R.string.action_save_watchlist
                            ),
                            tint = if (isSaved) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WatchRoomCard(
    room: WatchRoom,
    isCurrentUserHost: Boolean,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = if (room.isPlaying) "LIVE • ${room.sourceDomain}" else "PAUSED • ${room.sourceDomain}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = room.roomCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = room.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${room.memberUids.size} watching • Host: ${room.hostName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Button(
                    onClick = onJoinClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentUserHost) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (isCurrentUserHost) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text(text = if (isCurrentUserHost) "Resume" else "Join")
                }
            }
        }
    }
}

@Composable
fun CreatePartyUrlDialog(
    initialPageUrl: String,
    initialStreamUrl: String,
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, pageUrl: String, streamUrl: String, isPublic: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var pageUrl by remember { mutableStateOf(initialPageUrl) }
    var streamUrl by remember { mutableStateOf(initialStreamUrl) }
    var isPublic by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Start Lift Watch Party",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Room Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pageUrl,
                    onValueChange = { pageUrl = it },
                    label = { Text("Lift Page URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Stream URL (.m3u8 / .mp4 / optional)") },
                    placeholder = { Text("Leave blank to auto-extract from Lift page") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Public Party",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Visible in the Watch Parties lobby",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, pageUrl, streamUrl, isPublic) },
                enabled = pageUrl.isNotBlank() || streamUrl.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_party_button"),
            ) {
                Text("Go Live")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
