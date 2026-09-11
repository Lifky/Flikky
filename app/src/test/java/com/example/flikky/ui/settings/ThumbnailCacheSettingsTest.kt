package com.example.flikky.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailCacheSettingsTest {

    private val screen by lazy {
        val candidates = listOf(
            File("src/main/java/com/example/flikky/ui/settings/SettingsScreen.kt"),
            File("app/src/main/java/com/example/flikky/ui/settings/SettingsScreen.kt"),
        )
        val source = candidates.firstOrNull { it.isFile }?.readText()
            ?: error("SettingsScreen.kt not found")
        stripCommentsAndImports(source)
    }

    @Test
    fun `thumbnail cache dialog is reachable from its data row`() {
        assertTrue("sanity: SettingsScreen product code was not found", screen.contains("fun SettingsScreen("))
        assertTrue(
            "thumbnail cache row does not open its dialog",
            screen.contains("showThumbnailCacheDialog = true"),
        )
        assertTrue(
            "thumbnail cache dialog is not composed",
            screen.contains("if (showThumbnailCacheDialog)"),
        )
    }

    @Test
    fun `thumbnail cache dialog offers only the four declared ceilings`() {
        assertTrue("sanity: SettingsScreen product code was not found", screen.contains("fun SettingsScreen("))
        val dialog = screen.substringAfter("if (showThumbnailCacheDialog)", missingDelimiterValue = "")
            .substringBefore("if (showDeleteAllDialog)")
        assertTrue("thumbnail cache dialog slice is empty", dialog.isNotBlank())
        assertTrue(
            "cache dialog must use the declared ceiling list",
            dialog.contains("THUMBNAIL_CACHE_LIMIT_OPTIONS_MB.forEach"),
        )
        assertTrue(
            "zero must be labelled as the no-cache choice",
            dialog.contains("R.string.settings_thumbnail_cache_none"),
        )
        assertFalse("cache ceiling must use choices, not an input", dialog.contains("OutlinedTextField("))
    }

    private fun stripCommentsAndImports(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\r\n]*"""), "")
        .replace(Regex("""(?m)^\s*import\s+[^\r\n]+\r?\n"""), "")
}
