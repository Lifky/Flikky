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

    @Test
    fun `KtorServer forwards the storage thumbnail provider to storage routes`() {
        val src = read("src/main/java/com/example/flikky/server/KtorServer.kt")
        assertTrue(src.contains("storageThumbFileProvider: ((String) -> File)?"))
        assertTrue(src.contains("storageThumbFile = storageThumbFileProvider"))
        assertTrue(src.contains("thumbnailer = thumbnailGenerator"))
    }

    @Test
    fun `TransferService supplies the SessionFileStore owned thumbnail path`() {
        val src = read("src/main/java/com/example/flikky/service/TransferService.kt")
        assertTrue(src.contains("storageThumbFileProvider ="))
        assertTrue(src.contains("ServiceLocator.fileStore.storageThumbFile(key)"))
    }
}
