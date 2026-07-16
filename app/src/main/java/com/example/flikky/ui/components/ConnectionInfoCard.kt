package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * 连接信息卡片：在电脑浏览器打开 URL + 输入 PIN。
 * ServingScreen（连接前）与 ExportingScreen（ArmedContent）共用，确保两屏一致。
 */
@Composable
fun ConnectionInfoCard(
    url: String,
    pin: String,
    modifier: Modifier = Modifier,
    requirePin: Boolean = true,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.connection_open_in_browser),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // URL 单独整行居中（长地址自动换行不再与按钮挤在一行），
            // 复制动作下移为独立按钮，避免长 URL 时图标垂直错位。
            Text(
                text = url,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            FilledTonalButton(onClick = { scope.launch { clipboard.setPlainText(url) } }) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_copy),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.connection_copy_address))
            }
            Spacer(Modifier.height(Spacing.xs))
            if (requirePin) {
                Text(
                    text = "PIN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pin,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        // PIN is intentionally bold so users can read and transcribe it quickly.
                        fontWeight = FontWeight.Bold,
                    ),
                )
            } else {
                Text(
                    text = stringResource(R.string.connection_method),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.connection_without_pin),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
