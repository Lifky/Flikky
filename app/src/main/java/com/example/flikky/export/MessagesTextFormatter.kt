package com.example.flikky.export

import com.example.flikky.session.Origin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formats a [SessionExport] into the human-readable `messages.txt` content
 * described in the v1.2 design spec (§4.3).
 *
 * Pure Kotlin: no Android dependencies. Time zone is injectable so tests can
 * pin a deterministic zone (UTC) regardless of the host JVM default.
 */
object MessagesTextFormatter {

    private const val TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun format(
        session: SessionExport,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val dateFormat = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).apply {
            this.timeZone = timeZone
        }

        val messages = session.messages
        val fileCount = messages.count { it is MessageExport.File }
        val startedStr = dateFormat.format(Date(session.startedAt))
        val endedStr = session.endedAt?.let { dateFormat.format(Date(it)) } ?: "进行中"

        val sb = StringBuilder()
        sb.append("# 会话名: ").append(session.name).append('\n')
        sb.append("# sessionId: ").append(session.id).append('\n')
        sb.append("# 起止: ").append(startedStr).append(" ~ ").append(endedStr).append('\n')
        sb.append("# 消息: ").append(messages.size).append(" 条, 文件: ")
            .append(fileCount).append(" 个").append('\n')
        sb.append('\n')

        for (msg in messages) {
            val ts = dateFormat.format(Date(msg.ts))
            val originTag = formatOrigin(msg.origin)
            sb.append('[').append(ts).append("] ").append(originTag).append(' ')
            when (msg) {
                is MessageExport.Text -> sb.append(msg.content)
                is MessageExport.File -> sb.append("[文件] ")
                    .append(msg.name)
                    .append(" (")
                    .append(formatSize(msg.sizeBytes))
                    .append(')')
            }
            sb.append('\n')
        }

        return sb.toString()
    }

    private fun formatOrigin(origin: Origin): String = when (origin) {
        Origin.PHONE -> "[PHONE  ]"
        Origin.BROWSER -> "[BROWSER]"
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 0) return "--"
        if (bytes >= 1024L * 1024L) return "%.1f MB".format(bytes / 1048576.0)
        if (bytes >= 1024L) return "%.1f KB".format(bytes / 1024.0)
        return "$bytes B"
    }
}
