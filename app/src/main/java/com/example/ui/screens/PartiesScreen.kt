package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.WatchRoom
import com.example.ui.components.WatchRoomCard
import com.example.ui.viewmodel.UiState

@Composable
fun PartiesScreen(
    currentUserId: String,
    publicRoomsState: UiState<List<WatchRoom>>,
    myRoomsState: UiState<List<WatchRoom>>,
    onJoinRoom: (String) -> Unit,
    onJoinByCode: (String) -> Unit,
    onNavigateToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var codeInput by remember { mutableStateOf("") }

    val activeMyRooms = remember(myRoomsState) {
        (myRoomsState as? UiState.Success)?.data
            ?.filter { it.status == WatchRoom.STATUS_ACTIVE }
            .orEmpty()
    }
    val publicRooms = remember(publicRoomsState, activeMyRooms) {
        val myIds = activeMyRooms.map { it.id }.toSet()
        (publicRoomsState as? UiState.Success)?.data
            ?.filter { it.id !in myIds }
            .orEmpty()
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
                        text = "Join with Room Code",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = { codeInput = it.uppercase().take(8) },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.parties_join_code_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { if (codeInput.isNotBlank()) onJoinByCode(codeInput) }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("room_code_input"),
                        )

                        Button(
                            onClick = { onJoinByCode(codeInput) },
                            enabled = codeInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("join_code_button"),
                        ) {
                            Text(stringResource(R.string.parties_join_button))
                        }
                    }
                }
            }

            if (activeMyRooms.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Active Parties",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                items(activeMyRooms, key = { "mine_${it.id}" }) { room ->
                    WatchRoomCard(
                        room = room,
                        isCurrentUserHost = room.hostId == currentUserId,
                        onJoinClick = { onJoinRoom(room.id) },
                    )
                }
            }

            item {
                Text(
                    text = "Live Public Rooms",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            when (publicRoomsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = publicRoomsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    if (publicRooms.isEmpty() && activeMyRooms.isEmpty()) {
                        item {
                            EmptyPartiesCard(onNavigateToDiscover = onNavigateToDiscover)
                        }
                    } else if (publicRooms.isEmpty()) {
                        item {
                            Text(
                                text = "No other public parties right now — share your room code to invite friends!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(publicRooms, key = { it.id }) { room ->
                            WatchRoomCard(
                                room = room,
                                isCurrentUserHost = room.hostId == currentUserId,
                                onJoinClick = { onJoinRoom(room.id) },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun EmptyPartiesCard(onNavigateToDiscover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraLarge)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_room),
            contentDescription = stringResource(R.string.parties_empty_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(MaterialTheme.shapes.large),
        )
        Text(
            text = stringResource(R.string.parties_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.parties_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onNavigateToDiscover,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.height(48.dp),
        ) {
            Text("Browse Lift Content")
        }
    }
}
