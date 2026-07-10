package com.example.flikky.export

import com.example.flikky.session.Origin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun Origin.wireName(): String = when (this) {
    Origin.PHONE -> "PHONE"
    Origin.BROWSER -> "BROWSER"
}

internal fun wireNameToOrigin(name: String): Origin = when (name) {
    "PHONE" -> Origin.PHONE
    "BROWSER" -> Origin.BROWSER
    else -> Origin.PHONE
}

@Serializable
internal data class BackupManifestDto(
    val schemaVersion: Int,
    val scope: ExportScope,
    val exportedAt: Long,
)

@Serializable
internal data class FavoritesArchiveDto(
    val groups: List<FavoriteGroupExport>,
    val favorites: List<FavoriteExport>,
)

@Serializable
internal data class SessionDto(
    val sessionId: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val pinned: Boolean,
    val messages: List<MessageDto>,
)

@Serializable
internal sealed class MessageDto {
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
