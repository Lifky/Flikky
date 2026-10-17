package com.example.flikky.export

import com.example.flikky.session.Origin
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SettingsExport(
    val themeMode: String? = null,
    val presetTheme: String? = null,
    val customThemeSeedArgb: Long? = null,
    val contrastLevel: String? = null,
    val darkMode: String? = null,
    val amoled: Boolean? = null,
    val phoneAvatarId: Int? = null,
    val phoneAvatarKey: String? = null,
    val browserAvatarKey: String? = null,
    val backgroundMode: String? = null,
    val backgroundValue: String? = null,
    val deviceName: String? = null,
    @JsonNames("recallBetaEnabled")
    val recallEnabled: Boolean? = null,
    val allowPeerRecall: Boolean? = null,
    @JsonNames("favoriteBetaEnabled")
    val favoriteEnabled: Boolean? = null,
    /** v1.21.0 peer gate, separate from the app-side [favoriteEnabled] feature flag. */
    val favoriteBrowsingEnabled: Boolean? = null,
    /** D78 peer gate: the browser may favorite session messages. */
    val allowPeerFavorite: Boolean? = null,
    val requirePin: Boolean? = null,
    val historyRetainLimit: Int? = null,
    val thumbnailCacheLimitMb: Int? = null,
    val bubbleCornerRadius: Int? = null,
    val messageActionStyle: String? = null,
    val avatarGrouping: String? = null,
    val senderNameMode: String? = null,
    val allowBackDuringSession: Boolean? = null,
    val sessionTimestampEnabled: Boolean? = null,
    val keepScreenOnDuringSession: Boolean? = null,
    val hostNumber: Int? = null,
    val customPort: Int? = null,
    val localNameEnabled: Boolean? = null,
    val storageBrowsingEnabled: Boolean? = null,
    val albumBrowsingEnabled: Boolean? = null,
    val showHiddenFiles: Boolean? = null,
    val leadingShape: String? = null,
    val leadingColorMode: String? = null,
    /** 旧字段。v1.20.0 起只读不写，导入时回落到 [homeSort]。 */
    val sortMode: String? = null,
    val groupMode: String? = null,
    /** v1.20.0 起：四个界面各自的排序，形态是 `SortSpec.format()`（如 `TIME:desc`）。 */
    val homeSort: String? = null,
    val favoritesSort: String? = null,
    val filesSort: String? = null,
    val storageSort: String? = null,
    val animationSpeed: String? = null,
    val autoCheckUpdate: Boolean? = null,
)

/**
 * v1.23：会话参与设备（spec §8.2）。既是导出模型也是归档 JSON 形状 —— 同 [FavoriteExport] 的做法。
 * 全部字段有默认值，旧归档、旧版 App 导入都不受影响（导入端 `ignoreUnknownKeys`）。
 */
@Serializable
data class SessionDeviceExport(
    val deviceId: String,
    val kind: String = "BROWSER",
    val name: String = "",
    val defaultLabel: String? = null,
    val defaultOrdinal: Int = 1,
    val avatarKey: String = "icon:desktop_windows",
    val firstSeenAt: Long = 0L,
)

data class SessionExport(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val pinned: Boolean,
    val messages: List<MessageExport>,
    val devices: List<SessionDeviceExport> = emptyList(),
)

sealed class MessageExport {
    abstract val ts: Long
    abstract val origin: Origin

    data class Text(
        override val ts: Long,
        override val origin: Origin,
        val content: String,
        val senderDeviceId: String? = null,
    ) : MessageExport()

    data class File(
        override val ts: Long,
        override val origin: Origin,
        val fileId: String,
        val name: String,
        val mime: String,
        val sizeBytes: Long,
        val status: String? = null,
        val senderDeviceId: String? = null,
    ) : MessageExport()
}
