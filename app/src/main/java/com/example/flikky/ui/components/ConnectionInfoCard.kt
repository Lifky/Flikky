package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flikky.R
import com.example.flikky.session.ConnectionAddresses
import com.example.flikky.session.LocalNameStatus
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.LastNonNull
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
        // 间距按关系给（2026-09-27 装机「排版乱」）：原来整张卡一个 spacedBy(md)，有关系的不靠近、
        // 没关系的不拉开。现在分三组 —— 标题 + 地址 + 地址说明 / 二维码 / PIN —— 组内 4dp，组间 16dp。
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ⓘ 讲的是局域网名称那一行，没有那一行就不放（用户 2026-09-27 方案 C）。
            TitleRow(showInfo = address.localUrl != null)
            // 第一行永远是 IP：最稳定，二维码编的也是它（D63、D77）。
            // 两行同一结构（地址 + 行尾复制）、**同一字号、绝不换行**（用户 2026-09-26）：
            // 按较长那行在可用宽度内能放下的最大字号一起缩，端口号不会被挤到下一行。
            // 用 App 字体而非等宽：系统等宽字体不跟随 MiSans 与字重，小米上和周围文字不搭（用户 2026-09-27）。
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val base = MaterialTheme.typography.titleLarge
                val urls = listOfNotNull(address.primaryUrl, address.localUrl)
                val availablePx = with(density) { (maxWidth - COPY_BUTTON_GAP - COPY_BUTTON_WIDTH).roundToPx() }
                val (sizeSp, textWidthPx) = remember(urls, availablePx, base) {
                    fun widthAt(url: String, sp: Float) =
                        measurer.measure(url, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width
                    val size = fitFontSize(maxSp = base.fontSize.value, minSp = MIN_ADDRESS_SP, stepSp = 1f) { sp ->
                        urls.all { widthAt(it, sp) <= availablePx }
                    }
                    size to urls.maxOf { widthAt(it, size) }.coerceAtMost(availablePx)
                }
                val addressStyle = base.copy(fontSize = sizeSp.sp)
                // 两行文字占同一个宽度（较长那行的），整组居中：行尾复制图标落在同一条竖线上。
                val textWidth = with(density) { textWidthPx.toDp() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AddressRow(
                        url = address.primaryUrl,
                        style = addressStyle,
                        textWidth = textWidth,
                        copyDescription = stringResource(R.string.connection_copy_address),
                        onCopy = { scope.launch { clipboard.setPlainText(address.primaryUrl) } },
                    )
                    address.localUrl?.let { localUrl ->
                        Spacer(Modifier.height(ADDRESS_ROW_GAP))
                        AddressRow(
                            url = localUrl,
                            style = addressStyle,
                            textWidth = textWidth,
                            copyDescription = stringResource(R.string.connection_copy_local_address),
                            onCopy = { scope.launch { clipboard.setPlainText(localUrl) } },
                        )
                    }
                }
            }
            // 两个地址用的是同一个端口：说明放在两行之后，不夹在中间（夹着读起来像只说第一行）。
            address.portNotice?.let { notice ->
                AddressCaption(stringResource(R.string.connection_port_moved, notice.requested, notice.actual))
            }
            LocalNameStatusLine(address.localName, onAdoptNumber)
            Spacer(Modifier.height(GROUP_GAP))
            FilledTonalIconButton(onClick = { showQr = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_qr_code_2),
                    contentDescription = stringResource(R.string.connection_show_qr),
                )
            }
            Spacer(Modifier.height(GROUP_GAP))
            if (requirePin) {
                Text(
                    text = "PIN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
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
                Spacer(Modifier.height(Spacing.xs))
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

/** [ConnectionInfoCard] 的全部输入。 */
data class ConnectionCardInputs(val address: ConnectionAddresses, val pin: String, val requirePin: Boolean)

/**
 * 卡片停在最后一帧：服务一停，地址与 PIN 就被清空，页面却还要播完返回动画；
 * 卡片若跟着消失，下面的内容会塌成半截（2026-09-27 装机反馈）。地址未就绪前返回 null。
 */
@Composable
fun rememberLastCardInputs(address: ConnectionAddresses?, pin: String, requirePin: Boolean): ConnectionCardInputs? {
    val last = remember { LastNonNull<ConnectionCardInputs>() }
    return last.update(address?.let { ConnectionCardInputs(it, pin, requirePin) })
}

/** 同组内相邻两行之间（两行地址、地址与它的说明）。48dp 的按钮行自带留白，4dp 就够分开。 */
private val ADDRESS_ROW_GAP = Spacing.xs

/** 组与组之间（地址组 / 二维码 / PIN）。 */
private val GROUP_GAP = Spacing.lg

/** M3 IconButton 的最小触摸尺寸。标题行按它定高，有没有 ⓘ 行高都一样。 */
private val INFO_BUTTON_SIZE = 48.dp

/** 行尾复制按钮的最小触摸宽度（M3：48dp）。算字号时要把它从可用宽度里扣掉。 */
private val COPY_BUTTON_WIDTH = 48.dp

/** 地址与复制按钮之间的间距：tonal 圆底只比触摸区小 4dp，不加间距就贴着字。同样要从可用宽度里扣掉。 */
private val COPY_BUTTON_GAP = Spacing.sm
private const val MIN_ADDRESS_SP = 14f

@Composable
private fun AddressRow(
    url: String,
    style: TextStyle,
    textWidth: Dp,
    copyDescription: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 字号与宽度已由调用方按可用宽度算好；这里再加单行兜底，极端窄屏宁可省略也不换行。
        Text(
            text = url,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(textWidth),
        )
        Spacer(Modifier.width(COPY_BUTTON_GAP))
        // 与二维码按钮同一个组件、同一套默认色（用户 2026-09-27）。
        FilledTonalIconButton(onClick = onCopy) {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = copyDescription,
            )
        }
    }
}

/**
 * 「在浏览器中打开」+ ⓘ。标题居中、ⓘ 挂在右边，左边补一个同宽空位：
 * 否则居中的是「文字 + 按钮」整体，文字本身偏左，和下面各行不在同一条中轴上。
 */
@Composable
private fun TitleRow(showInfo: Boolean) {
    Row(
        modifier = Modifier.heightIn(min = INFO_BUTTON_SIZE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showInfo) Spacer(Modifier.width(INFO_BUTTON_SIZE))
        Text(
            text = stringResource(R.string.connection_open_in_browser),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showInfo) {
            // 与设置页 ⓘ 同一实现。原先的「以上任意地址均可打开」一行并进这里的说明（用户 2026-09-27）。
            InfoIconButton(
                title = stringResource(R.string.settings_access_address),
                text = stringResource(R.string.connection_local_info),
            )
        }
    }
}

/** 地址组里的一行说明：紧跟在地址下面，与地址同组。 */
@Composable
private fun AddressCaption(text: String) {
    Spacer(Modifier.height(ADDRESS_ROW_GAP))
    HintText(text)
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
        is LocalNameStatus.Probing -> AddressCaption(stringResource(R.string.connection_local_probing))
        is LocalNameStatus.Renamed -> {
            AddressCaption(
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
        LocalNameStatus.Unavailable -> AddressCaption(stringResource(R.string.connection_local_unavailable))
        // 名字已就绪：说明在标题旁的 ⓘ 里，这里不再单占一行（用户 2026-09-27 方案 C）。
        is LocalNameStatus.Owned, LocalNameStatus.Disabled -> Unit
    }
}
