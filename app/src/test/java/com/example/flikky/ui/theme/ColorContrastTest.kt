package com.example.flikky.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {

    private fun lum(c: Color): Double {
        fun linearize(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
        }
        val r = linearize(c.red)
        val g = linearize(c.green)
        val b = linearize(c.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = lum(a)
        val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertContrast(schemeLabel: String, fg: Color, bg: Color) {
        val c = contrast(fg, bg)
        assertTrue("$schemeLabel: contrast $c < 4.5", c >= 4.5)
    }

    // Coral
    @Test fun coral_light_text_contrast() {
        assertContrast("CoralLight onPrimary/primary", CoralLight.onPrimary, CoralLight.primary)
        assertContrast("CoralLight onSurface/surface", CoralLight.onSurface, CoralLight.surface)
        assertContrast("CoralLight onSecondaryContainer/secondaryContainer",
            CoralLight.onSecondaryContainer, CoralLight.secondaryContainer)
    }

    @Test fun coral_dark_text_contrast() {
        assertContrast("CoralDark onPrimary/primary", CoralDark.onPrimary, CoralDark.primary)
        assertContrast("CoralDark onSurface/surface", CoralDark.onSurface, CoralDark.surface)
        assertContrast("CoralDark onSecondaryContainer/secondaryContainer",
            CoralDark.onSecondaryContainer, CoralDark.secondaryContainer)
    }

    // Mushroom
    @Test fun mushroom_light_text_contrast() {
        assertContrast("MushroomLight onPrimary/primary", MushroomLight.onPrimary, MushroomLight.primary)
        assertContrast("MushroomLight onSurface/surface", MushroomLight.onSurface, MushroomLight.surface)
        assertContrast("MushroomLight onSecondaryContainer/secondaryContainer",
            MushroomLight.onSecondaryContainer, MushroomLight.secondaryContainer)
    }

    @Test fun mushroom_dark_text_contrast() {
        assertContrast("MushroomDark onPrimary/primary", MushroomDark.onPrimary, MushroomDark.primary)
        assertContrast("MushroomDark onSurface/surface", MushroomDark.onSurface, MushroomDark.surface)
        assertContrast("MushroomDark onSecondaryContainer/secondaryContainer",
            MushroomDark.onSecondaryContainer, MushroomDark.secondaryContainer)
    }

    // Teal
    @Test fun teal_light_text_contrast() {
        assertContrast("TealLight onPrimary/primary", TealLight.onPrimary, TealLight.primary)
        assertContrast("TealLight onSurface/surface", TealLight.onSurface, TealLight.surface)
        assertContrast("TealLight onSecondaryContainer/secondaryContainer",
            TealLight.onSecondaryContainer, TealLight.secondaryContainer)
    }

    @Test fun teal_dark_text_contrast() {
        assertContrast("TealDark onPrimary/primary", TealDark.onPrimary, TealDark.primary)
        assertContrast("TealDark onSurface/surface", TealDark.onSurface, TealDark.surface)
        assertContrast("TealDark onSecondaryContainer/secondaryContainer",
            TealDark.onSecondaryContainer, TealDark.secondaryContainer)
    }

    // Mist
    @Test fun mist_light_text_contrast() {
        assertContrast("MistLight onPrimary/primary", MistLight.onPrimary, MistLight.primary)
        assertContrast("MistLight onSurface/surface", MistLight.onSurface, MistLight.surface)
        assertContrast("MistLight onSecondaryContainer/secondaryContainer",
            MistLight.onSecondaryContainer, MistLight.secondaryContainer)
    }

    @Test fun mist_dark_text_contrast() {
        assertContrast("MistDark onPrimary/primary", MistDark.onPrimary, MistDark.primary)
        assertContrast("MistDark onSurface/surface", MistDark.onSurface, MistDark.surface)
        assertContrast("MistDark onSecondaryContainer/secondaryContainer",
            MistDark.onSecondaryContainer, MistDark.secondaryContainer)
    }
}
