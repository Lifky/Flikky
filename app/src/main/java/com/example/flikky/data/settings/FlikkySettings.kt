package com.example.flikky.data.settings

enum class ThemeMode { DYNAMIC, PRESET }
enum class PresetTheme { CORAL, MUSHROOM, TEAL, MIST }
enum class DarkMode { SYSTEM, LIGHT, DARK }

/** 消息操作交互样式：FLOATING=长按弹底部悬浮工具栏；INLINE=气泡旁常驻按钮（旧行为）。 */
enum class MessageActionStyle { FLOATING, INLINE }

/** 头像显示模式：FIRST=同来源组内首条；LAST=同来源组内末条；EACH=每条都显示。 */
enum class AvatarGroupingMode { FIRST, LAST, EACH }

sealed class BackgroundSetting {
    object Default : BackgroundSetting()           // 显示连接状态 + 对端
    object Blank : BackgroundSetting()             // 空白
    data class Solid(val argb: Long) : BackgroundSetting()
    // v1.6.0：移除 Gradient（效果不佳、不符 MD3 极浅规范）。历史存的 GRADIENT 解码回退 Default。
}

const val BUBBLE_CORNER_MIN = 8
const val BUBBLE_CORNER_MAX = 28
const val BUBBLE_CORNER_DEFAULT = 18

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
    val bubbleCornerRadius: Int = BUBBLE_CORNER_DEFAULT,   // dp，钳制 8..28
    val messageActionStyle: MessageActionStyle = MessageActionStyle.FLOATING,
    val avatarGrouping: AvatarGroupingMode = AvatarGroupingMode.FIRST,
)
