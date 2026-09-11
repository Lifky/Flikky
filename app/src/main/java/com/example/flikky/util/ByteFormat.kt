package com.example.flikky.util

import java.util.Locale

/**
 * Formats bytes with binary `B` / `KB` / `MB` / `GB` units and one decimal
 * place above bytes.
 *
 * Invalid values return `0 B`. This folds the strictest defensive behavior of
 * the six formatters this function replaced into the single shared boundary.
 */
fun formatBytes(bytes: Number): String {
    val raw = bytes.toDouble()
    if (!raw.isFinite() || raw < 0.0) return "0 B"
    if (raw < 1024.0) return "${raw.toLong()} B"

    val units = arrayOf("KB", "MB", "GB")
    var value = raw / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}
