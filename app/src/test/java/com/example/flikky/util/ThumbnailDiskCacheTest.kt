package com.example.flikky.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThumbnailDiskCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `size sums regular cache files`() {
        val dir = temporaryFolder.newFolder("thumbs")
        file(dir, "a.jpg", 3, 1L)
        file(dir, "b.jpg", 5, 2L)
        dir.resolve("nested").mkdir()

        assertEquals(8L, ThumbnailDiskCache(dir, { 100L }).sizeBytes())
    }

    @Test
    fun `write evicts least recently used files after the new file lands`() {
        val dir = temporaryFolder.newFolder("thumbs")
        val oldest = file(dir, "old.jpg", 4, 1_000L)
        val latest = file(dir, "latest.jpg", 4, 2_000L)
        val cache = ThumbnailDiskCache(dir, { 8L }, clock = { 3_000L })

        val written = cache.put("new.jpg") { target ->
            target.writeBytes(ByteArray(4))
            true
        }

        assertNotNull(written)
        assertFalse(oldest.exists())
        assertTrue(latest.exists())
        assertTrue(dir.resolve("new.jpg").exists())
        assertEquals(8L, cache.sizeBytes())
    }

    @Test
    fun `cache hit refreshes last modified without evicting on the read path`() {
        val dir = temporaryFolder.newFolder("thumbs")
        val hit = file(dir, "hit.jpg", 4, 1_000L)
        val other = file(dir, "other.jpg", 4, 2_000L)
        val cache = ThumbnailDiskCache(dir, { 4L }, clock = { 5_000L })

        assertEquals(hit, cache.get("hit.jpg"))

        assertEquals(5_000L, hit.lastModified())
        assertTrue("reads must not run eviction", other.exists())
        assertEquals(8L, cache.sizeBytes())
    }

    @Test
    fun `zero ceiling skips disk generation entirely`() {
        val dir = temporaryFolder.newFolder("thumbs")
        var generated = false
        val cache = ThumbnailDiskCache(dir, { 0L })

        val result = cache.put("new.jpg") {
            generated = true
            it.writeBytes(byteArrayOf(1))
            true
        }

        assertNull(result)
        assertFalse(generated)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `next write applies a newly reduced ceiling`() {
        val dir = temporaryFolder.newFolder("thumbs")
        var ceiling = 12L
        var now = 1_000L
        val cache = ThumbnailDiskCache(dir, { ceiling }, clock = { now++ })
        repeat(3) { index ->
            assertNotNull(cache.put("$index.jpg") { it.writeBytes(ByteArray(4)); true })
        }
        assertEquals(12L, cache.sizeBytes())

        ceiling = 4L
        assertNotNull(cache.put("last.jpg") { it.writeBytes(ByteArray(4)); true })

        assertEquals(listOf("last.jpg"), dir.listFiles().orEmpty().map(File::getName).sorted())
    }

    private fun file(directory: File, name: String, size: Int, modifiedAt: Long): File =
        directory.resolve(name).apply {
            writeBytes(ByteArray(size))
            assertTrue(setLastModified(modifiedAt))
        }
}
