package com.example.flikky.data.settings

enum class ThemeMode { DYNAMIC, PRESET, CUSTOM }

/**
 * 8 个命名预设主题。色值来自用户自定义的 Material Theme Builder 导出
 * （ui/theme/scheme 包下的 Scheme 对象，逐字未改）。枚举常量名用英文，展示名由 UI 资源提供。
 * [seedHex] 是该主题的身份种子色（取 light/标准 的 primary），推给浏览器端 `mdui.setColorScheme`
 * 让双端配色对齐——MDC 与 mdui 同用 Material Color Utilities，同一 seed 出同一色相。
 */
enum class PresetTheme(val seedHex: String) {
    DANSHU_RED("#8F4A4C"),
    DANZI_RED("#8B4A63"),
    CHENGPI_YELLOW("#825513"),
    QIUKUI_YELLOW("#6D5E0E"),
    ANAN_BLUE("#33618D"),
    ZHUMU_GRAY("#8E4D31"),
    YINGWU_GREEN("#466730"),
    JIEHUA_PURPLE("#844C72"),
}

/**
 * 对比度档。[SYSTEM] 跟随系统无障碍对比度（API34+ `UiModeManager.getContrast()`，低版本回落
 * [STANDARD]）；其余为手动锁定。每个 [PresetTheme] 都备有标准/中/高三套 MD3 role。
 */
enum class ContrastLevel {
    SYSTEM,
    STANDARD,
    MEDIUM,
    HIGH,
}

enum class DarkMode { SYSTEM, LIGHT, DARK }
enum class SortMode { TIME, NAME }
enum class GroupMode { NONE, STATUS, DATE }

/**
 * 全局动画速度档。[multiplier] 是 duration 倍率：`>1` 更慢、`<1` 更快、`0` 关闭（reduce-motion）。
 * 与系统 animatorDurationScale 合成见 [com.example.flikky.ui.theme.effectiveMotionScale]。
 */
enum class AnimationSpeed(val multiplier: Float) {
    OFF(0f),
    SLOW(1.5f),
    STANDARD(1.0f),
    FAST(0.7f),
}

/** 消息操作交互样式：FLOATING=单击气泡弹底部悬浮工具栏；INLINE=气泡旁常驻按钮（旧行为）。 */
enum class MessageActionStyle { FLOATING, INLINE }

/** 头像显示模式：FIRST=同来源组内首条；LAST=同来源组内末条；EACH=每条都显示。 */
enum class AvatarGroupingMode { FIRST, LAST, EACH }

sealed class BackgroundSetting {
    object Default : BackgroundSetting()           // 显示连接状态 + 对端
    object Blank : BackgroundSetting()             // 空白
    data class Solid(val argb: Long) : BackgroundSetting()
    // v1.6.0：移除 Gradient（效果不佳、不符 MD3 极浅规范）。历史存的 GRADIENT 解码回退 Default。
}

/** 设备名长度上限。设置页与会话页的快捷设置共用同一个上限，别各写一个 20。 */
const val DEVICE_NAME_MAX = 20

const val BUBBLE_CORNER_MIN = 8
const val BUBBLE_CORNER_MAX = 28
const val BUBBLE_CORNER_DEFAULT = 10
const val CUSTOM_THEME_SEED_DEFAULT = 0xFF33618DL

data class FlikkySettings(
    val themeMode: ThemeMode = ThemeMode.PRESET,
    val presetTheme: PresetTheme = PresetTheme.ANAN_BLUE,
    val customThemeSeedArgb: Long = CUSTOM_THEME_SEED_DEFAULT,
    val contrastLevel: ContrastLevel = ContrastLevel.SYSTEM,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val amoled: Boolean = false,
    val phoneAvatarId: Int = 0,
    val phoneAvatarKey: String = "icon:smartphone",
    val browserAvatarKey: String = "icon:desktop_windows",
    val background: BackgroundSetting = BackgroundSetting.Default,
    val deviceName: String = "",
    val recallBetaEnabled: Boolean = true,
    val allowPeerRecall: Boolean = true,
    val favoriteBetaEnabled: Boolean = false,
    val requirePin: Boolean = true,
    val historyRetainLimit: Int = 20,   // 0=不保存, -1=无限制
    val bubbleCornerRadius: Int = BUBBLE_CORNER_DEFAULT,   // dp，钳制 8..28
    val messageActionStyle: MessageActionStyle = MessageActionStyle.INLINE,
    val avatarGrouping: AvatarGroupingMode = AvatarGroupingMode.EACH,
    /** 允许会话进行中按返回键退出到主页。 */
    val allowBackDuringSession: Boolean = true,
    /** 会话中显示时间戳分隔条（两端同步，浏览器经 settings_changed 跟随）。 */
    val sessionTimestampEnabled: Boolean = false,
    /** 服务运行中停留在会话页时保持屏幕常亮。 */
    val keepScreenOnDuringSession: Boolean = false,
    val sortMode: SortMode = SortMode.TIME,
    val groupMode: GroupMode = GroupMode.NONE,
    val animationSpeed: AnimationSpeed = AnimationSpeed.STANDARD,
    /** 启动时自动检查更新，默认关闭。 */
    val autoCheckUpdate: Boolean = false,
    val activeGroupId: Long? = null,
    val activeFavoriteGroupId: Long? = null,
    val recentFavoriteIds: List<Long> = emptyList(),
)
