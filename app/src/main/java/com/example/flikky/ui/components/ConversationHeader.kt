package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.connected

/**
 * 连接后纤细头部：对端头像 + 对端名 + 已连接状态 + 运行统计。
 * 替代连接前的 ConnectionInfoCard，并吸收原底部 StatusBar 的统计信息。
 * trailing 槽留给停止服务按钮（Task 7 注入）。
 */
@Composable
fun ConversationHeader(
    peerAvatarId: Int,
    peerName: String,
    uptimeSeconds: Long,
    fileCount: Int,
    bytesPerSecond: Long,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(avatarId = peerAvatarId, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peerName.ifBlank { "对端设备" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "已连接 · 运行 ${formatUptime(uptimeSeconds)} · $fileCount 文件 · ${formatRate(bytesPerSecond)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.connected,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
