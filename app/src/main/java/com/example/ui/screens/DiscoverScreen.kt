package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.LiftMediaItem
import com.example.data.model.SavedLiftMedia
import com.example.ui.components.CreatePartyUrlDialog
import com.example.ui.components.LiftMediaCard
import com.example.ui.viewmodel.LiftFetchUiState

@Composable
fun DiscoverScreen(
    liftSourceUrl: String,
    liftFetchState: LiftFetchUiState,
    savedMedia: List<SavedLiftMedia>,
    isBusy: Boolean,
    onUpdateLiftSourceAndFetch: (String) -> Unit,
    onOpenLiftBrowser: (String) -> Unit,
    onWatchTogetherItem: (LiftMediaItem) -> Unit,
    onToggleSaveItem: (LiftMediaItem) -> Unit,
    onCreatePartyFromUrls: (title: String, pageUrl: String, streamUrl: String, isPublic: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var urlInput by remember(liftSourceUrl) { mutableStateOf(liftSourceUrl) }
    var showPasteUrlDialog by remember { mutableStateOf(false) }

    val savedUrls = remember(savedMedia) {
        savedMedia.map { it.sourcePageUrl }.toSet()
    }

    if (showPasteUrlDialog) {
        CreatePartyUrlDialog(
            initialPageUrl = liftSourceUrl,
            initialStreamUrl = "",
            initialTitle = "Lift Watch Party",
            onDismiss = { showPasteUrlDialog = false },
            onConfirm = { title, pageUrl, streamUrl, isPublic ->
                showPasteUrlDialog = false
                onCreatePartyFromUrls(title, pageUrl, streamUrl, isPublic)
            },
        )
    }

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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DiscoverHeroCard(
                    onOpenLiftBrowser = { onOpenLiftBrowser(liftSourceUrl) },
                    onOpenPasteUrl = { showPasteUrlDialog = true },
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Lift Website Source",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            singleLine = true,
                            placeholder = { Text("https://www.viewlift.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { onUpdateLiftSourceAndFetch(urlInput) }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lift_url_input"),
                        )

                        Button(
                            onClick = { onUpdateLiftSourceAndFetch(urlInput) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("fetch_lift_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.action_fetch),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_fetch))
                        }
                    }
                }
            }

            when (val state = liftFetchState) {
                is LiftFetchUiState.Idle,
                is LiftFetchUiState.Loading -> {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Fetching media from Lift…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is LiftFetchUiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "Couldn't parse direct HTML media",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { onOpenLiftBrowser(liftSourceUrl) },
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Icon(Icons.Filled.Language, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Browse in Lift Explorer")
                                    }
                                    OutlinedButton(
                                        onClick = { onUpdateLiftSourceAndFetch(urlInput) },
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }

                is LiftFetchUiState.Success -> {
                    val result = state.result
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.pageTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (result.siteDescription.isNotBlank()) {
                                    Text(
                                        text = result.siteDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onUpdateLiftSourceAndFetch(liftSourceUrl) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Lift content",
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }

                    if (result.detectedStreamUrls.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Detected Playable Streams (${result.detectedStreamUrls.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(result.detectedStreamUrls) { stream ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                                                    RoundedCornerShape(14.dp),
                                                )
                                                .clickable {
                                                    onCreatePartyFromUrls(
                                                        result.pageTitle,
                                                        result.finalUrl,
                                                        stream,
                                                        true,
                                                    )
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stream.substringAfterLast('/').take(28).ifBlank { "Stream" },
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (result.items.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        text = "Interactive Lift Site Detected",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "This Lift page loads video players dynamically with JavaScript. Open it in Lift Explorer to browse and automatically capture playable streams as they load.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Button(
                                        onClick = { onOpenLiftBrowser(result.finalUrl) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                    ) {
                                        Icon(Icons.Filled.Language, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Open in Lift Explorer")
                                    }
                                }
                            }
                        }
                    } else {
                        items(result.items, key = { it.id }) { item ->
                            LiftMediaCard(
                                item = item,
                                isSaved = item.sourcePageUrl in savedUrls,
                                onWatchTogether = { onWatchTogetherItem(item) },
                                onToggleSave = { onToggleSaveItem(item) },
                                onOpenInExplorer = { onOpenLiftBrowser(item.sourcePageUrl) },
                            )
                        }
                    }
                }
            }

            if (isBusy) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DiscoverHeroCard(
    onOpenLiftBrowser: () -> Unit,
    onOpenPasteUrl: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraLarge),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_watch_party_hero),
            contentDescription = stringResource(R.string.discover_hero_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.85f),
                        )
                    )
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.discover_hero_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.discover_hero_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenLiftBrowser,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_lift_explorer_button"),
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_open_browser))
                }
                FilledTonalButton(
                    onClick = onOpenPasteUrl,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("paste_url_button"),
                ) {
                    Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_paste_url))
                }
            }
        }
    }
}
