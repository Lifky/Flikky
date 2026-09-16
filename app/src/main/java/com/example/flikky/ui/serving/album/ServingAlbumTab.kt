package com.example.flikky.ui.serving.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.ui.components.ChannelSelectionFabMenu
import com.example.flikky.ui.components.PeerChannelLockFab
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.AlbumAccess
import com.example.flikky.util.formatBytes

/** Album permission states, timeline grid, selection actions, and peer channel lock. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServingAlbumTab(
    access: AlbumAccess,
    items: List<AlbumItemDto>,
    visibleCount: Int,
    selected: Set<String>,
    todayKey: String,
    yesterdayKey: String,
    peerAlbumEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onChangeScope: () -> Unit,
    onSetPeerAlbumEnabled: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSendSelection: () -> Unit,
    onPreview: (AlbumItemDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (access) {
        AlbumAccess.None -> {
            AlbumPermissionCard(onRequestPermission = onRequestPermission, modifier = modifier)
            return
        }
        AlbumAccess.Partial, AlbumAccess.Full -> Unit
    }

    var selecting by remember { mutableStateOf(false) }
    LaunchedEffect(selected.isEmpty()) {
        if (selected.isEmpty()) selecting = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (access == AlbumAccess.Partial) {
                AlbumScopeBanner(count = visibleCount, onChangeScope = onChangeScope)
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.album_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AlbumGrid(
                    items = items,
                    selected = selected,
                    selecting = selecting,
                    todayKey = todayKey,
                    yesterdayKey = yesterdayKey,
                    onToggleSelection = onToggleSelection,
                    onPreview = onPreview,
                    onStartSelecting = { id ->
                        selecting = true
                        onToggleSelection(id)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ChannelSelectionFabMenu(
            visible = selected.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomEnd),
            channelLock = { lockModifier ->
                PeerChannelLockFab(
                    peerEnabled = peerAlbumEnabled,
                    onToggle = onSetPeerAlbumEnabled,
                    descriptionOn = stringResource(R.string.album_lock_peer_on),
                    descriptionOff = stringResource(R.string.album_lock_peer_off),
                    modifier = lockModifier,
                )
            },
            menuItems = {
                FloatingActionButtonMenuItem(
                    onClick = onSendSelection,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_upward),
                            contentDescription = null,
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                R.string.album_send_n,
                                selected.size,
                                formatBytes(items.filter { it.id in selected }.sumOf { it.size }),
                            ),
                        )
                    },
                )
                FloatingActionButtonMenuItem(
                    onClick = onClearSelection,
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_deselect),
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.album_clear)) },
                )
            },
        )
    }
}

@Composable
private fun AlbumScopeBanner(count: Int, onChangeScope: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.album_scope_partial, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChangeScope) {
            Text(stringResource(R.string.album_scope_change))
        }
    }
}

@Composable
private fun AlbumPermissionCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.album_permission_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.album_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.album_permission_action))
                }
            }
        }
    }
}
