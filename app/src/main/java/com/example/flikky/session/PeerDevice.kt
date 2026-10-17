package com.example.flikky.session

enum class DeviceKind { BROWSER, APP }

/**
 * 一台对端设备（spec §3）。一台设备 = 一份登录凭据，同一浏览器的多个标签页共享它。
 *
 * [deviceId] 由服务端签发、公开，只用于指代 —— 不能代替凭据。
 * [name] 为空表示用默认名（[defaultLabel] + [defaultOrdinal]）。
 * [visible]：经 PIN 签发即为真；关 PIN 自动签发的要等第一条 WebSocket（幽灵设备防护，spec §4.3）。
 */
data class PeerDevice(
    val deviceId: String,
    val kind: DeviceKind = DeviceKind.BROWSER,
    val name: String = "",
    val defaultLabel: String? = null,
    val defaultOrdinal: Int = 1,
    val avatarKey: String,
    val avatarExplicit: Boolean = false,
    val connections: Int = 0,
    val connectedAt: Long,
    val visible: Boolean = false,
) {
    val online: Boolean get() = connections > 0
}

/**
 * 默认名只取浏览器名（`Chrome`），同名第二台起加编号；识别不出用 [unknownLabel]（「浏览器」，随语言）。
 * 完整的「浏览器 · 系统」只放设备面板副标题（装机 Screenshot_6~8：完整标签在顶栏必被截断）。
 * 浏览器端同一规则：device-self.js / peers.js 的 `shortLabel`。
 */
fun PeerDevice.defaultName(unknownLabel: String): String =
    (shortLabel() ?: unknownLabel) + if (defaultOrdinal > 1) " $defaultOrdinal" else ""

/** `Chrome · Windows` → `Chrome`。编号按它分配（DeviceRegistry），否则两台系统不同的 Edge 会同名。 */
fun PeerDevice.shortLabel(): String? = defaultLabel?.substringBefore(" · ")

fun PeerDevice.displayName(unknownLabel: String): String = name.ifBlank { defaultName(unknownLabel) }

/** 消息气泡的对端头像：发送那台设备的头像；旧消息或已不认识的设备用 [fallback]（spec §6.5 #16）。 */
fun peerAvatarFor(senderDeviceId: String?, avatarsById: Map<String, String>, fallback: String): String =
    senderDeviceId?.let(avatarsById::get) ?: fallback
