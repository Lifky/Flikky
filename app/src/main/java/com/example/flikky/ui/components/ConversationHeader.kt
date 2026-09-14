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
     * 副标题右侧的状态 chip。null 时不渲染，头部形态与之前完全一致。
     *
     * 它既显示「对端现在看得到什么」，又是进那个面板的入口 —— 状态与入口合成一个
     * 控件，所以顶栏能少一个按钮（用户裁决 2026-09-14）。
     *
     * **放在副标题这一行，不另起一行。** 上一版我给面板动作单独开了一行，
     * 头部因此变成三层 ≈168dp，装机反馈「太厚」（Screenshot_4）。这里只借用
     * 副标题旁边本来就空着的位置，一个像素的高度都不增加。
     *
     * 副标题那个 Text 带 `weight(1f)`、chip 不带：宽度不够时**压文字、留 chip**。
     * 这是刻意的 —— chip 上的通道名是这一版的核心信息（「对端现在能看到什么」），
     * 而它左边那句「已连接」在这个 header 只于已连接时渲染，本来就是废话。
     */
    statusChip: (@Composable () -> Unit)? = null,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = subtitle ?: stringResource(R.string.conversation_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.connected,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                statusChip?.invoke()
            }
        }
        trailing()
    }
}
