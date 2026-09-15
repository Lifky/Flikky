package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards album dependencies that must be resolved again after a Wi-Fi rebind. */
class AlbumRebindReferenceTest {

    @Test
    fun `album gates are passed as lambdas, never as snapshots`() {
        val service = productCode("service/TransferService.kt")
        val block = service.substring(service.indexOf("albumBrowsingEnabled"))
            .take(600)

        assertTrue(
            "albumBrowsingEnabled must be a lambda",
            Regex("""albumBrowsingEnabled\s*=\s*\{""").containsMatchIn(service),
        )
        assertTrue(
            "albumAccessProvider must be a lambda",
            Regex("""albumAccessProvider\s*=\s*\{""").containsMatchIn(service),
        )
        assertTrue(
            "mediaLibraryProvider must be a lambda",
            Regex("""mediaLibraryProvider\s*=\s*\{""").containsMatchIn(service),
        )
        assertFalse("sanity: album wiring block is empty", block.isEmpty())
    }

    @Test
    fun `the route takes a provider, not a library instance`() {
        val routes = productCode("server/routes/AlbumRoutes.kt")

        assertTrue(routes.contains("library: () -> MediaLibrary?"))
        assertTrue(routes.contains("enabled: suspend () -> Boolean"))
    }

    @Test
    fun `the guard fires on a snapshot style wiring`() {
        val bad = "albumBrowsingEnabled = latestSettings.albumBrowsingEnabled,"

        assertFalse(
            Regex("""albumBrowsingEnabled\s*=\s*\{""").containsMatchIn(bad),
        )
    }

    private fun productCode(relative: String): String {
        val file = File("src/main/java/com/example/flikky/$relative")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/$relative")
        assertTrue("source file not found: $relative", file.isFile)
        return stripCommentsAndImports(file.readText())
    }

    private fun stripCommentsAndImports(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\r\n]*"""), "")
        .replace(Regex("""(?m)^\s*import\s+[^\r\n]+\r?\n"""), "")
}
