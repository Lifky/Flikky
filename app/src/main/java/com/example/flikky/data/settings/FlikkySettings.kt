package com.example.flikky.data.settings

enum class ThemeMode { DYNAMIC, PRESET }

/**
 * 8 个命名预设主题。色值来自用户自定义的 Material Theme Builder 导出
 * （ui/theme/scheme 包下的 Scheme 对象，逐字未改）。枚举常量名用英文，[label] 是中文展示名。
 */
enum class PresetTheme(val label: String) {
    DANSHU_RED("淡曙红"),
    DANZI_RED("丹紫红"),
    CHENGPI_YELLOW("橙皮黄"),
    QIUKUI_YELLOW("秋葵黄"),
    ANAN_BLUE("安安蓝"),
    ZHUMU_GRAY("珠母灰"),
    YINGWU_GREEN("鹦鹉绿"),
    JIEHUA_PURPLE("芥花紫"),
}

/**
 * 对比度档。[SYSTEM] 跟随系统无障碍对比度（API34+ `UiModeManager.getContrast()`，低版本回落
 * [STANDARD]）；其余为手动锁定。每个 [PresetTheme] 都备有标准/中/高三套 MD3 role。
 */
enum class ContrastLevel(val label: String) {
    SYSTEM("跟随系统"),
    STANDARD("标准"),
    MEDIUM("中"),
    HIGH("高"),
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
    val presetTheme: PresetTheme = PresetTheme.DANSHU_RED,
    val contrastLevel: ContrastLevel = ContrastLevel.SYSTEM,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val amoled: Boolean = false,
    val phoneAvatarId: Int = 0,
    val background: BackgroundSetting = BackgroundSetting.Default,
    val deviceName: String = "我的手机",
    val recallBetaEnabled: Boolean = false,
    val favoriteBetaEnabled: Boolean = false,
    val historyRetainLimit: Int = 20,   // 0=不保存, -1=无限制
    val bubbleCornerRadius: Int = BUBBLE_CORNER_DEFAULT,   // dp，钳制 8..28
    val messageActionStyle: MessageActionStyle = MessageActionStyle.FLOATING,
    val avatarGrouping: AvatarGroupingMode = AvatarGroupingMode.FIRST,
    /** 允许会话进行中按返回键退出到主页（默认 false：拦截返回，保护会话稳定）。 */
    val allowBackDuringSession: Boolean = false,
    val sortMode: SortMode = SortMode.TIME,
    val groupMode: GroupMode = GroupMode.NONE,
    val animationSpeed: AnimationSpeed = AnimationSpeed.STANDARD,
    val activeGroupId: Long? = null,
    val activeFavoriteGroupId: Long? = null,
    val recentFavoriteIds: List<Long> = emptyList(),
)
