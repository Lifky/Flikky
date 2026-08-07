package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeSeedTest {
    @Test
    fun parseThemeSeed_acceptsSixDigitRgbWithOptionalHash() {
        assertEquals(0xFF33618DL, parseThemeSeed("#33618D"))
        assertEquals(0xFF8F4A4CL, parseThemeSeed("8f4a4c"))
    }

    @Test
    fun parseThemeSeed_rejectsInvalidInput() {
        assertNull(parseThemeSeed("#12345"))
        assertNull(parseThemeSeed("#FF33618D"))
        assertNull(parseThemeSeed("#GG3361"))
        assertNull(parseThemeSeed(""))
    }

    @Test
    fun formatThemeSeed_outputsUppercaseRgbWithoutAlpha() {
        assertEquals("#03618D", formatThemeSeed(0x7F03618DL))
    }
}
