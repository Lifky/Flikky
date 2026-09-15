package com.example.flikky.ui.serving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.session.PeerChannelState
import com.example.flikky.session.peerChannelState
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.AlbumAccess

/** Controls what the authenticated browser peer can see and do. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerPermissionsSheet(
    settings: FlikkySettings,
    hasStoragePermission: Boolean,
    albumAccess: AlbumAccess,
    onSetStorageBrowsing: (Boolean) -> Unit,
    onSetAlbumBrowsing: (Boolean) -> Unit,
    onSetFavoriteBrowsing: (Boolean) -> Unit,
    onSetAllowPeerRecall: (Boolean) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onRequestAlbumPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.peer_permissions_title),
                style = MaterialTheme.typography.titleMedium,
            )

            SettingSection(title = stringResource(R.string.peer_permissions_section_see)) {
                PeerChannelRow(
                    state = peerChannelState(
                        available = hasStoragePermission,
                        peerEnabled = settings.storageBrowsingEnabled,
                    ),
                    title = stringResource(R.string.peer_permissions_files),
                    summary = stringResource(R.string.peer_permissions_files_summary),
                    unavailableHint = stringResource(
                        R.string.peer_permissions_files_need_permission,
                    ),
                    onResolve = onRequestStoragePermission,
                    onToggle = onSetStorageBrowsing,
                    index = 0,
                    total = 3,
                )
                PeerChannelRow(
                    state = peerChannelState(
                        available = albumAccess != AlbumAccess.None,
                        peerEnabled = settings.albumBrowsingEnabled,
                    ),
                    title = stringResource(R.string.peer_permissions_album),
                    summary = stringResource(R.string.peer_permissions_album_summary),
                    unavailableHint = stringResource(
                        R.string.peer_permissions_album_need_permission,
                    ),
                    onResolve = onRequestAlbumPermission,
                    onToggle = onSetAlbumBrowsing,
                    index = 1,
                    total = 3,
                )
                PeerChannelRow(
                    state = peerChannelState(
                        available = settings.favoriteBetaEnabled,
                        peerEnabled = settings.favoriteBrowsingEnabled,
                    ),
                    title = stringResource(R.string.peer_permissions_favorites),
                    summary = stringResource(R.string.peer_permissions_favorites_summary),
                    unavailableHint = stringResource(
                        R.string.peer_permissions_favorites_need_feature,
                    ),
                    onResolve = onOpenSettings,
                    onToggle = onSetFavoriteBrowsing,
                    index = 2,
                    total = 3,
                )
            }

            SettingSection(title = stringResource(R.string.peer_permissions_section_do)) {
                PeerChannelRow(
                    state = peerChannelState(
                        available = settings.recallBetaEnabled,
                        peerEnabled = settings.allowPeerRecall,
                    ),
                    title = stringResource(R.string.peer_permissions_recall),
                    summary = stringResource(R.string.peer_permissions_recall_summary),
                    unavailableHint = stringResource(
                        R.string.peer_permissions_recall_need_feature,
                    ),
                    onResolve = onOpenSettings,
                    onToggle = onSetAllowPeerRecall,
                    index = 0,
                    total = 1,
                )
            }
        }
    }
}

@Composable
private fun PeerChannelRow(
    state: PeerChannelState,
    title: String,
    summary: String,
    unavailableHint: String,
    onResolve: () -> Unit,
    onToggle: (Boolean) -> Unit,
    index: Int,
    total: Int,
) {
    when (state) {
        PeerChannelState.Unavailable -> SettingItem(
            title = title,
            subtitle = unavailableHint,
            onClick = onResolve,
            index = index,
            total = total,
        )

        PeerChannelState.Off,
        PeerChannelState.On,
        -> SettingItem(
            title = title,
            subtitle = summary,
            trailing = {
                Switch(
                    checked = state == PeerChannelState.On,
                    onCheckedChange = onToggle,
                )
            },
            index = index,
            total = total,
        )
    }
}
