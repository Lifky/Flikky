package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeHsvTest {
    @Test
    fun themeSeedToHsv_mapsRgbPrimaries() {
        assertHsv(ThemeHsv(0f, 1f, 1f), themeSeedToHsv(0xFFFF0000L))
        assertHsv(ThemeHsv(120f, 1f, 1f), themeSeedToHsv(0xFF00FF00L))
        assertHsv(ThemeHsv(240f, 1f, 1f), themeSeedToHsv(0xFF0000FFL))
    }

    @Test
    fun themeHsvToSeed_mapsKnownColors() {
        assertEquals(0xFFFF0000L, themeHsvToSeed(ThemeHsv(0f, 1f, 1f)))
        assertEquals(0xFF00FF00L, themeHsvToSeed(ThemeHsv(120f, 1f, 1f)))
        assertEquals(0xFF0000FFL, themeHsvToSeed(ThemeHsv(240f, 1f, 1f)))
        assertEquals(0xFF808080L, themeHsvToSeed(ThemeHsv(0f, 0f, 0.5f)))
    }

    private fun assertHsv(expected: ThemeHsv, actual: ThemeHsv) {
        assertEquals(expected.hue, actual.hue, 0.01f)
        assertEquals(expected.saturation, actual.saturation, 0.001f)
        assertEquals(expected.value, actual.value, 0.001f)
    }
}
