package com.example.flikky.export

import com.example.flikky.session.Origin

data class ExportSnapshot(
    val sessions: List<SessionExport>,
    val exportedAt: Long,
)

data class SessionExport(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val pinned: Boolean,
    val messages: List<MessageExport>,
)

sealed class MessageExport {
    abstract val ts: Long
    abstract val origin: Origin

    data class Text(
        override val ts: Long,
        override val origin: Origin,
        val content: String,
    ) : MessageExport()

    data class File(
        override val ts: Long,
        override val origin: Origin,
        val fileId: String,
        val name: String,
        val mime: String,
        val sizeBytes: Long,
    ) : MessageExport()
}
