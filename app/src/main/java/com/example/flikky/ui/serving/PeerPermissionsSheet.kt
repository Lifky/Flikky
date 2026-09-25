package com.example.flikky.ui.serving

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.example.flikky.session.visibleChannelLabels
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

/**
 * 「对端现在能看到什么」的一句话摘要。会话顶栏副标题与设置页入口行共用这一份，
 * 两处用不同判据就会一处说「可见：文件」、另一处说「未开放任何内容」。
 */
@Composable
fun peerVisibleSummary(
    settings: FlikkySettings,
    hasStoragePermission: Boolean,
    albumAccess: AlbumAccess,
): String {
    val visibleLabels = visibleChannelLabels(
        listOf(
            stringResource(R.string.peer_permissions_files) to peerChannelState(
                available = hasStoragePermission,
                peerEnabled = settings.storageBrowsingEnabled,
            ),
            stringResource(R.string.peer_permissions_album) to peerChannelState(
                available = albumAccess != AlbumAccess.None,
                peerEnabled = settings.albumBrowsingEnabled,
            ),
            stringResource(R.string.peer_permissions_favorites) to peerChannelState(
                available = settings.favoriteBetaEnabled,
                peerEnabled = settings.favoriteBrowsingEnabled,
            ),
        ),
    )
    return if (visibleLabels.isEmpty()) {
        stringResource(R.string.peer_permissions_visible_none)
    } else {
        stringResource(
            R.string.peer_permissions_visible_summary,
            visibleLabels.joinToString(stringResource(R.string.peer_permissions_visible_sep)),
        )
    }
}

/** 相册授权请求的权限组。Android 14+ 多一个「部分照片」权限，否则系统不给「选择照片」选项。 */
val albumPermissionRequest: Array<String> = buildList {
    add(android.Manifest.permission.READ_MEDIA_IMAGES)
    add(android.Manifest.permission.READ_MEDIA_VIDEO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        add(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }
}.toTypedArray()

/**
 * 跳「所有文件访问」的系统授权页。必须带 `package:` data，否则打开的是全局应用列表、
 * 用户得自己在几十个应用里翻到 Flikky。系统页没有结果回调，返回后靠 ON_RESUME 重查。
 */
fun requestAllFilesAccess(ctx: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:" + ctx.packageName),
    )
    runCatching { ctx.startActivity(intent) }.onFailure {
        // 极少数 ROM 不实现按包名的那个 action，退回全局列表总比什么都不发生好。
        runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
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
