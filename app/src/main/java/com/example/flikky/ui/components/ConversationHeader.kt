package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.connected

/**
 * 连接后纤细头部：对端头像 + 对端名 + 已连接状态。
 * 数值统计（运行时长 / 文件数 / 速率）已移至底部 ConversationStatusRow。
 * trailing 槽留给停止服务按钮。
 */
@Composable
fun ConversationHeader(
    peerAvatarId: Int,
    peerAvatarKey: String? = null,
    peerName: String,
    modifier: Modifier = Modifier,
    onAvatarClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
            Text(
                text = stringResource(R.string.conversation_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.connected,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
