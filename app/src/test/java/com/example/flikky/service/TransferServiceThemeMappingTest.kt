package com.example.flikky.service

import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.DarkMode
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

    @Test
    fun `amoled flag maps into PeerInfoDto only alongside dark`() {
        val darkAmoled = with(TransferService.Companion) {
            FlikkySettings(darkMode = DarkMode.DARK, amoled = true)
                .toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }
        assertTrue(darkAmoled.themeDark)
        assertTrue(darkAmoled.amoled)
    }

    @Test
    fun `amoled is reported verbatim even in light mode so the browser can decide`() {
        // 刻意不在映射层做「浅色时把 amoled 抹成 false」的清洗：
        // 手机端 amoled 是独立持久化的偏好，切回浅色再切回深色时应保持。
        // 浏览器端按 themeDark 短路即可，服务端不替它做决定。
        val lightAmoled = with(TransferService.Companion) {
            FlikkySettings(darkMode = DarkMode.LIGHT, amoled = true)
                .toPeerInfoDto(systemDark = true, defaultDeviceName = "Phone")
        }
        assertFalse(lightAmoled.themeDark)
        assertTrue(lightAmoled.amoled)
    }

    @Test
    fun `amoled defaults to false on a fresh install`() {
        val fresh = with(TransferService.Companion) {
            FlikkySettings().toPeerInfoDto(systemDark = false, defaultDeviceName = "Phone")
        }
        assertFalse(fresh.amoled)
    }
}
