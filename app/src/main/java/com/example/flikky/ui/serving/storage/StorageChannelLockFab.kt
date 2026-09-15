package com.example.flikky.ui.serving.storage

import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R

/**
 * 文件 tab 的通道锁：一键决定对端能不能跟着翻这些文件。
 *
 * 它不是新状态，而是与对端权限面板读写同一个 `storageBrowsingEnabled` 值。锁的是
 * 对端通道，不是 App 的系统存储权限；后者不能由 App 在不杀进程的情况下主动撤销。
 *
 * 与右下的 [StorageSelectionFab] 各自独立：那个管选中项操作，这个管对端通道。
 * 两者共享 StorageSelectionFab 的固定按钮槽中心，大小刻意不同 ——
 * 发送选中项是主操作，所以它更大。
 *
 * **用 56dp 标准 FAB，不用 SmallFloatingActionButton。** M3 Expressive 已经把
 * small（40dp）标记为 deprecated（本地文档 components/FloatingActionButton.md：
 * 「Deprecated **small** FAB size」），Expressive 里没有比 56dp 更小的档了。
 * 而且 40dp 配本项目 Shapes.medium 的 16dp 圆角 = 圆角占边长 40%，读起来是「圆」
 * 而不是「圆角方」（装机反馈 2026-09-14 Screenshot_1）；56dp 下同一个圆角只占 29%。
 */
@Composable
fun StorageChannelLockFab(
    peerEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (peerEnabled) R.string.storage_lock_peer_on else R.string.storage_lock_peer_off,
    )
    FloatingActionButton(
        onClick = { onToggle(!peerEnabled) },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(
                if (peerEnabled) R.drawable.ic_lock_open_right else R.drawable.ic_lock,
            ),
            contentDescription = description,
        )
    }
}
