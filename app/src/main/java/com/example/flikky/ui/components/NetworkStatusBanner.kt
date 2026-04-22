package com.example.flikky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.flikky.session.NetworkStatus

/**
 * Banner mounted under the top bar of ServingScreen / ExportingScreen to
 * surface Wi-Fi rebind state. Silent on [NetworkStatus.Ok]; otherwise paints
 * a single line plus (only in the Switched case) a "我知道了" dismiss button.
 *
 * Colors: tertiaryContainer for transient Switching/Switched; errorContainer
 * for Lost — keeps the banner theme-aware, no hard-coded hex.
 */
@Composable
fun NetworkStatusBanner(
    status: NetworkStatus,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status is NetworkStatus.Ok) return

    val (bg, fg, text, showAck) = when (status) {
        is NetworkStatus.Ok -> return // unreachable
        is NetworkStatus.Switching -> BannerStyle(
            bg = MaterialTheme.colorScheme.tertiaryContainer,
            fg = MaterialTheme.colorScheme.onTertiaryContainer,
            text = "正在切换网络…",
            showAck = false,
        )
        is NetworkStatus.Lost -> BannerStyle(
            bg = MaterialTheme.colorScheme.errorContainer,
            fg = MaterialTheme.colorScheme.onErrorContainer,
            text = "已失联，等待网络恢复",
            showAck = false,
        )
        is NetworkStatus.Switched -> BannerStyle(
            bg = MaterialTheme.colorScheme.tertiaryContainer,
            fg = MaterialTheme.colorScheme.onTertiaryContainer,
            text = "网络已切换，URL 已更新为 ${status.newUrl}，请让浏览器重新打开",
            showAck = true,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = true),
        )
        if (showAck) {
            TextButton(onClick = onAcknowledge) {
                Text("我知道了", color = fg)
            }
        }
    }
}

private data class BannerStyle(
    val bg: Color,
    val fg: Color,
    val text: String,
    val showAck: Boolean,
)
