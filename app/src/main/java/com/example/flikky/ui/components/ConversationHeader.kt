package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    /** Optional live status; null preserves the default connected subtitle. */
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onAvatarClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    /**
     * 第二行的动作区，靠右排列。null 时不占任何高度。
     *
     * 为什么另起一行而不是继续塞进 [trailing]：同一个 Row 里 [trailing] 不可压缩，
     * 而文字那一列是 `weight(1f)` —— Row 永远**先压文字**。于是按钮每多一个，
     * 副标题就少一截，`已连接 · 可见：文件、收藏` 被压成 `已连接 · ...`
     *（装机反馈 2026-09-14 Screenshot_2）。那句话是这一版新加的核心信息，
     * 压掉它等于功能白做。
     *
     * 同理也不能靠 ButtonGroup 的官方 overflow 兜：它在这个 Row 里拿得到
     * 全部想要的宽度，overflow 永远不触发。**只有把两者分行，宽度竞争才真正消失。**
     */
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    text = subtitle ?: stringResource(R.string.conversation_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.connected,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
        // 第二行：面板动作靠右。null 时连 Spacer 都不加 —— 没有动作的调用点
        // 不该为此多出一段空白。
        actions?.let { row ->
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) { row() }
        }
    }
}
