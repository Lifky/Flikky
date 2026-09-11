package com.example.flikky.server

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KtorServerStorageThumbnailWiringTest {

    private fun read(path: String): String {
        val candidates = listOf(File(path), File("app/$path"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("source file not found: $path")
    }

    private fun codeOnly(source: String): String = source
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lineSequence()
        .filterNot { it.trimStart().startsWith("import ") }
        .map { it.substringBefore("//") }
        .joinToString("\n")

    @Test
    fun `KtorServer forwards the storage thumbnail provider to storage routes`() {
        val src = codeOnly(read("src/main/java/com/example/flikky/server/KtorServer.kt"))
        assertTrue("sanity: KtorServer code slice is empty", src.contains("class KtorServer("))
        assertTrue(src.contains("storageThumbFileProvider: ((String) -> File)?"))
        assertTrue(src.contains("storageThumbFile = storageThumbFileProvider"))
        assertTrue(src.contains("thumbnailer = thumbnailGenerator"))
        assertTrue(src.contains("favoriteThumbFileProvider: ((Long) -> File)?"))
        assertTrue(src.contains("favoriteThumbFile = favoriteThumbFileProvider"))
    }

    @Test
    fun `TransferService supplies the SessionFileStore owned thumbnail path`() {
        val src = codeOnly(read("src/main/java/com/example/flikky/service/TransferService.kt"))
        assertTrue("sanity: TransferService code slice is empty", src.contains("class TransferService"))
        assertTrue(src.contains("storageThumbFileProvider ="))
        assertTrue(src.contains("ServiceLocator.fileStore.storageThumbFile(key)"))
        assertTrue(
            "thumbnail cache ceiling must be read from the latest settings on every request",
            src.contains(
                "storageThumbnailCacheMaxBytes = { " +
                    "latestSettings.thumbnailCacheLimitMb * 1024L * 1024L }",
            ),
        )
        assertTrue(src.contains("favoriteThumbFileProvider ="))
        assertTrue(src.contains("ServiceLocator.favoriteFileStore.thumbnailFile(id)"))
    }
}
