package com.example.flikky.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.example.flikky.data.settings.PresetTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 对比度回归：8 个命名预设 × {light,dark} × {标准,中,高对比度} 的全部 MD3「on」对
 * 都必须 ≥ 4.5:1（正文最低 AA）。色值来自用户的 MTB 导出，本测试守住「逐字搬入后未破坏可达性」。
 */
class ColorContrastTest {

    private fun lum(c: Color): Double {
        fun linearize(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linearize(c.red) + 0.7152 * linearize(c.green) + 0.0722 * linearize(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = lum(a)
        val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** MD3 设计上必须满足 ≥4.5:1 的前景/背景 role 对（正文文本可读性）。 */
    private val onPairs: List<Pair<String, (ColorScheme) -> Pair<Color, Color>>> = listOf(
        "onPrimary/primary" to { it.onPrimary to it.primary },
        "onPrimaryContainer/primaryContainer" to { it.onPrimaryContainer to it.primaryContainer },
        "onSecondary/secondary" to { it.onSecondary to it.secondary },
        "onSecondaryContainer/secondaryContainer" to { it.onSecondaryContainer to it.secondaryContainer },
        "onTertiary/tertiary" to { it.onTertiary to it.tertiary },
        "onTertiaryContainer/tertiaryContainer" to { it.onTertiaryContainer to it.tertiaryContainer },
        "onError/error" to { it.onError to it.error },
        "onErrorContainer/errorContainer" to { it.onErrorContainer to it.errorContainer },
        "onBackground/background" to { it.onBackground to it.background },
        "onSurface/surface" to { it.onSurface to it.surface },
        "onSurfaceVariant/surfaceVariant" to { it.onSurfaceVariant to it.surfaceVariant },
    )

    @Test
    fun all_presets_meet_wcag_aa_across_dark_and_contrast() {
        val failures = mutableListOf<String>()
        for (preset in PresetTheme.entries) {
            for (dark in listOf(false, true)) {
                for (level in ResolvedContrast.entries) {
                    val scheme = presetScheme(preset, dark, level)
                    for ((label, select) in onPairs) {
                        val (fg, bg) = select(scheme)
                        val r = ratio(fg, bg)
                        if (r < 4.5) {
                            val mode = if (dark) "dark" else "light"
                            failures += "$preset/$mode/$level $label = %.2f".format(r)
                        }
                    }
                }
            }
        }
        assertTrue(
            "WCAG AA (<4.5:1) failures (${failures.size}):\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }
}
