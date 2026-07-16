package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing

/**
 * 会话底部统计行：运行时长 / 文件数 / 速率。
 * 最小高 56dp —— 作为底部 snackbar 的落区，使默认底部 snackbar 覆盖在这条信息行上，
 * 而不会盖住其上方的输入框。
 */
@Composable
fun ConversationStatusRow(
    uptimeSeconds: Long,
    fileCount: Int,
    bytesPerSecond: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.rowMinH).padding(horizontal = Spacing.screenEdge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.conversation_running, formatUptime(uptimeSeconds)), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(pluralStringResource(R.plurals.conversation_file_count, fileCount, fileCount), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatRate(bytesPerSecond), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

internal fun formatRate(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%.1f KB/s".format(bps / 1_000.0)
    else -> "$bps B/s"
}
