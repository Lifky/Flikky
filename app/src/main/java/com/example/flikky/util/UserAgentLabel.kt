package com.example.flikky.util

/**
 * UA 字符串 → 设备默认名的标签，形如 `Chrome · Windows`（spec §5.1）。品牌名不翻译。
 * 识别不出浏览器或系统时返回 null，由界面渲染为「浏览器」。
 *
 * 判定顺序是 UA 惯例，不能随意调换：Edge / Opera / Samsung 的 UA 都含 `Chrome/`，必须先判；
 * Chrome 的 UA 含 `Safari/`，所以 Safari 最后判。系统同理：Android 含 `Linux`、iPhone 含 `Mac OS X`、
 * ChromeOS 含 `X11`，都要先于更宽的那个判。
 *
 * 已知限制：iPadOS 的 Safari 默认伪装成 Mac，会显示 `Safari · macOS`；网页侧识别不出来。
 */
object UserAgentLabel {
    fun from(userAgent: String?): String? {
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: return null
        val browser = browserOf(ua) ?: return null
        val os = osOf(ua) ?: return null
        return "$browser · $os"
    }

    private fun browserOf(ua: String): String? = when {
        "Edg/" in ua || "EdgA/" in ua || "EdgiOS/" in ua -> "Edge"
        "OPR/" in ua || "Opera" in ua -> "Opera"
        "SamsungBrowser/" in ua -> "Samsung" // 不用全称「Samsung Internet」：加系统与编号后会超过改名框 20 字的上限
        "Firefox/" in ua || "FxiOS/" in ua -> "Firefox"
        "Chrome/" in ua || "CriOS/" in ua -> "Chrome"
        "Safari/" in ua -> "Safari"
        else -> null
    }

    private fun osOf(ua: String): String? = when {
        "Windows" in ua -> "Windows"
        "CrOS" in ua -> "ChromeOS"
        "Android" in ua -> "Android"
        "iPad" in ua -> "iPadOS"
        "iPhone" in ua || "iPod" in ua -> "iOS"
        "Mac OS X" in ua || "Macintosh" in ua -> "macOS"
        "Linux" in ua -> "Linux"
        else -> null
    }
}
