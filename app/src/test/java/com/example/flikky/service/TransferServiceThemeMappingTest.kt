package com.example.flikky.service

import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
