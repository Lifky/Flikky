package com.example.flikky.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.flikky.util.LeadingColorMode
import com.example.flikky.util.LeadingShape
import com.example.flikky.util.LeadingType
import com.example.flikky.util.LeadingVisualCatalog
import com.materialkolor.blend.Blend
import com.materialkolor.dynamicColorScheme
import com.materialkolor.hct.Hct

data class LeadingColorPair(
    val container: Color,
    val onContainer: Color,
)

data class LeadingVisualStyle(
    val shape: LeadingShape = LeadingShape.Default,
    val colors: Map<String, LeadingColorPair> = emptyMap(),
)

val LocalLeadingVisual = compositionLocalOf { LeadingVisualStyle() }

fun resolveLeadingColors(
    mode: LeadingColorMode,
    theme: ColorScheme,
    dark: Boolean,
): Map<String, LeadingColorPair> = LeadingVisualCatalog.types.associate { type ->
    type.id to when (mode) {
        LeadingColorMode.THEME -> LeadingColorPair(
            container = theme.primaryContainer,
            onContainer = theme.onPrimaryContainer,
        )
        LeadingColorMode.HARMONIZED -> harmonizedColors(type, theme, dark)
        LeadingColorMode.FIXED -> generatedColors(type.fixedArgb.toInt(), dark)
    }
}

private fun harmonizedColors(
    type: LeadingType,
    theme: ColorScheme,
    dark: Boolean,
): LeadingColorPair {
    val themePrimary = theme.primary.toArgb()
    val primaryHct = Hct.fromInt(themePrimary)
    val shifted = primaryHct.withHue((primaryHct.hue + type.hueShift) % 360.0).toInt()
    val seed = Blend.harmonize(shifted, themePrimary)
    val generated = dynamicColorScheme(seedColor = Color(seed), isDark = dark)

    return if (type.id == "other") {
        LeadingColorPair(generated.primaryContainer, generated.onPrimaryContainer)
    } else {
        LeadingColorPair(generated.secondaryContainer, generated.onSecondaryContainer)
    }
}

private fun generatedColors(seedArgb: Int, dark: Boolean): LeadingColorPair {
    val generated = dynamicColorScheme(seedColor = Color(seedArgb), isDark = dark)
    return LeadingColorPair(generated.primaryContainer, generated.onPrimaryContainer)
}
