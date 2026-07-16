package com.example.flikky.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `empty language tags follow the system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags(""))
    }

    @Test
    fun `English language tags select English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-US"))
    }

    @Test
    fun `Chinese language tags select simplified Chinese`() {
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-CN"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTags("zh-Hans-CN"))
    }

    @Test
    fun `unsupported app language falls back to system`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("fr"))
    }

    @Test
    fun `explicit app language wins when resolving the web language`() {
        assertEquals(
            "en",
            AppLanguage.resolveEffectiveLanguageTag(
                applicationLanguageTags = "en-US",
                systemLanguageTags = "zh-CN",
            ),
        )
    }

    @Test
    fun `system mode selects the first supported system locale`() {
        assertEquals(
            "en",
            AppLanguage.resolveEffectiveLanguageTag(
                applicationLanguageTags = "",
                systemLanguageTags = "fr-FR,en-US,zh-CN",
            ),
        )
        assertEquals(
            "zh-CN",
            AppLanguage.resolveEffectiveLanguageTag(
                applicationLanguageTags = "",
                systemLanguageTags = "zh-HK,en-US",
            ),
        )
    }

    @Test
    fun `unsupported system locales use the unqualified Chinese resources`() {
        assertEquals(
            "zh-CN",
            AppLanguage.resolveEffectiveLanguageTag(
                applicationLanguageTags = "",
                systemLanguageTags = "fr-FR,de-DE",
            ),
        )
    }
}
