package com.example.flikky.export

import com.example.flikky.session.Origin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Formats a [SessionExport] into the machine-readable `messages.json` content
 * described in the v1.2 design spec (§4.4).
 *
 * Pure Kotlin: no Android dependencies. Uses internal `@Serializable` DTOs so
 * the public [MessageExport] / [SessionExport] data classes stay serialization-
 * framework free.
 *
 * Known v1.2 limitation: `relativePath` is simply `"files/" + file.name`.
 * De-duplication for duplicate file names within one session is deferred to
 * [ZipExporter] (T4), which will rewrite both the zip entry path and the
 * `relativePath` value emitted here. This formatter by itself does not
 * attempt any renaming.
 */
object MessagesJsonFormatter {

    private val DefaultJson: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun format(session: SessionExport, json: Json = DefaultJson): String {
        val dto = session.toDto()
        return json.encodeToString(dto)
    }

    // --- internal DTOs (kept private so they don't leak serialization coupling) ---

    @Serializable
    private data class SessionDto(
        val sessionId: Long,
        val name: String,
        val startedAt: Long,
        val endedAt: Long?,
        val pinned: Boolean,
        val messages: List<MessageDto>,
    )

    @Serializable
    private sealed class MessageDto {
        abstract val ts: Long
        abstract val origin: String

        @Serializable
        @SerialName("text")
        data class TextDto(
            override val ts: Long,
            override val origin: String,
            val content: String,
        ) : MessageDto()

        @Serializable
        @SerialName("file")
        data class FileDto(
            override val ts: Long,
            override val origin: String,
            val fileId: String,
            val name: String,
            val mime: String,
            val sizeBytes: Long,
            val relativePath: String,
        ) : MessageDto()
    }

    private fun SessionExport.toDto(): SessionDto = SessionDto(
        sessionId = id,
        name = name,
        startedAt = startedAt,
        endedAt = endedAt,
        pinned = pinned,
        messages = messages.map { it.toDto() },
    )

    private fun MessageExport.toDto(): MessageDto = when (this) {
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
            relativePath = "files/$name",
        )
    }

    private fun Origin.wireName(): String = when (this) {
        Origin.PHONE -> "PHONE"
        Origin.BROWSER -> "BROWSER"
    }
}
