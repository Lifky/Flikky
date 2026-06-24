package com.example.flikky.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val ds: DataStore<Preferences>) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val preset = stringPreferencesKey("preset_theme")
        val darkMode = stringPreferencesKey("dark_mode")
        val amoled = booleanPreferencesKey("amoled")
        val phoneAvatar = intPreferencesKey("phone_avatar")
        val bgMode = stringPreferencesKey("bg_mode")
        val bgValue = stringPreferencesKey("bg_value")
        val deviceName = stringPreferencesKey("device_name")
        val recallBeta = booleanPreferencesKey("recall_beta")
        val retainLimit = intPreferencesKey("retain_limit")
        val bubbleCorner = intPreferencesKey("bubble_corner")
        val msgActionStyle = stringPreferencesKey("msg_action_style")
        val avatarGrouping = stringPreferencesKey("avatar_grouping")
        val allowBackDuringSession = booleanPreferencesKey("allow_back_during_session")
        val sortMode = stringPreferencesKey("sort_mode")
        val groupMode = stringPreferencesKey("group_mode")
        val activeGroupId = longPreferencesKey("active_group_id")
        val activeFavoriteGroupId = longPreferencesKey("active_favorite_group_id")
    }

    val settings: Flow<FlikkySettings> = ds.data.map { p ->
        FlikkySettings(
            themeMode = p[Keys.themeMode]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.DYNAMIC,
            presetTheme = p[Keys.preset]?.let { PresetTheme.valueOf(it) } ?: PresetTheme.CORAL,
            darkMode = p[Keys.darkMode]?.let { DarkMode.valueOf(it) } ?: DarkMode.SYSTEM,
            amoled = p[Keys.amoled] ?: false,
            phoneAvatarId = p[Keys.phoneAvatar] ?: 0,
            background = decodeBackground(p[Keys.bgMode], p[Keys.bgValue]),
            deviceName = p[Keys.deviceName] ?: "我的手机",
            recallBetaEnabled = p[Keys.recallBeta] ?: false,
            historyRetainLimit = p[Keys.retainLimit] ?: 20,
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
            activeGroupId = p[Keys.activeGroupId]?.takeIf { it > 0L },
            activeFavoriteGroupId = p[Keys.activeFavoriteGroupId]?.takeIf { it > 0L },
        )
    }

    suspend fun setThemeMode(v: ThemeMode) = ds.edit { it[Keys.themeMode] = v.name }
    suspend fun setPresetTheme(v: PresetTheme) = ds.edit { it[Keys.preset] = v.name }
    suspend fun setDarkMode(v: DarkMode) = ds.edit { it[Keys.darkMode] = v.name }
    suspend fun setAmoled(v: Boolean) = ds.edit { it[Keys.amoled] = v }
    suspend fun setPhoneAvatar(v: Int) = ds.edit { it[Keys.phoneAvatar] = v }
    suspend fun setDeviceName(v: String) = ds.edit { it[Keys.deviceName] = v.trim().ifEmpty { "我的手机" }.take(20) }
    suspend fun setRecallBeta(v: Boolean) = ds.edit { it[Keys.recallBeta] = v }
    suspend fun setHistoryRetainLimit(v: Int) = ds.edit { it[Keys.retainLimit] = v }
    suspend fun setBubbleCornerRadius(v: Int) = ds.edit {
        it[Keys.bubbleCorner] = v.coerceIn(BUBBLE_CORNER_MIN, BUBBLE_CORNER_MAX)
    }
    suspend fun setMessageActionStyle(v: MessageActionStyle) = ds.edit { it[Keys.msgActionStyle] = v.name }
    suspend fun setAvatarGrouping(v: AvatarGroupingMode) = ds.edit { it[Keys.avatarGrouping] = v.name }
    suspend fun setAllowBackDuringSession(v: Boolean) = ds.edit { it[Keys.allowBackDuringSession] = v }
    suspend fun setSortMode(v: SortMode) = ds.edit { it[Keys.sortMode] = v.name }
    suspend fun setGroupMode(v: GroupMode) = ds.edit { it[Keys.groupMode] = v.name }
    suspend fun setActiveGroup(id: Long?) = ds.edit { prefs ->
        val valid = id?.takeIf { it > 0L }
        if (valid != null) prefs[Keys.activeGroupId] = valid else prefs.remove(Keys.activeGroupId)
    }
    suspend fun setActiveFavoriteGroup(id: Long?) = ds.edit { prefs ->
        val valid = id?.takeIf { it > 0L }
        if (valid != null) prefs[Keys.activeFavoriteGroupId] = valid else prefs.remove(Keys.activeFavoriteGroupId)
    }
    suspend fun setBackground(v: BackgroundSetting) = ds.edit {
        when (v) {
            BackgroundSetting.Default -> { it[Keys.bgMode] = "DEFAULT"; it.remove(Keys.bgValue) }
            BackgroundSetting.Blank -> { it[Keys.bgMode] = "BLANK"; it.remove(Keys.bgValue) }
            is BackgroundSetting.Solid -> { it[Keys.bgMode] = "SOLID"; it[Keys.bgValue] = v.argb.toString() }
        }
    }

    private fun decodeBackground(mode: String?, value: String?): BackgroundSetting = when (mode) {
        "BLANK" -> BackgroundSetting.Blank
        "SOLID" -> value?.toLongOrNull()?.let { BackgroundSetting.Solid(it) } ?: BackgroundSetting.Default
        // "GRADIENT"（v1.5.x 历史值）→ 回退 Default，不崩
        else -> BackgroundSetting.Default
    }
}
