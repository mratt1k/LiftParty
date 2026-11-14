package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.LiftMediaItem
import com.example.data.model.SavedLiftMedia
import com.example.ui.viewmodel.UiState

@Composable
fun WatchlistScreen(
    displayName: String,
    email: String?,
    photoUrl: String?,
    liftSourceUrl: String,
    savedMediaState: UiState<List<SavedLiftMedia>>,
    onWatchSavedItem: (LiftMediaItem) -> Unit,
    onRemoveSavedItem: (String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (!photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = displayName,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (!email.isNullOrBlank()) {
                                    Text(
                                        text = email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "Lift Source: $liftSourceUrl",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.sign_out))
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Bookmarked Lift Media",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            when (savedMediaState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(
                            text = savedMediaState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is UiState.Success -> {
                    val items = savedMediaState.data
                    if (items.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = stringResource(R.string.watchlist_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.watchlist_empty_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(items, key = { it.id }) { saved ->
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = saved.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = saved.sourceDomain,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onWatchSavedItem(
                                                LiftMediaItem(
                                                    id = saved.id,
                                                    title = saved.title,
                                                    description = saved.description,
                                                    sourcePageUrl = saved.sourcePageUrl,
                                                    streamUrl = saved.streamUrl,
                                                    thumbnailUrl = saved.thumbnailUrl,
                                                    sourceDomain = saved.sourceDomain,
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Watch")
                                    }

                                    IconButton(
                                        onClick = { onRemoveSavedItem(saved.id) },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Remove from watchlist",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
