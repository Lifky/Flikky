package com.example.flikky.export

sealed class ExportMode {
    object Idle : ExportMode()

    data class Armed(
        val session: ExportSession,
        val snapshot: ExportSnapshot,
    ) : ExportMode()

    data class Sending(
        val session: ExportSession,
        val bytesSent: Long,
        val totalBytes: Long,
    ) : ExportMode()

    data class Done(
        val session: ExportSession,
    ) : ExportMode()
}
