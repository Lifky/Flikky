package com.example.flikky.service

import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.ExportScope
import com.example.flikky.util.formatBytes

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
     *   - < 1 KB  → shown as bytes
     *   - ≥ 1 KB  → shown with a binary unit and one decimal place
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
}
