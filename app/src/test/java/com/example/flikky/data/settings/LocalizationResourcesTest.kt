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
        assertEquals(
            "无法打开链接，请安装浏览器后重试",
            localized.getString(R.string.settings_open_source_open_failed),
        )
    }

    @Test
    fun `core settings copy is available in English`() {
        val localized = context.forLanguage("en")

        assertEquals("Settings", localized.getString(R.string.settings_title))
        assertEquals("Follow system", localized.getString(R.string.language_system))
        assertEquals("Simplified Chinese", localized.getString(R.string.language_simplified_chinese))
        assertEquals("English", localized.getString(R.string.language_english))
        assertEquals(
            "Couldn't open the link. Install a browser and try again.",
            localized.getString(R.string.settings_open_source_open_failed),
        )
    }

    @Test
    fun `empty state copy uses semantic line breaks in simplified Chinese`() {
        val localized = context.forLanguage("zh-CN")

        assertEquals(
            "点 + 添加本地文本或文件\n也可以在消息或文件上点 ☆ 收藏",
            localized.getString(R.string.favorites_empty),
        )
        assertEquals(
            "尚无历史会话。\n点右下“启动服务”开始第一次传输。",
            localized.getString(R.string.home_empty),
        )
    }

    @Test
    fun `empty state copy uses semantic line breaks in English`() {
        val localized = context.forLanguage("en")

        assertEquals(
            "Tap + to add local text or files.\nYou can also tap ☆ on a message or file.",
            localized.getString(R.string.favorites_empty),
        )
        assertEquals(
            "No previous sessions.\nTap “Start service” to begin your first transfer.",
            localized.getString(R.string.home_empty),
        )
    }

    private fun Context.forLanguage(languageTag: String): Context {
        val configuration = Configuration(resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(languageTag))
        }
        return createConfigurationContext(configuration)
    }
}
