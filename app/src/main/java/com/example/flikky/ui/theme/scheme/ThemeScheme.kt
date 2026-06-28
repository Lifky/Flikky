package com.example.flikky.ui.theme.scheme

import androidx.compose.material3.ColorScheme

/**
 * 一套命名主题的全部 MD3 [ColorScheme] 变体：light/dark × 标准/中/高对比度。
 * 由 `scripts/gen_schemes.mjs` 从 Material Theme Builder 导出生成的 `*Scheme` 对象实现。
 * 选择逻辑（preset/深浅/对比度 → ColorScheme）见 `ui/theme/Color.kt`。
 */
internal interface ThemeScheme {
    val light: ColorScheme
    val dark: ColorScheme
    val lightMedium: ColorScheme
    val lightHigh: ColorScheme
    val darkMedium: ColorScheme
    val darkHigh: ColorScheme
}
