package com.example.flikky.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.util.LeadingColorMode
import com.example.flikky.util.LeadingVisualCatalog
import com.materialkolor.hct.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class LeadingColorsTest {
    private val light = presetScheme(PresetTheme.ANAN_BLUE, dark = false)
    private val dark = presetScheme(PresetTheme.ANAN_BLUE, dark = true)

    @Test
    fun themeModeUsesOneContainerForEveryType() {
        listOf(light to false, dark to true).forEach { (scheme, isDark) ->
            val colors = resolveLeadingColors(LeadingColorMode.THEME, scheme, isDark)
            assertEquals(LeadingVisualCatalog.types.map { it.id }.toSet(), colors.keys)
            assertEquals(1, colors.values.map { it.container }.distinct().size)
            assertEquals(1, colors.values.map { it.onContainer }.distinct().size)
        }
    }

    @Test
    fun harmonizedModeDistinguishesEveryTypeAndKeepsOtherOnThemeHue() {
        listOf(light to false, dark to true).forEach { (scheme, isDark) ->
            val colors = resolveLeadingColors(LeadingColorMode.HARMONIZED, scheme, isDark)
            assertEquals(LeadingVisualCatalog.types.size, colors.values.map { it.container }.distinct().size)
            val primaryHue = Hct.fromInt(scheme.primary.toArgb()).hue
            val otherHue = Hct.fromInt(colors.getValue("other").container.toArgb()).hue
            assertTrue("other hue $otherHue != primary hue $primaryHue", hueDistance(primaryHue, otherHue) < 2.0)
        }
    }

    @Test
    fun fixedModeDistinguishesEveryTypeAndHasSeparateDarkColors() {
        val lightColors = resolveLeadingColors(LeadingColorMode.FIXED, light, dark = false)
        val darkColors = resolveLeadingColors(LeadingColorMode.FIXED, dark, dark = true)

        assertEquals(LeadingVisualCatalog.types.size, lightColors.values.map { it.container }.distinct().size)
        assertEquals(LeadingVisualCatalog.types.size, darkColors.values.map { it.container }.distinct().size)
        LeadingVisualCatalog.types.forEach { type ->
            assertNotEquals(type.id, lightColors.getValue(type.id), darkColors.getValue(type.id))
        }
    }

    @Test
    fun everyModeAndThemeStateMeetsWcagAa() {
        LeadingColorMode.entries.forEach { mode ->
            listOf(light to false, dark to true).forEach { (scheme, isDark) ->
                resolveLeadingColors(mode, scheme, isDark).forEach { (id, pair) ->
                    val ratio = contrastRatio(pair.container, pair.onContainer)
                    assertTrue("$mode/$id/dark=$isDark contrast=$ratio", ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun unknownColorModeFallsBackToTheme() {
        assertEquals(LeadingColorMode.THEME, LeadingColorMode.parse("not-a-mode"))
        assertEquals(LeadingColorMode.THEME, LeadingColorMode.parse(null))
    }

    private fun hueDistance(first: Double, second: Double): Double {
        val direct = abs(first - second)
        return min(direct, 360.0 - direct)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        fun luminance(color: Color): Double {
            fun linearize(component: Float): Double {
                val value = component.toDouble()
                return if (value <= 0.03928) value / 12.92
                else Math.pow((value + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linearize(color.red) +
                0.7152 * linearize(color.green) +
                0.0722 * linearize(color.blue)
        }

        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
