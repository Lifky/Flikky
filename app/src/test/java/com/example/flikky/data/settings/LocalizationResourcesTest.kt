package com.example.flikky.data.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalizationResourcesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `core settings copy is available in simplified Chinese`() {
        val localized = context.forLanguage("zh-CN")

        assertEquals("设置", localized.getString(R.string.settings_title))
        assertEquals("跟随系统", localized.getString(R.string.language_system))
        assertEquals("简体中文", localized.getString(R.string.language_simplified_chinese))
        assertEquals("English", localized.getString(R.string.language_english))
    }

    @Test
    fun `core settings copy is available in English`() {
        val localized = context.forLanguage("en")

        assertEquals("Settings", localized.getString(R.string.settings_title))
        assertEquals("Follow system", localized.getString(R.string.language_system))
        assertEquals("Simplified Chinese", localized.getString(R.string.language_simplified_chinese))
        assertEquals("English", localized.getString(R.string.language_english))
    }

    private fun Context.forLanguage(languageTag: String): Context {
        val configuration = Configuration(resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(languageTag))
        }
        return createConfigurationContext(configuration)
    }
}
