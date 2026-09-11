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

    @Test
    fun `thumbnail cache usage distinguishes calculating from an empty cache`() {
        assertTrue("sanity: SettingsScreen product code was not found", screen.contains("fun SettingsScreen("))
        val row = screen.substringAfter(
            "title = stringResource(R.string.settings_thumbnail_cache)",
            missingDelimiterValue = "",
        ).substringBefore("onClick = { showThumbnailCacheDialog = true }")
        assertTrue("thumbnail cache row slice is empty", row.isNotBlank())
        assertTrue(
            "thumbnail cache usage is not collected by the settings screen",
            screen.contains("thumbnailCacheUsageBytes.collectAsState"),
        )
        assertTrue(
            "the loading state must not be rendered as zero bytes",
            row.contains("R.string.settings_thumbnail_cache_calculating"),
        )
        assertTrue(
            "computed usage must use the shared byte formatter",
            row.contains("formatBytes("),
        )
    }

    @Test
    fun `clear cache is immediate and does not open a confirmation dialog`() {
        assertTrue("sanity: SettingsScreen product code was not found", screen.contains("fun SettingsScreen("))
        val dialog = screen.substringAfter("if (showThumbnailCacheDialog)", missingDelimiterValue = "")
            .substringBefore("if (showDeleteAllDialog)")
        assertTrue("thumbnail cache dialog slice is empty", dialog.isNotBlank())
        assertTrue(
            "clear-cache button is not wired to the view model",
            dialog.contains("viewModel.clearThumbnailCache()"),
        )
        assertFalse(
            "derived cache clearing must not ask for destructive-action confirmation",
            dialog.contains("AlertDialog(") || dialog.contains("showDeleteAllDialog = true"),
        )
    }

    private fun stripCommentsAndImports(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\r\n]*"""), "")
        .replace(Regex("""(?m)^\s*import\s+[^\r\n]+\r?\n"""), "")
}
