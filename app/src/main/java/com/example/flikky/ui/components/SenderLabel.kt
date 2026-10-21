package com.example.flikky.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.flikky.data.settings.SenderNameMode
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.theme.Spacing

/**
 * 气泡上方的发送者名字（v1.23 多设备）。两端同一规则，浏览器端见 app.js 的 `senderLabelVisible`：
 * 「自动」= 会话里有 2 台以上设备时显示（一对一时名字只是噪音）。会话页数仍在会话里的（被手机断开的不算，
 * v1.24 装机反馈：同一个浏览器被断开后重进，会被算成两台）；历史页没有断开记录，数本场出现过的。
 */
internal fun senderNamesEnabled(mode: SenderNameMode, peerDeviceCount: Int): Boolean = when (mode) {
    SenderNameMode.AUTO -> peerDeviceCount >= 2
    SenderNameMode.ALWAYS -> true
    SenderNameMode.OFF -> false
}

/**
 * 第 [index] 条要不要标名字（两侧都标，装机反馈：别人有、自己没有反而别扭）。[senders] 是 [groupKey]。
 * [everyMessage]（常驻按钮）：每条都标 —— 每条下面都有一排按钮，只标首条会让后面几条上方空一块。
 * 否则（悬浮工具栏）：同一发送者连续几条只标第一条；[breakBefore]（前面有时间分隔条）时重新开始。
 */
internal fun senderLabelAt(senders: List<String>, index: Int, everyMessage: Boolean, breakBefore: Boolean = false): Boolean =
    everyMessage || index == 0 || breakBefore || senders[index - 1] != senders[index]

/** 旧数据（v1.23 之前）里浏览器发的消息没有发送设备，统一算作一台「浏览器」。 */
internal const val LEGACY_BROWSER_SENDER = "legacy-browser"
internal const val PHONE_SENDER = "phone"

/** 对方一侧的发送者键：手机自己发的为 null（不标名字）。 */
internal fun Message.peerSenderKey(): String? =
    if (origin == Origin.PHONE) null else senderDeviceId ?: LEGACY_BROWSER_SENDER

/** 头像分组用的键：按发送设备分组，而不是按来源 —— 两台电脑交替发言不能并成一组。 */
internal fun Message.groupKey(): String = peerSenderKey() ?: PHONE_SENDER

@Composable
fun SenderLabel(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(bottom = Spacing.xs),
    )
}
