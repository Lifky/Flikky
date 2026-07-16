package com.example.flikky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.data.settings.BackgroundSetting

@Composable
fun ConversationBackground(
    setting: BackgroundSetting,
    connected: Boolean,
    peerName: String?,           // M7: pass null; reserved
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (setting) {
            BackgroundSetting.Default -> {
                // centered faint watermark: connection status
                val text = if (connected) {
                    stringResource(R.string.conversation_connected) +
                        (peerName?.let { " · $it" } ?: "")
                } else {
                    stringResource(R.string.conversation_disconnected)
                }
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            BackgroundSetting.Blank -> {}
            is BackgroundSetting.Solid -> Box(Modifier.fillMaxSize().background(Color(setting.argb)))
            // BackgroundSetting.Gradient removed in v1.6.0
        }
        content()
    }
}
