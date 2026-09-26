package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flikky.R
import com.example.flikky.session.ConnectionAddresses
import com.example.flikky.session.LocalNameStatus
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.LocalHostName
import com.example.flikky.util.fitFontSize
import kotlinx.coroutines.launch

/**
 * 连接信息卡片：在电脑浏览器打开 URL + 输入 PIN。
 * ServingScreen（连接前）与 ExportingScreen（ArmedContent）共用，确保两屏一致。
 */
@Composable
fun ConnectionInfoCard(
    address: ConnectionAddresses,
    pin: String,
    modifier: Modifier = Modifier,
    requirePin: Boolean = true,
    onAdoptNumber: ((Int) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showQr by remember { mutableStateOf(false) }
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
            // 第一行永远是 IP：最稳定，二维码编的也是它（D63、D77）。
            // 两行同一结构（地址 + 行尾复制）、**同一字号、绝不换行**（用户 2026-09-26）：
            // 按较长那行在可用宽度内能放下的最大字号一起缩，端口号不会被挤到下一行。
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val base = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                val urls = listOfNotNull(address.primaryUrl, address.localUrl)
                val availablePx = with(density) { (maxWidth - COPY_BUTTON_WIDTH).roundToPx() }
                val sizeSp = remember(urls, availablePx, base) {
                    fitFontSize(maxSp = base.fontSize.value, minSp = MIN_ADDRESS_SP, stepSp = 1f) { sp ->
                        urls.all { url ->
                            measurer.measure(url, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false)
                                .size.width <= availablePx
                        }
                    }
                }
                val addressStyle = base.copy(fontSize = sizeSp.sp)
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AddressRow(
                        url = address.primaryUrl,
                        style = addressStyle,
                        copyDescription = stringResource(R.string.connection_copy_address),
                        onCopy = { scope.launch { clipboard.setPlainText(address.primaryUrl) } },
                    )
                    address.portNotice?.let { notice ->
                        HintText(stringResource(R.string.connection_port_moved, notice.requested, notice.actual))
                    }
                    address.localUrl?.let { localUrl ->
                        AddressRow(
                            url = localUrl,
                            style = addressStyle,
                            copyDescription = stringResource(R.string.connection_copy_local_address),
                            onCopy = { scope.launch { clipboard.setPlainText(localUrl) } },
                        )
                    }
                }
            }
            LocalNameStatusLine(address.localName, onAdoptNumber)
            FilledTonalIconButton(onClick = { showQr = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_code_2),
                    contentDescription = stringResource(R.string.connection_show_qr),
                )
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
    if (showQr) QrCodeSheet(url = address.primaryUrl, onDismiss = { showQr = false })
}

/** 行尾 IconButton 的最小触摸宽度（M3：48dp）。算字号时要把它从可用宽度里扣掉。 */
private val COPY_BUTTON_WIDTH = 48.dp
private const val MIN_ADDRESS_SP = 14f

@Composable
private fun AddressRow(url: String, style: TextStyle, copyDescription: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 字号已由调用方按可用宽度算好；这里再加单行兜底，极端窄屏宁可省略也不换行。
        Text(
            text = url,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(onClick = onCopy) {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = copyDescription,
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LocalNameStatusLine(status: LocalNameStatus, onAdoptNumber: ((Int) -> Unit)?) {
    when (status) {
        is LocalNameStatus.Probing -> HintText(stringResource(R.string.connection_local_probing))
        is LocalNameStatus.Owned -> HintText(stringResource(R.string.connection_local_hint))
        is LocalNameStatus.Renamed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HintText(
                stringResource(
                    R.string.connection_local_renamed,
                    LocalHostName.label(status.wanted),
                    LocalHostName.label(status.actual),
                ),
            )
            if (onAdoptNumber != null) {
                TextButton(onClick = { onAdoptNumber(status.actual) }) {
                    Text(stringResource(R.string.connection_local_adopt, status.actual))
                }
            }
        }
        LocalNameStatus.Unavailable -> HintText(stringResource(R.string.connection_local_unavailable))
        LocalNameStatus.Disabled -> Unit
    }
}
