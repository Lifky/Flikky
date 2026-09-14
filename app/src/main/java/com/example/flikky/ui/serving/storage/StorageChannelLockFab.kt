package com.example.flikky.ui.serving.storage

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageChannelLockFab(
    peerEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (peerEnabled) R.string.storage_lock_peer_on else R.string.storage_lock_peer_off,
    )
    SmallFloatingActionButton(
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
