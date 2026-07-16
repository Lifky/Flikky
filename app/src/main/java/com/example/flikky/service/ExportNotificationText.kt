package com.example.flikky.service

import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.ExportScope

/**
 * Pure Android-free helper that aggregates an [ExportSnapshot] for the
 * export-mode foreground-service notification.
 *
 * Android resources turn the returned scope/count/byte summary into localized
 * text; keeping the aggregation here lets it be unit-tested on the JVM without
 * spinning up the Android notification stack.
 */
object ExportNotificationText {
    data class Summary(
        val scope: ExportScope,
        val itemCount: Int,
        val formattedBytes: String,
    )

    /**
     * - Item count: sessions or favorites, depending on [ExportSnapshot.scope].
     * - Bytes: sum of file-message and favorite-file sizes in the snapshot.
     *   - < 1 MB  → shown as "X KB"
     *   - ≥ 1 MB  → shown as "X.X MB"
     *   - 0 bytes → "0 MB"
     */
    fun summary(snapshot: ExportSnapshot): Summary {
        val sessionCount = snapshot.sessions.size
        val sessionBytes = snapshot.sessions.sumOf { s ->
            s.messages.filterIsInstance<MessageExport.File>().sumOf { it.sizeBytes }
        }
        val favoriteBytes = snapshot.favorites
            .filter { it.kind == "FILE" }
            .sumOf { it.fileSize ?: 0L }
        val totalBytes = sessionBytes + favoriteBytes
        val itemCount = when (snapshot.scope) {
            ExportScope.SESSIONS -> sessionCount
            ExportScope.FAVORITES -> snapshot.favorites.size
            ExportScope.SETTINGS, ExportScope.ALL -> 0
        }
        return Summary(snapshot.scope, itemCount, formatBytes(totalBytes))
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
