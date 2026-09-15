package com.example.flikky.util

import java.net.URLConnection
import java.util.Locale

object MimeGuess {
    const val APK_MIME = "application/vnd.android.package-archive"
    private val fallback = mapOf("apk" to APK_MIME)

    fun fromName(name: String): String? {
        URLConnection.guessContentTypeFromName(name)?.let { return it }
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return ext.takeIf { it.isNotEmpty() }?.let(fallback::get)
    }
}
