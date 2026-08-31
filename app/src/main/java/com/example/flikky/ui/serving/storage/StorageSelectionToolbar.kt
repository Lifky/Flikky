package com.example.flikky.ui.serving.storage

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.ui.components.formatSize
import com.example.flikky.ui.theme.Spacing

/**
 * 文件 tab 的选择操作条内容：「已选 N 项 · 合计大小」+ 清除 + 发送。
 *
 * 外壳由调用方套 `FlikkySelectingToolbarOverlay` + `FlikkyFloatingToolbar`
 * （收藏页与文件总览页同一套），这里只提供三件内容。
 *
 * [summary] 来自 `LocalStorageBrowser.selectionSummary`，与真正发出的量同源——
 * 在这里自己数一遍 `selected.size` 就会出现「已选 2 项」却只发 1 个的错位。
 */
@Composable
fun RowScope.StorageSelectionToolbar(
    summary: StorageSelectionSummary,
    onClear: () -> Unit,
    onSend: () -> Unit,
) {
    Text(
        text = stringResource(
            R.string.serving_storage_selected,
            summary.count,
            formatSize(summary.totalBytes),
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm),
    )
    IconButton(onClick = onClear) {
        Icon(
            painter = painterResource(R.drawable.ic_deselect),
            contentDescription = stringResource(R.string.serving_storage_clear),
        )
    }
    // 没有可发的文件时置灰而不是隐藏：位置稳定，用户不会以为按钮消失了。
    IconButton(onClick = onSend, enabled = summary.count > 0) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_upward),
            contentDescription = stringResource(R.string.serving_storage_send),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
