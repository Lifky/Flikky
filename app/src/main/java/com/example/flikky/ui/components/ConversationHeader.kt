package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.flikky.R
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.connected

/**
 * 连接后头部：对端头像、名称、可见范围，以及紧凑图标动作。
 * 数值统计（运行时长 / 文件数 / 速率）已移至底部 ConversationStatusRow。
 * 右侧集中放置盾牌、设置、断开连接。
 */
@Composable
fun ConversationHeader(
    peerAvatarId: Int,
    peerAvatarKey: String? = null,
    peerName: String,
    /** Optional live visibility summary. */
    subtitle: String? = null,
    onPermissionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAvatarClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        val avatar: @Composable () -> Unit = {
            if (peerAvatarKey != null) {
                Avatar(avatarKey = peerAvatarKey, size = Sizes.avatar)
            } else {
                Avatar(avatarId = peerAvatarId, size = Sizes.avatar)
            }
        }
        if (onAvatarClick != null) {
            IconButton(onClick = onAvatarClick) {
                avatar()
            }
        } else {
            avatar()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peerName.ifBlank { stringResource(R.string.conversation_peer_device) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.connected,
                )
            }
        }
        Row {
            FilledTonalIconButton(onClick = onPermissionsClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_toggle),
                    contentDescription = stringResource(R.string.peer_permissions_entry),
                )
            }
            FilledTonalIconButton(onClick = onSettingsClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.serving_quick_settings),
                )
            }
            FilledTonalIconButton(
                onClick = onStopClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = stringResource(R.string.serving_stop_service),
                )
            }
        }
    }
}
