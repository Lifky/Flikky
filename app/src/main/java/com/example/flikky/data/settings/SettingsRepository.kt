package com.example.flikky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.flikky.export.SettingsExport
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import com.example.flikky.util.LeadingColorMode
import com.example.flikky.util.LeadingShape
import com.example.flikky.util.normalizeThemeSeedArgb

class SettingsRepository(private val ds: DataStore<Preferences>) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val preset = stringPreferencesKey("preset_theme")
        val customThemeSeed = longPreferencesKey("custom_theme_seed")
        val contrast = stringPreferencesKey("contrast_level")
        val darkMode = stringPreferencesKey("dark_mode")
        val amoled = booleanPreferencesKey("amoled")
        val phoneAvatar = intPreferencesKey("phone_avatar")
        val phoneAvatarKey = stringPreferencesKey("phone_avatar_key")
        val browserAvatarKey = stringPreferencesKey("browser_avatar_key")
        val bgMode = stringPreferencesKey("bg_mode")
        val bgValue = stringPreferencesKey("bg_value")
        val deviceName = stringPreferencesKey("device_name")
        val recallBeta = booleanPreferencesKey("recall_beta")
        val allowPeerRecall = booleanPreferencesKey("allow_peer_recall")
        val favoriteBeta = booleanPreferencesKey("favorite_beta")
        val favoriteBrowsing = booleanPreferencesKey("favorite_browsing")
        val requirePin = booleanPreferencesKey("require_pin")
        val retainLimit = intPreferencesKey("retain_limit")
        val thumbnailCacheLimitMb = intPreferencesKey("thumbnail_cache_limit_mb")
        val bubbleCorner = intPreferencesKey("bubble_corner")
        val msgActionStyle = stringPreferencesKey("msg_action_style")
        val avatarGrouping = stringPreferencesKey("avatar_grouping")
        val allowBackDuringSession = booleanPreferencesKey("allow_back_during_session")
        val sessionTimestampEnabled = booleanPreferencesKey("session_timestamp_enabled")
        val keepScreenOnDuringSession = booleanPreferencesKey("keep_screen_on_during_session")
        val storageBrowsingEnabled = booleanPreferencesKey("storage_browsing_enabled")
        val albumBrowsingEnabled = booleanPreferencesKey("album_browsing_enabled")
        val showHiddenFiles = booleanPreferencesKey("show_hidden_files")
        val leadingShape = stringPreferencesKey("leading_shape")
        val leadingColorMode = stringPreferencesKey("leading_color_mode")
        /** 旧键。v1.20.0 起**只读不写**，仅供导入旧备份时回落到 [homeSort]。 */
        val sortMode = stringPreferencesKey("sort_mode")
        val groupMode = stringPreferencesKey("group_mode")
        val homeSort = stringPreferencesKey("home_sort")
        val favoritesSort = stringPreferencesKey("favorites_sort")
        val filesSort = stringPreferencesKey("files_sort")
        val storageSort = stringPreferencesKey("storage_sort")
        val animationSpeed = stringPreferencesKey("animation_speed")
        val autoCheckUpdate = booleanPreferencesKey("auto_check_update")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
        val lastPromptedUpdateVersion = stringPreferencesKey("last_prompted_update_version")
        val activeGroupId = longPreferencesKey("active_group_id")
        val activeFavoriteGroupId = longPreferencesKey("active_favorite_group_id")
        val recentFavoriteIds = stringPreferencesKey("recent_favorite_ids")
    }

    val settings: Flow<FlikkySettings> = ds.data.map { p ->
        FlikkySettings(
            themeMode = p[Keys.themeMode]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.PRESET,
            // 旧版本可能存了已移除的预设名（CORAL/MUSHROOM/TEAL/MIST）——valueOf 会抛，
            // runCatching 兜底回落到默认主题 ANAN_BLUE（安安蓝）。
            presetTheme = p[Keys.preset]
                ?.let { runCatching { PresetTheme.valueOf(it) }.getOrNull() }
                ?: PresetTheme.ANAN_BLUE,
            customThemeSeedArgb = normalizeThemeSeedArgb(
                p[Keys.customThemeSeed] ?: CUSTOM_THEME_SEED_DEFAULT,
            ),
            contrastLevel = p[Keys.contrast]
                ?.let { runCatching { ContrastLevel.valueOf(it) }.getOrNull() }
                ?: ContrastLevel.SYSTEM,
            darkMode = p[Keys.darkMode]?.let { DarkMode.valueOf(it) } ?: DarkMode.SYSTEM,
            amoled = p[Keys.amoled] ?: false,
            phoneAvatarId = p[Keys.phoneAvatar] ?: 0,
            phoneAvatarKey = p[Keys.phoneAvatarKey] ?: "icon:smartphone",
            browserAvatarKey = p[Keys.browserAvatarKey] ?: "icon:desktop_windows",
            background = decodeBackground(p[Keys.bgMode], p[Keys.bgValue]),
            deviceName = normalizeDeviceName(p[Keys.deviceName]),
            recallBetaEnabled = p[Keys.recallBeta] ?: true,
            allowPeerRecall = p[Keys.allowPeerRecall] ?: true,
            favoriteBetaEnabled = p[Keys.favoriteBeta] ?: false,
            favoriteBrowsingEnabled = resolveFavoriteBrowsing(
                p[Keys.favoriteBrowsing],
                p[Keys.favoriteBeta],
            ),
            requirePin = p[Keys.requirePin] ?: true,
            historyRetainLimit = (p[Keys.retainLimit] ?: 20).coerceAtLeast(-1),
            thumbnailCacheLimitMb = normalizeThumbnailCacheLimitMb(p[Keys.thumbnailCacheLimitMb]),
            bubbleCornerRadius = (p[Keys.bubbleCorner] ?: BUBBLE_CORNER_DEFAULT)
                .coerceIn(BUBBLE_CORNER_MIN, BUBBLE_CORNER_MAX),
            messageActionStyle = p[Keys.msgActionStyle]
                ?.let { runCatching { MessageActionStyle.valueOf(it) }.getOrNull() }
                ?: MessageActionStyle.INLINE,
            avatarGrouping = p[Keys.avatarGrouping]
                ?.let { runCatching { AvatarGroupingMode.valueOf(it) }.getOrNull() }
                ?: AvatarGroupingMode.EACH,
            allowBackDuringSession = p[Keys.allowBackDuringSession] ?: true,
            sessionTimestampEnabled = p[Keys.sessionTimestampEnabled] ?: false,
            keepScreenOnDuringSession = p[Keys.keepScreenOnDuringSession] ?: false,
            storageBrowsingEnabled = p[Keys.storageBrowsingEnabled] ?: false,
            albumBrowsingEnabled = p[Keys.albumBrowsingEnabled] ?: false,
            showHiddenFiles = p[Keys.showHiddenFiles] ?: false,
            leadingShape = LeadingShape.parse(p[Keys.leadingShape]),
            leadingColorMode = LeadingColorMode.parse(p[Keys.leadingColorMode]),
            groupMode = p[Keys.groupMode]
                ?.let { runCatching { GroupMode.valueOf(it) }.getOrNull() }
                ?: GroupMode.DATE,
            // 旧键回退**只给主页**：那是当时唯一有排序设置的界面。让它同时喂给
            // 另外三处，会在导入旧备份时把它们的默认值一起改掉。
            // 只取键，方向用该键的自然方向（旧键里没有方向这个概念）。
            homeSort = SortSpec.parse(p[Keys.homeSort])
                ?: p[Keys.sortMode]
                    ?.let { runCatching { SortKey.valueOf(it) }.getOrNull() }
                    ?.let { SortSpec.natural(it) }
                ?: SortSpec(SortKey.TIME, descending = true),
            favoritesSort = SortSpec.parse(p[Keys.favoritesSort])
                ?: SortSpec(SortKey.TIME, descending = true),
            filesSort = SortSpec.parse(p[Keys.filesSort])
                ?: SortSpec(SortKey.TIME, descending = true),
            storageSort = SortSpec.parse(p[Keys.storageSort]) ?: SortSpec.NameAsc,
            animationSpeed = p[Keys.animationSpeed]
                ?.let { runCatching { AnimationSpeed.valueOf(it) }.getOrNull() }
                ?: AnimationSpeed.STANDARD,
            autoCheckUpdate = p[Keys.autoCheckUpdate] ?: false,
            activeGroupId = p[Keys.activeGroupId]?.takeIf { it > 0L },
            activeFavoriteGroupId = p[Keys.activeFavoriteGroupId]?.takeIf { it > 0L },
            recentFavoriteIds = decodeRecentFavoriteIds(p[Keys.recentFavoriteIds]),
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = ds.edit { it[Keys.themeMode] = v.name }
    suspend fun setPresetTheme(v: PresetTheme) = ds.edit { it[Keys.preset] = v.name }
    suspend fun setCustomThemeSeed(v: Long) = ds.edit {
        it[Keys.customThemeSeed] = normalizeThemeSeedArgb(v)
    }
    suspend fun setContrastLevel(v: ContrastLevel) = ds.edit { it[Keys.contrast] = v.name }
    suspend fun setDarkMode(v: DarkMode) = ds.edit { it[Keys.darkMode] = v.name }
    suspend fun setAmoled(v: Boolean) = ds.edit { it[Keys.amoled] = v }
    suspend fun setPhoneAvatar(v: Int) = ds.edit { it[Keys.phoneAvatar] = v }
    suspend fun setPhoneAvatarKey(v: String) = ds.edit { it[Keys.phoneAvatarKey] = v }
    suspend fun setBrowserAvatarKey(v: String) = ds.edit { it[Keys.browserAvatarKey] = v }

    /**
     * One-shot raw read: null means the user (or a browser hello) has never persisted a
     * browser avatar - the upgrade-migration branch in BrowserAvatarHelloPolicy keys off this.
     */
    suspend fun browserAvatarKeyOrNull(): String? = ds.data.first()[Keys.browserAvatarKey]

    suspend fun setDeviceName(v: String) = ds.edit { prefs ->
        val normalized = normalizeDeviceName(v)
        if (normalized.isEmpty()) prefs.remove(Keys.deviceName)
        else prefs[Keys.deviceName] = normalized
    }
    suspend fun setRecallBeta(v: Boolean) = ds.edit { it[Keys.recallBeta] = v }
    suspend fun setAllowPeerRecall(v: Boolean) = ds.edit { it[Keys.allowPeerRecall] = v }
    suspend fun setFavoriteBeta(v: Boolean) = ds.edit { it[Keys.favoriteBeta] = v }
    suspend fun setFavoriteBrowsingEnabled(v: Boolean) = ds.edit { it[Keys.favoriteBrowsing] = v }
    suspend fun setRequirePin(v: Boolean) = ds.edit { it[Keys.requirePin] = v }
    suspend fun setHistoryRetainLimit(v: Int) = ds.edit { it[Keys.retainLimit] = v.coerceAtLeast(-1) }
    suspend fun setThumbnailCacheLimitMb(v: Int) = ds.edit {
        it[Keys.thumbnailCacheLimitMb] = normalizeThumbnailCacheLimitMb(v)
    }
    suspend fun setBubbleCornerRadius(v: Int) = ds.edit {
        it[Keys.bubbleCorner] = v.coerceIn(BUBBLE_CORNER_MIN, BUBBLE_CORNER_MAX)
    }
    suspend fun setMessageActionStyle(v: MessageActionStyle) = ds.edit { it[Keys.msgActionStyle] = v.name }
    suspend fun setAvatarGrouping(v: AvatarGroupingMode) = ds.edit { it[Keys.avatarGrouping] = v.name }
    suspend fun setAllowBackDuringSession(v: Boolean) = ds.edit { it[Keys.allowBackDuringSession] = v }
    suspend fun setSessionTimestampEnabled(v: Boolean) = ds.edit { it[Keys.sessionTimestampEnabled] = v }
    suspend fun setKeepScreenOnDuringSession(v: Boolean) = ds.edit { it[Keys.keepScreenOnDuringSession] = v }

    suspend fun setStorageBrowsingEnabled(v: Boolean) = ds.edit { it[Keys.storageBrowsingEnabled] = v }
    suspend fun setAlbumBrowsingEnabled(v: Boolean) = ds.edit { it[Keys.albumBrowsingEnabled] = v }

    suspend fun setShowHiddenFiles(v: Boolean) = ds.edit { it[Keys.showHiddenFiles] = v }
    suspend fun setLeadingShape(v: LeadingShape) = ds.edit { it[Keys.leadingShape] = v.id }
    suspend fun setLeadingColorMode(v: LeadingColorMode) = ds.edit {
        it[Keys.leadingColorMode] = v.name
    }
    suspend fun setGroupMode(v: GroupMode) = ds.edit { it[Keys.groupMode] = v.name }
    suspend fun setHomeSort(v: SortSpec) = ds.edit { it[Keys.homeSort] = v.format() }
    suspend fun setFavoritesSort(v: SortSpec) = ds.edit { it[Keys.favoritesSort] = v.format() }
    suspend fun setFilesSort(v: SortSpec) = ds.edit { it[Keys.filesSort] = v.format() }
    suspend fun setStorageSort(v: SortSpec) = ds.edit { it[Keys.storageSort] = v.format() }

    /** 只为测试旧键回退而存在 —— 生产代码不再写 `sort_mode`。 */
    internal suspend fun setLegacySortModeForTest(raw: String) =
        ds.edit { it[Keys.sortMode] = raw }

    /** 只为测试「存坏了的偏好回落默认值」而存在。 */
    internal suspend fun setRawSortForTest(key: String, raw: String) =
        ds.edit { it[stringPreferencesKey(key)] = raw }
    internal suspend fun setRawLeadingVisualForTest(shape: String, colorMode: String) = ds.edit {
        it[Keys.leadingShape] = shape
        it[Keys.leadingColorMode] = colorMode
    }
    suspend fun setAnimationSpeed(v: AnimationSpeed) = ds.edit { it[Keys.animationSpeed] = v.name }
    suspend fun setAutoCheckUpdate(v: Boolean) = ds.edit { it[Keys.autoCheckUpdate] = v }

    /** Last successful check time. Device-local state excluded from backups. */
    suspend fun lastUpdateCheckAt(): Long = ds.data.first()[Keys.lastUpdateCheckAt] ?: 0L
    suspend fun setLastUpdateCheckAt(v: Long) = ds.edit { it[Keys.lastUpdateCheckAt] = v }

    /** Last version shown by an automatic prompt. Device-local state excluded from backups. */
    suspend fun lastPromptedUpdateVersion(): String? =
        ds.data.first()[Keys.lastPromptedUpdateVersion]
    suspend fun setLastPromptedUpdateVersion(v: String) = ds.edit {
        it[Keys.lastPromptedUpdateVersion] = v
    }

    suspend fun setActiveGroup(id: Long?) = ds.edit { prefs ->
        val valid = id?.takeIf { it > 0L }
        if (valid != null) prefs[Keys.activeGroupId] = valid else prefs.remove(Keys.activeGroupId)
    }
    suspend fun setActiveFavoriteGroup(id: Long?) = ds.edit { prefs ->
        val valid = id?.takeIf { it > 0L }
        if (valid != null) prefs[Keys.activeFavoriteGroupId] = valid else prefs.remove(Keys.activeFavoriteGroupId)
    }
    suspend fun recordRecentFavorite(id: Long) = ds.edit { prefs ->
        val valid = id.takeIf { it > 0L } ?: return@edit
        val updated = (listOf(valid) + decodeRecentFavoriteIds(prefs[Keys.recentFavoriteIds]))
            .distinct()
            .take(RECENT_FAVORITE_LIMIT)
        prefs[Keys.recentFavoriteIds] = updated.joinToString(",")
    }
    suspend fun setBackground(v: BackgroundSetting) = ds.edit {
        when (v) {
            BackgroundSetting.Default -> { it[Keys.bgMode] = "DEFAULT"; it.remove(Keys.bgValue) }
            BackgroundSetting.Blank -> { it[Keys.bgMode] = "BLANK"; it.remove(Keys.bgValue) }
            is BackgroundSetting.Solid -> { it[Keys.bgMode] = "SOLID"; it[Keys.bgValue] = v.argb.toString() }
        }
    }

    /** Clears every persisted preference so all flow defaults become active again. */
    suspend fun clearAll() = ds.edit { it.clear() }

    suspend fun exportBackup(): SettingsExport {
        val s = settings.first()
        val (backgroundMode, backgroundValue) = when (val background = s.background) {
            BackgroundSetting.Default -> "DEFAULT" to null
            BackgroundSetting.Blank -> "BLANK" to null
            is BackgroundSetting.Solid -> "SOLID" to background.argb.toString()
        }
        return SettingsExport(
            themeMode = s.themeMode.name,
            presetTheme = s.presetTheme.name,
            customThemeSeedArgb = s.customThemeSeedArgb,
            contrastLevel = s.contrastLevel.name,
            darkMode = s.darkMode.name,
            amoled = s.amoled,
            phoneAvatarId = s.phoneAvatarId,
            phoneAvatarKey = s.phoneAvatarKey,
            browserAvatarKey = browserAvatarKeyOrNull(),
            backgroundMode = backgroundMode,
            backgroundValue = backgroundValue,
            deviceName = s.deviceName,
            recallEnabled = s.recallBetaEnabled,
            allowPeerRecall = s.allowPeerRecall,
            favoriteEnabled = s.favoriteBetaEnabled,
            favoriteBrowsingEnabled = s.favoriteBrowsingEnabled,
            requirePin = s.requirePin,
            historyRetainLimit = s.historyRetainLimit,
            thumbnailCacheLimitMb = s.thumbnailCacheLimitMb,
            bubbleCornerRadius = s.bubbleCornerRadius,
            messageActionStyle = s.messageActionStyle.name,
            avatarGrouping = s.avatarGrouping.name,
            allowBackDuringSession = s.allowBackDuringSession,
            sessionTimestampEnabled = s.sessionTimestampEnabled,
            keepScreenOnDuringSession = s.keepScreenOnDuringSession,
            storageBrowsingEnabled = s.storageBrowsingEnabled,
            albumBrowsingEnabled = s.albumBrowsingEnabled,
            showHiddenFiles = s.showHiddenFiles,
            leadingShape = s.leadingShape.id,
            leadingColorMode = s.leadingColorMode.name,
            groupMode = s.groupMode.name,
            homeSort = s.homeSort.format(),
            favoritesSort = s.favoritesSort.format(),
            filesSort = s.filesSort.format(),
            storageSort = s.storageSort.format(),
            animationSpeed = s.animationSpeed.name,
            autoCheckUpdate = s.autoCheckUpdate,
        )
    }

    suspend fun importBackup(backup: SettingsExport) = ds.edit { prefs ->
        backup.themeMode?.enumNameOrNull<ThemeMode>()?.let { prefs[Keys.themeMode] = it }
        backup.presetTheme?.enumNameOrNull<PresetTheme>()?.let { prefs[Keys.preset] = it }
        backup.customThemeSeedArgb?.let {
            prefs[Keys.customThemeSeed] = normalizeThemeSeedArgb(it)
        }
        backup.contrastLevel?.enumNameOrNull<ContrastLevel>()?.let { prefs[Keys.contrast] = it }
        backup.darkMode?.enumNameOrNull<DarkMode>()?.let { prefs[Keys.darkMode] = it }
        backup.amoled?.let { prefs[Keys.amoled] = it }
        backup.phoneAvatarId?.let { prefs[Keys.phoneAvatar] = it }
        backup.phoneAvatarKey?.let { prefs[Keys.phoneAvatarKey] = it }
        backup.browserAvatarKey?.let { prefs[Keys.browserAvatarKey] = it }
        backup.deviceName?.let { value ->
            val normalized = normalizeDeviceName(value)
            if (normalized.isEmpty()) prefs.remove(Keys.deviceName)
            else prefs[Keys.deviceName] = normalized
        }
        backup.recallEnabled?.let { prefs[Keys.recallBeta] = it }
        backup.allowPeerRecall?.let { prefs[Keys.allowPeerRecall] = it }
        backup.favoriteEnabled?.let { prefs[Keys.favoriteBeta] = it }
        backup.favoriteBrowsingEnabled?.let { prefs[Keys.favoriteBrowsing] = it }
        backup.requirePin?.let { prefs[Keys.requirePin] = it }
        backup.historyRetainLimit?.let { prefs[Keys.retainLimit] = it.coerceAtLeast(-1) }
        backup.thumbnailCacheLimitMb?.let {
            prefs[Keys.thumbnailCacheLimitMb] = normalizeThumbnailCacheLimitMb(it)
        }
        backup.bubbleCornerRadius?.let {
            prefs[Keys.bubbleCorner] = it.coerceIn(BUBBLE_CORNER_MIN, BUBBLE_CORNER_MAX)
        }
        backup.messageActionStyle?.enumNameOrNull<MessageActionStyle>()
            ?.let { prefs[Keys.msgActionStyle] = it }
        backup.avatarGrouping?.enumNameOrNull<AvatarGroupingMode>()
            ?.let { prefs[Keys.avatarGrouping] = it }
        backup.allowBackDuringSession?.let { prefs[Keys.allowBackDuringSession] = it }
        backup.sessionTimestampEnabled?.let { prefs[Keys.sessionTimestampEnabled] = it }
        backup.keepScreenOnDuringSession?.let { prefs[Keys.keepScreenOnDuringSession] = it }
        backup.storageBrowsingEnabled?.let { prefs[Keys.storageBrowsingEnabled] = it }
        backup.albumBrowsingEnabled?.let { prefs[Keys.albumBrowsingEnabled] = it }
        backup.showHiddenFiles?.let { prefs[Keys.showHiddenFiles] = it }
        backup.leadingShape
            ?.let { raw -> LeadingShape.entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } }
            ?.let { prefs[Keys.leadingShape] = it.id }
        backup.leadingColorMode?.enumNameOrNull<LeadingColorMode>()
            ?.let { prefs[Keys.leadingColorMode] = it }
        // 旧备份只有 sortMode（键名，无方向）；新备份有 homeSort（键+方向）。
        // 新的优先，旧的兜底，两者都没有就保持默认。
        backup.sortMode?.let { raw ->
            runCatching { SortKey.valueOf(raw) }.getOrNull()
                ?.let { prefs[Keys.homeSort] = SortSpec.natural(it).format() }
        }
        backup.homeSort?.let { SortSpec.parse(it) }?.let { prefs[Keys.homeSort] = it.format() }
        backup.favoritesSort?.let { SortSpec.parse(it) }
            ?.let { prefs[Keys.favoritesSort] = it.format() }
        backup.filesSort?.let { SortSpec.parse(it) }?.let { prefs[Keys.filesSort] = it.format() }
        backup.storageSort?.let { SortSpec.parse(it) }
            ?.let { prefs[Keys.storageSort] = it.format() }
        backup.groupMode?.enumNameOrNull<GroupMode>()?.let { prefs[Keys.groupMode] = it }
        backup.animationSpeed?.enumNameOrNull<AnimationSpeed>()
            ?.let { prefs[Keys.animationSpeed] = it }
        backup.autoCheckUpdate?.let { prefs[Keys.autoCheckUpdate] = it }
        when (backup.backgroundMode) {
            "DEFAULT", "BLANK" -> {
                prefs[Keys.bgMode] = backup.backgroundMode
                prefs.remove(Keys.bgValue)
            }
            "SOLID" -> backup.backgroundValue?.toLongOrNull()?.let { value ->
                prefs[Keys.bgMode] = "SOLID"
                prefs[Keys.bgValue] = value.toString()
            }
        }
    }

    private fun decodeBackground(mode: String?, value: String?): BackgroundSetting = when (mode) {
        "BLANK" -> BackgroundSetting.Blank
        "SOLID" -> value?.toLongOrNull()?.let { BackgroundSetting.Solid(it) } ?: BackgroundSetting.Default
        // "GRADIENT"（v1.5.x 历史值）→ 回退 Default，不崩
        else -> BackgroundSetting.Default
    }

    private fun decodeRecentFavoriteIds(raw: String?): List<Long> =
        raw.orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull()?.takeIf { id -> id > 0L } }
            .distinct()
            .take(RECENT_FAVORITE_LIMIT)

    private fun normalizeDeviceName(value: String?): String = value
        .orEmpty()
        .trim()
        .take(20)
        .takeUnless { it == LEGACY_DEFAULT_DEVICE_NAME }
        .orEmpty()

    private inline fun <reified T : Enum<T>> String.enumNameOrNull(): String? =
        runCatching { enumValueOf<T>(this).name }.getOrNull()

    private companion object {
        const val RECENT_FAVORITE_LIMIT = 5
        const val LEGACY_DEFAULT_DEVICE_NAME = "我的手机"
    }
}

/**
 * Resolves the peer gate while preserving the v1.20 behaviour for existing installations.
 * Once the new key has been written it is authoritative over the legacy app-side flag.
 */
fun resolveFavoriteBrowsing(newKey: Boolean?, legacyBeta: Boolean?): Boolean =
    newKey ?: (legacyBeta ?: false)
