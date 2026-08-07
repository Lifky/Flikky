package com.example.flikky.util

import java.util.Locale

fun parseThemeSeed(input: String): Long? {
    val rgb = input.trim().removePrefix("#")
    if (!rgb.matches(Regex("[0-9A-Fa-f]{6}"))) return null
    return normalizeThemeSeedArgb(rgb.toLong(16))
}

fun formatThemeSeed(argb: Long): String = buildString(7) {
    append('#')
    append((argb and 0xFFFFFFL).toString(16).uppercase(Locale.ROOT).padStart(6, '0'))
}

fun normalizeThemeSeedArgb(argb: Long): Long = 0xFF000000L or (argb and 0xFFFFFFL)
