package com.example.flikky.util

private const val APK_NAME_MAX_BYTES = 120

fun apkFileName(label: String, versionName: String?, versionCode: Long, packageName: String): String {
    val cleanLabel = sanitizeApkPart(label).ifEmpty { sanitizeApkPart(packageName) }.ifEmpty { "app" }
    val version = versionName?.let(::sanitizeApkPart)?.ifEmpty { null } ?: versionCode.toString()
    val tail = "_$version.apk"
    return truncateUtf8(cleanLabel, APK_NAME_MAX_BYTES - tail.toByteArray().size) + tail
}

private fun sanitizeApkPart(raw: String): String = buildString(raw.length) {
    raw.forEach { ch ->
        if (ch in "/\\:*?\"<>|" || ch.isISOControl() || ch.isWhitespace()) append('_') else append(ch)
    }
}.replace(Regex("_+"), "_").trim('_', '.')

private fun truncateUtf8(text: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    val out = StringBuilder()
    var used = 0
    var index = 0
    while (index < text.length) {
        val point = text.codePointAt(index)
        val piece = String(Character.toChars(point))
        val cost = piece.toByteArray(Charsets.UTF_8).size
        if (used + cost > maxBytes) break
        out.append(piece); used += cost; index += Character.charCount(point)
    }
    return out.toString().trim('_', '.')
}
