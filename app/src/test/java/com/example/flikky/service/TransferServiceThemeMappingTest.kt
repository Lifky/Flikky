package com.example.flikky.service

import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferServiceThemeMappingTest {
    @Test
    fun animationSpeed_sendsSelectedSpeedToBrowser() {
        val dto = with(TransferService.Companion) {
            FlikkySettings(animationSpeed = AnimationSpeed.SLOW)
                .toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }

        assertEquals("SLOW", dto.animationSpeed)
    }

    @Test
    fun customTheme_sendsCustomSeedToBrowser() {
        val dto = with(TransferService.Companion) {
            FlikkySettings(
                themeMode = ThemeMode.CUSTOM,
                customThemeSeedArgb = 0xFF03618DL,
            ).toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }

        assertEquals("#03618D", dto.themeSeed)
    }

    @Test
    fun dynamicTheme_keepsBrowserOnDefaultSeed() {
        val dto = with(TransferService.Companion) {
            FlikkySettings(themeMode = ThemeMode.DYNAMIC)
                .toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }

        assertNull(dto.themeSeed)
    }

    @Test
    fun sessionTimestampFlag_mapsIntoPeerInfoDto() {
        val on = with(TransferService.Companion) {
            FlikkySettings(sessionTimestampEnabled = true)
                .toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }
        val off = with(TransferService.Companion) {
            FlikkySettings(sessionTimestampEnabled = false)
                .toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }

        assertTrue(on.sessionTimestampEnabled)
        assertFalse(off.sessionTimestampEnabled)
    }
}
