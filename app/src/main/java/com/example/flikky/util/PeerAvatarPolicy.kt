package com.example.flikky.util

/**
 * 对端设备头像（spec §5.3）：浏览器自选优先，否则跟随手机设置里的「浏览器默认头像」。
 * 取代 v1.17 的 BrowserAvatarHelloPolicy —— 那一版的「手机值为准」只认一个浏览器。
 */
object PeerAvatarPolicy {
    const val DEFAULT_BROWSER = "icon:desktop_windows"

    data class Decision(
        val avatarKey: String,
        /** 是不是这台设备「自己的主意」；手机改默认值时只有 false 的设备跟着变。 */
        val explicit: Boolean,
        /** 迁移路径：手机从未存过默认值时，把浏览器本地的旧头像存成默认值。 */
        val persistAsDefault: Boolean,
    )

    /** 浏览器连接时第一次上报头像（[announcedExplicit] 来自它本地的 `flikky_avatar_explicit`）。 */
    fun onHello(announcedKey: String?, announcedExplicit: Boolean, storedDefault: String?): Decision {
        val announced = announcedKey?.takeIf(::isAcceptableKey)
        return when {
            announced != null && announcedExplicit -> Decision(announced, explicit = true, persistAsDefault = false)
            announced != null && storedDefault == null -> Decision(announced, explicit = false, persistAsDefault = true)
            else -> Decision(storedDefault ?: DEFAULT_BROWSER, explicit = false, persistAsDefault = false)
        }
    }

    /** 服务端只接受 `icon:` / `char:` 两种形态、长度 6..48、不含控制字符与引号（会进 JSON 与日志）。 */
    fun isAcceptableKey(key: String?): Boolean {
        if (key == null || key.length !in 6..48) return false
        if (key.any { it.isISOControl() || it == '"' }) return false
        return key.startsWith("icon:") || key.startsWith("char:")
    }
}
