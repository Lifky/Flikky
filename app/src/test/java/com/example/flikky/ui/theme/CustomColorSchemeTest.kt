package com.example.flikky.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColorSchemeTest {
    @Test
    fun customScheme_usesSeedAndDarkMode() {
        val blueLight = customScheme(0xFF33618DL, dark = false)
        val redLight = customScheme(0xFF8F4A4CL, dark = false)
        val blueDark = customScheme(0xFF33618DL, dark = true)

        assertNotEquals(blueLight.primary, redLight.primary)
        assertNotEquals(blueLight.primary, blueDark.primary)
    }

    @Test
    fun customScheme_contrastLevelsIncreasePrimaryReadability() {
        val standard = customScheme(
            seedArgb = 0xFF33618DL,
            dark = false,
            contrast = ResolvedContrast.STANDARD,
        )
        val medium = customScheme(
            seedArgb = 0xFF33618DL,
            dark = false,
            contrast = ResolvedContrast.MEDIUM,
        )
        val high = customScheme(
            seedArgb = 0xFF33618DL,
            dark = false,
            contrast = ResolvedContrast.HIGH,
        )

        val standardRatio = contrastRatio(standard.onPrimary, standard.primary)
        val mediumRatio = contrastRatio(medium.onPrimary, medium.primary)
        val highRatio = contrastRatio(high.onPrimary, high.primary)

        assertTrue(standardRatio >= 4.5)
        assertTrue(mediumRatio >= standardRatio)
        assertTrue(highRatio >= mediumRatio)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        fun luminance(color: Color): Double {
            fun linearize(component: Float): Double {
                val value = component.toDouble()
                return if (value <= 0.03928) {
                    value / 12.92
                } else {
                    Math.pow((value + 0.055) / 1.055, 2.4)
                }
            }
            return 0.2126 * linearize(color.red) +
                0.7152 * linearize(color.green) +
                0.0722 * linearize(color.blue)
        }

        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (maxOf(firstLuminance, secondLuminance) + 0.05) /
            (minOf(firstLuminance, secondLuminance) + 0.05)
    }
}
