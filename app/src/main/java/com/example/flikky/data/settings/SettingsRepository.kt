package com.example.flikky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.example.flikky.export.SettingsExport

class SettingsRepository(private val ds: DataStore<Preferences>) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val preset = stringPreferencesKey("preset_theme")
        val contrast = stringPreferencesKey("contrast_level")
        val darkMode = stringPreferencesKey("dark_mode")
        val amoled = booleanPreferencesKey("amoled")
        val phoneAvatar = intPreferencesKey("phone_avatar")
        val phoneAvatarKey = stringPreferencesKey("phone_avatar_key")
        val bgMode = stringPreferencesKey("bg_mode")
        val bgValue = stringPreferencesKey("bg_value")
        val deviceName = stringPreferencesKey("device_name")
        val recallBeta = booleanPreferencesKey("recall_beta")
        val favoriteBeta = booleanPreferencesKey("favorite_beta")
        val requirePin = booleanPreferencesKey("require_pin")
        val retainLimit = intPreferencesKey("retain_limit")
        val bubbleCorner = intPreferencesKey("bubble_corner")
        val msgActionStyle = stringPreferencesKey("msg_action_style")
        val avatarGrouping = stringPreferencesKey("avatar_grouping")
        val allowBackDuringSession = booleanPreferencesKey("allow_back_during_session")
        val sortMode = stringPreferencesKey("sort_mode")
        val groupMode = stringPreferencesKey("group_mode")
        val animationSpeed = stringPreferencesKey("animation_speed")
        val activeGroupId = longPreferencesKey("active_group_id")
        val activeFavoriteGroupId = longPreferencesKey("active_favorite_group_id")
        val recentFavoriteIds = stringPreferencesKey("recent_favorite_ids")
    }

    val settings: Flow<FlikkySettings> = ds.data.map { p ->
        FlikkySettings(
            themeMode = p[Keys.themeMode]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.DYNAMIC,
            // 旧版本可能存了已移除的预设名（CORAL/MUSHROOM/TEAL/MIST）——valueOf 会抛，
            // runCatching 兜底回落到默认主题 DANSHU_RED（淡曙红，与旧 CORAL 同为暖红色系）。
            presetTheme = p[Keys.preset]
                ?.let { runCatching { PresetTheme.valueOf(it) }.getOrNull() }
                ?: PresetTheme.DANSHU_RED,
            contrastLevel = p[Keys.contrast]
                ?.let { runCatching { ContrastLevel.valueOf(it) }.getOrNull() }
                ?: ContrastLevel.SYSTEM,
            darkMode = p[Keys.darkMode]?.let { DarkMode.valueOf(it) } ?: DarkMode.SYSTEM,
            amoled = p[Keys.amoled] ?: false,
            phoneAvatarId = p[Keys.phoneAvatar] ?: 0,
            phoneAvatarKey = p[Keys.phoneAvatarKey] ?: "icon:smartphone",
            background = decodeBackground(p[Keys.bgMode], p[Keys.bgValue]),
            deviceName = normalizeDeviceName(p[Keys.deviceName]),
            recallBetaEnabled = p[Keys.recallBeta] ?: false,
            favoriteBetaEnabled = p[Keys.favoriteBeta] ?: false,
            requirePin = p[Keys.requirePin] ?: true,
            historyRetainLimit = (p[Keys.retainLimit] ?: 20).coerceAtLeast(-1),
            bubbleCornerRadius = (p[Keys.bubbleCorner] ?: BUBBLE_CORNER_DEFAULT)
                .coerceIn(BUBBLE_CORNER_MIN, BUBBLE_CORNER_MAX),
            messageActionStyle = p[Keys.msgActionStyle]
                ?.let { runCatching { MessageActionStyle.valueOf(it) }.getOrNull() }
                ?: MessageActionStyle.FLOATING,
            avatarGrouping = p[Keys.avatarGrouping]
                ?.let { runCatching { AvatarGroupingMode.valueOf(it) }.getOrNull() }
                ?: AvatarGroupingMode.FIRST,
            allowBackDuringSession = p[Keys.allowBackDuringSession] ?: false,
            sortMode = p[Keys.sortMode]
                ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
                ?: SortMode.TIME,
            groupMode = p[Keys.groupMode]
                ?.let { runCatching { GroupMode.valueOf(it) }.getOrNull() }
                ?: GroupMode.NONE,
            animationSpeed = p[Keys.animationSpeed]
                ?.let { runCatching { AnimationSpeed.valueOf(it) }.getOrNull() }
                ?: AnimationSpeed.STANDARD,
            activeGroupId = p[Keys.activeGroupId]?.takeIf { it > 0L },
            activeFavoriteGroupId = p[Keys.activeFavoriteGroupId]?.takeIf { it > 0L },
            recentFavoriteIds = decodeRecentFavoriteIds(p[Keys.recentFavoriteIds]),
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = ds.edit { it[Keys.themeMode] = v.name }
    suspend fun setPresetTheme(v: PresetTheme) = ds.edit { it[Keys.preset] = v.name }
    suspend fun setContrastLevel(v: ContrastLevel) = ds.edit { it[Keys.contrast] = v.name }
    suspend fun setDarkMode(v: DarkMode) = ds.edit { it[Keys.darkMode] = v.name }
    suspend fun setAmoled(v: Boolean) = ds.edit { it[Keys.amoled] = v }
    suspend fun setPhoneAvatar(v: Int) = ds.edit { it[Keys.phoneAvatar] = v }
    suspend fun setPhoneAvatarKey(v: String) = ds.edit { it[Keys.phoneAvatarKey] = v }
    suspend fun setDeviceName(v: String) = ds.edit { prefs ->
        val normalized = normalizeDeviceName(v)
        if (normalized.isEmpty()) prefs.remove(Keys.deviceName)
        else prefs[Keys.deviceName] = normalized
    }
    suspend fun setRecallBeta(v: Boolean) = ds.edit { it[Keys.recallBeta] = v }
    suspend fun setFavoriteBeta(v: Boolean) = ds.edit { it[Keys.favoriteBeta] = v }
    suspend fun setRequirePin(v: Boolean) = ds.edit { it[Keys.requirePin] = v }
    suspend fun setHistoryRetainLimit(v: Int) = ds.edit { it[Keys.retainLimit] = v.coerceAtLeast(-1) }
    suspend fun setBubbleCornerRadius(v: Int) = ds.edit {
        it[Keys.bubbleCorner] = v.coerceIn(BUBBLE_CORNER_MIN, BUBBLE_CORNER_MAX)
    }
    suspend fun setMessageActionStyle(v: MessageActionStyle) = ds.edit { it[Keys.msgActionStyle] = v.name }
    suspend fun setAvatarGrouping(v: AvatarGroupingMode) = ds.edit { it[Keys.avatarGrouping] = v.name }
    suspend fun setAllowBackDuringSession(v: Boolean) = ds.edit { it[Keys.allowBackDuringSession] = v }
    suspend fun setSortMode(v: SortMode) = ds.edit { it[Keys.sortMode] = v.name }
    suspend fun setGroupMode(v: GroupMode) = ds.edit { it[Keys.groupMode] = v.name }
    suspend fun setAnimationSpeed(v: AnimationSpeed) = ds.edit { it[Keys.animationSpeed] = v.name }
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
            contrastLevel = s.contrastLevel.name,
            darkMode = s.darkMode.name,
            amoled = s.amoled,
            phoneAvatarId = s.phoneAvatarId,
            phoneAvatarKey = s.phoneAvatarKey,
            backgroundMode = backgroundMode,
            backgroundValue = backgroundValue,
            deviceName = s.deviceName,
            recallBetaEnabled = s.recallBetaEnabled,
            favoriteBetaEnabled = s.favoriteBetaEnabled,
            requirePin = s.requirePin,
            historyRetainLimit = s.historyRetainLimit,
            bubbleCornerRadius = s.bubbleCornerRadius,
            messageActionStyle = s.messageActionStyle.name,
            avatarGrouping = s.avatarGrouping.name,
            allowBackDuringSession = s.allowBackDuringSession,
            sortMode = s.sortMode.name,
            groupMode = s.groupMode.name,
            animationSpeed = s.animationSpeed.name,
        )
    }

    suspend fun importBackup(backup: SettingsExport) = ds.edit { prefs ->
        backup.themeMode?.enumNameOrNull<ThemeMode>()?.let { prefs[Keys.themeMode] = it }
        backup.presetTheme?.enumNameOrNull<PresetTheme>()?.let { prefs[Keys.preset] = it }
        backup.contrastLevel?.enumNameOrNull<ContrastLevel>()?.let { prefs[Keys.contrast] = it }
        backup.darkMode?.enumNameOrNull<DarkMode>()?.let { prefs[Keys.darkMode] = it }
        backup.amoled?.let { prefs[Keys.amoled] = it }
        backup.phoneAvatarId?.let { prefs[Keys.phoneAvatar] = it }
        backup.phoneAvatarKey?.let { prefs[Keys.phoneAvatarKey] = it }
        backup.deviceName?.let { value ->
            val normalized = normalizeDeviceName(value)
            if (normalized.isEmpty()) prefs.remove(Keys.deviceName)
            else prefs[Keys.deviceName] = normalized
        }
        backup.recallBetaEnabled?.let { prefs[Keys.recallBeta] = it }
        backup.favoriteBetaEnabled?.let { prefs[Keys.favoriteBeta] = it }
        backup.requirePin?.let { prefs[Keys.requirePin] = it }
        backup.historyRetainLimit?.let { prefs[Keys.retainLimit] = it.coerceAtLeast(-1) }
        backup.bubbleCornerRadius?.let {
            prefs[Keys.bubbleCorner] = it.coerceIn(BUBBLE_CORNER_MIN, BUBBLE_CORNER_MAX)
        }
        backup.messageActionStyle?.enumNameOrNull<MessageActionStyle>()
            ?.let { prefs[Keys.msgActionStyle] = it }
        backup.avatarGrouping?.enumNameOrNull<AvatarGroupingMode>()
            ?.let { prefs[Keys.avatarGrouping] = it }
        backup.allowBackDuringSession?.let { prefs[Keys.allowBackDuringSession] = it }
        backup.sortMode?.enumNameOrNull<SortMode>()?.let { prefs[Keys.sortMode] = it }
        backup.groupMode?.enumNameOrNull<GroupMode>()?.let { prefs[Keys.groupMode] = it }
        backup.animationSpeed?.enumNameOrNull<AnimationSpeed>()
            ?.let { prefs[Keys.animationSpeed] = it }
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
