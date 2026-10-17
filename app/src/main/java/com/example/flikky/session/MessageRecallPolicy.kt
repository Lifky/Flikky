package com.example.flikky.session

fun canRecallMessage(
    requesterOrigin: Origin,
    messageOrigin: Origin,
    recallEnabled: Boolean,
    allowPeerRecall: Boolean,
): Boolean = recallEnabled && (requesterOrigin == messageOrigin || allowPeerRecall)

/**
 * 浏览器撤回（v1.23 多设备）：只有**自己设备**发的才算自己的消息。别的电脑发的与手机发的一样算对端消息，
 * 要手机打开「允许撤回对端消息」。只比来源会让电脑 B 撤回电脑 A 的消息 —— 所有浏览器的来源都是 BROWSER。
 * 没有发送设备的旧消息（v1.23 之前）沿用旧规则：浏览器发的算自己的。
 */
fun canBrowserRecall(
    requesterDeviceId: String,
    message: Message,
    recallEnabled: Boolean,
    allowPeerRecall: Boolean,
): Boolean {
    val own = message.origin == Origin.BROWSER &&
        (message.senderDeviceId == null || message.senderDeviceId == requesterDeviceId)
    return recallEnabled && (own || allowPeerRecall)
}
