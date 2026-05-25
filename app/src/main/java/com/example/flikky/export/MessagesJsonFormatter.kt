package com.example.flikky.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object MessagesJsonFormatter {

    private val DefaultJson: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun format(
        session: SessionExport,
        json: Json = DefaultJson,
        fileIdToRelativePath: Map<String, String>? = null,
    ): String {
        val dto = session.toDto(fileIdToRelativePath)
        return json.encodeToString(dto)
    }

    private fun SessionExport.toDto(fileIdToRelativePath: Map<String, String>?): SessionDto =
        SessionDto(
            sessionId = id,
            name = name,
            startedAt = startedAt,
            endedAt = endedAt,
            pinned = pinned,
            messages = messages.map { it.toDto(fileIdToRelativePath) },
        )

    private fun MessageExport.toDto(fileIdToRelativePath: Map<String, String>?): MessageDto =
        when (this) {
            is MessageExport.Text -> MessageDto.TextDto(
                ts = ts,
                origin = origin.wireName(),
                content = content,
            )
            is MessageExport.File -> MessageDto.FileDto(
                ts = ts,
                origin = origin.wireName(),
                fileId = fileId,
                name = name,
                mime = mime,
                sizeBytes = sizeBytes,
                relativePath = fileIdToRelativePath?.get(fileId) ?: "files/$name",
            )
        }
}
