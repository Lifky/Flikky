package com.example.flikky.export

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ExportFileName {
    fun build(
        scope: ExportScope,
        nowMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val kind = when (scope) {
            ExportScope.SESSIONS -> "sessions"
            ExportScope.FAVORITES -> "favorites"
            ExportScope.SETTINGS -> "settings"
            ExportScope.ALL -> "all"
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            this.timeZone = timeZone
        }.format(Date(nowMs))
        return "flikky-$kind-$timestamp.zip"
    }
}
