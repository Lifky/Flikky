package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailCacheKeyTest {

    @Test
    fun `same file identity produces the same key`() {
        assertEquals(
            thumbnailCacheKey("/storage/emulated/0/DCIM/photo.jpg", 123L, 456L),
            thumbnailCacheKey("/storage/emulated/0/DCIM/photo.jpg", 123L, 456L),
        )
    }

    @Test
    fun `replacement metadata changes the key`() {
        val original = thumbnailCacheKey("/storage/emulated/0/DCIM/photo.jpg", 123L, 456L)
        assertNotEquals(original, thumbnailCacheKey("/storage/emulated/0/DCIM/photo.jpg", 124L, 456L))
        assertNotEquals(original, thumbnailCacheKey("/storage/emulated/0/DCIM/photo.jpg", 123L, 457L))
    }

    @Test
    fun `different absolute paths do not collide`() {
        assertNotEquals(
            thumbnailCacheKey("/storage/emulated/0/DCIM/a.jpg", 123L, 456L),
            thumbnailCacheKey("/storage/emulated/0/DCIM/b.jpg", 123L, 456L),
        )
    }

    @Test
    fun `key is a file-name-safe sha256 hex value`() {
        val key = thumbnailCacheKey("/storage/emulated/0/DCIM/photo.jpg", 123L, 456L)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        assertTrue('/' !in key && ".." !in key && ' ' !in key)
    }

    @Test
    fun `special path characters remain opaque inside the hash`() {
        val key = thumbnailCacheKey("/storage/emulated/0/相册 /../#封面.jpg", 123L, 456L)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }
}
