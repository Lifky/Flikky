package com.example.flikky.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.ui.theme.scheme.AnanBlueScheme
import com.example.flikky.ui.theme.scheme.ChengpiYellowScheme
import com.example.flikky.ui.theme.scheme.DanshuRedScheme
import com.example.flikky.ui.theme.scheme.DanziRedScheme
import com.example.flikky.ui.theme.scheme.JiehuaPurpleScheme
import com.example.flikky.ui.theme.scheme.QiukuiYellowScheme
import com.example.flikky.ui.theme.scheme.ThemeScheme
import com.example.flikky.ui.theme.scheme.YingwuGreenScheme
import com.example.flikky.ui.theme.scheme.ZhumuGrayScheme

/**
 * 解析后的对比度档（已去除用户档里的 SYSTEM——SYSTEM 由 [FlikkyTheme] 按系统
 * `UiModeManager.getContrast()` 解析成这三档之一）。8 套主题各自带「标准/中/高」全 role。
 */
enum class ResolvedContrast { STANDARD, MEDIUM, HIGH }

/** 8 个命名预设 → 该主题的全部 [ThemeScheme] 变体集合（色值见 ui/theme/scheme 包下的 Scheme 对象）。 */
private fun PresetTheme.schemeSet(): ThemeScheme = when (this) {
    PresetTheme.DANSHU_RED     -> DanshuRedScheme
    PresetTheme.DANZI_RED      -> DanziRedScheme
    PresetTheme.CHENGPI_YELLOW -> ChengpiYellowScheme
    PresetTheme.QIUKUI_YELLOW  -> QiukuiYellowScheme
    PresetTheme.ANAN_BLUE      -> AnanBlueScheme
    PresetTheme.ZHUMU_GRAY     -> ZhumuGrayScheme
    PresetTheme.YINGWU_GREEN   -> YingwuGreenScheme
    PresetTheme.JIEHUA_PURPLE  -> JiehuaPurpleScheme
}

/**
 * 8 个命名预设 × 深浅 × 对比度 → MD3 [ColorScheme]。
 * 色值来自 Material Theme Builder 导出（逐字搬入 ui/theme/scheme/，未自造）。
 */
fun presetScheme(
    preset: PresetTheme,
    dark: Boolean,
    contrast: ResolvedContrast = ResolvedContrast.STANDARD,
): ColorScheme {
    val set = preset.schemeSet()
    return when (contrast) {
        ResolvedContrast.STANDARD -> if (dark) set.dark else set.light
        ResolvedContrast.MEDIUM   -> if (dark) set.darkMedium else set.lightMedium
        ResolvedContrast.HIGH     -> if (dark) set.darkHigh else set.lightHigh
    }
}

// ─── AMOLED override ─────────────────────────────────────────────────────────
// Pure-black background/surface; surfaceContainer* stay as dark greys for layering.
fun amoledOverride(scheme: ColorScheme): ColorScheme = scheme.copy(
    background = Color.Black,
    surface = Color.Black,
)

// ─── Semantic color extensions ────────────────────────────────────────────────
val ColorScheme.connected: Color
    @Composable get() = lerp(Color(0xFF2E7D32), MaterialTheme.colorScheme.primary, 0.2f) // success green blended 20% toward primary

val ColorScheme.disconnected: Color
    @Composable get() = onSurfaceVariant   // muted, intentionally de-emphasized
