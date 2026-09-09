package com.example.flikky.data.settings

import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import com.example.flikky.util.LeadingColorMode
import com.example.flikky.util.LeadingShape

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
    /**
     * 允许已认证的浏览器浏览本机共享存储。默认关闭。
     *
     * 只门控**对端**：App 端自己的文件 tab 不受它约束（只受系统权限约束）。
     * 关闭时浏览器不渲染文件目的地，且 storage 接口一律 404。
     * （注意：KDoc 里不要写 "/api/storage" 加星号——Kotlin 块注释可嵌套，那个 "/" 加 "*"
     * 会开一个内层注释，把后面整个文件吃掉。踩过一次。）
     */
    val storageBrowsingEnabled: Boolean = false,

    /**
     * v1.20.0: 浏览存储时是否显示 `.` 开头的隐藏项。
     *
     * 默认关：Android 存储里的隐藏项（`.thumbnails`、`.trashed-*`、`.nomedia`）数量可观，
     * 会把真正想发的文件挤下去。**两端共用这一个值**，副标题的计数也走它——
     * 三者用不同判据就是 2026-09-03 那个「副标题 5 项、进去只有 4 行」的成因。
     */
    val showHiddenFiles: Boolean = false,
    /** 全局文件 leading 形状；默认保持升级前的九边形。 */
    val leadingShape: LeadingShape = LeadingShape.Default,
    /** 全局文件 leading 配色；默认保持升级前的主题单色。 */
    val leadingColorMode: LeadingColorMode = LeadingColorMode.THEME,
    /**
     * 会话列表的分节方式。
     *
     * 默认 `DATE` 而不是 `NONE`：v1.20.0 之前 `HomeViewModel` 写死传 `DATE`，
     * 没有任何用户见过 `NONE`。接线时若保留 `NONE`，升级后所有人的主页会突然
     * 不分节 —— 一次静默的行为回归。
     */
    val groupMode: GroupMode = GroupMode.DATE,
    /** 会话列表的排序。默认等于本版之前写死的顺序。 */
    val homeSort: SortSpec = SortSpec(SortKey.TIME, descending = true),
    /** 收藏页的排序。默认等于 DAO 原本的 `createdAt DESC`。 */
    val favoritesSort: SortSpec = SortSpec(SortKey.TIME, descending = true),
    /** 文件总览页的排序。此前只在内存里，重启即丢。 */
    val filesSort: SortSpec = SortSpec(SortKey.TIME, descending = true),
    /** 文件浏览（会话页文件 tab）的排序。默认「目录优先 + 名称升序」。 */
    val storageSort: SortSpec = SortSpec.NameAsc,
    val animationSpeed: AnimationSpeed = AnimationSpeed.STANDARD,
    /** 启动时自动检查更新，默认关闭。 */
    val autoCheckUpdate: Boolean = false,
    val activeGroupId: Long? = null,
    val activeFavoriteGroupId: Long? = null,
    val recentFavoriteIds: List<Long> = emptyList(),
)
