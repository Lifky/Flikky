package com.example.flikky.service

import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport

/**
 * Pure Android-free helper that turns an [ExportSnapshot] into the human-readable
 * body shown in the export-mode foreground-service notification.
 *
 * Extracted so the aggregation logic (session count + total file bytes → "N 个
 * 会话 / X MB 可下载") can be unit-tested on the JVM without spinning up the
 * Android notification stack.
 */
object ExportNotificationText {
    const val TITLE: String = "Flikky 正在提供导出"

    /**
     * Format: "N 个会话 / X MB 可下载"
     * - Sessions: count of sessions in snapshot.
     * - Bytes: sum of file-message sizes across all sessions.
     *   - < 1 MB  → shown as "X KB"
     *   - ≥ 1 MB  → shown as "X.X MB"
     *   - 0 bytes → "0 MB"
     */
    fun body(snapshot: ExportSnapshot): String {
        val sessionCount = snapshot.sessions.size
        val totalBytes = snapshot.sessions.sumOf { s ->
            s.messages.filterIsInstance<MessageExport.File>().sumOf { it.sizeBytes }
        }
        return "$sessionCount 个会话 / ${formatBytes(totalBytes)} 可下载"
    }

    internal fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        if (mb < 1.0) {
            val kb = bytes.toDouble() / 1024.0
            // < 1 KB → show as 1 KB floor; otherwise round to whole KB
            val kbRounded = if (kb < 1.0) 1 else kb.toLong()
            return "$kbRounded KB"
        }
        // One decimal for MB; trim trailing ".0"
        val oneDecimal = (Math.round(mb * 10.0) / 10.0)
        val asString = if (oneDecimal == oneDecimal.toLong().toDouble()) {
            "${oneDecimal.toLong()} MB"
        } else {
            String.format(java.util.Locale.US, "%.1f MB", oneDecimal)
        }
        return asString
    }
}
