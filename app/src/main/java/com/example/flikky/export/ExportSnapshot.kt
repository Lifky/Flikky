package com.example.flikky.export

import com.example.flikky.session.Origin
import kotlinx.serialization.Serializable

@Serializable
enum class ExportScope {
    SESSIONS,
    FAVORITES,
    SETTINGS,
    ALL,
}

data class ExportSnapshot(
    val sessions: List<SessionExport> = emptyList(),
    val exportedAt: Long,
    val scope: ExportScope = ExportScope.SESSIONS,
    val favoriteGroups: List<FavoriteGroupExport> = emptyList(),
    val favorites: List<FavoriteExport> = emptyList(),
    val settings: SettingsExport? = null,
)

@Serializable
data class FavoriteGroupExport(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
)

@Serializable
data class FavoriteExport(
    val id: Long,
    val sourceSessionId: Long,
    val sourceMessageId: Long,
    val kind: String,
    val textContent: String? = null,
    val fileId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileMime: String? = null,
    val groupId: Long? = null,
    val createdAt: Long,
    val sourceSessionName: String? = null,
    val origin: String? = null,
    val relativePath: String? = null,
)

@Serializable
data class SettingsExport(
    val themeMode: String? = null,
    val presetTheme: String? = null,
    val contrastLevel: String? = null,
    val darkMode: String? = null,
    val amoled: Boolean? = null,
    val phoneAvatarId: Int? = null,
    val phoneAvatarKey: String? = null,
    val backgroundMode: String? = null,
    val backgroundValue: String? = null,
    val deviceName: String? = null,
    val recallBetaEnabled: Boolean? = null,
    val favoriteBetaEnabled: Boolean? = null,
    val requirePin: Boolean? = null,
    val historyRetainLimit: Int? = null,
    val bubbleCornerRadius: Int? = null,
    val messageActionStyle: String? = null,
    val avatarGrouping: String? = null,
    val allowBackDuringSession: Boolean? = null,
    val sortMode: String? = null,
    val groupMode: String? = null,
    val animationSpeed: String? = null,
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
