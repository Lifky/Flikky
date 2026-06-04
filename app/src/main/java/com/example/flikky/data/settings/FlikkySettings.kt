package com.example.flikky.data.settings

enum class ThemeMode { DYNAMIC, PRESET }
enum class PresetTheme { CORAL, MUSHROOM, TEAL, MIST }
enum class DarkMode { SYSTEM, LIGHT, DARK }

sealed class BackgroundSetting {
    object Default : BackgroundSetting()           // 显示连接状态 + 对端
    object Blank : BackgroundSetting()             // 空白
    data class Solid(val argb: Long) : BackgroundSetting()
    data class Gradient(val name: String) : BackgroundSetting()  // "sunset"/"forest"/"ocean"
}

data class FlikkySettings(
    val themeMode: ThemeMode = ThemeMode.DYNAMIC,
    val presetTheme: PresetTheme = PresetTheme.CORAL,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val amoled: Boolean = false,
    val phoneAvatarId: Int = 0,
    val background: BackgroundSetting = BackgroundSetting.Default,
    val deviceName: String = "我的手机",
    val recallBetaEnabled: Boolean = false,
    val historyRetainLimit: Int = 20,   // 0=不保存, -1=无限制
)
