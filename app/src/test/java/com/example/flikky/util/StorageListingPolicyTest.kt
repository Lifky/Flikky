package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageListingPolicyTest {

    private data class Item(val name: String, val isDir: Boolean)

    private fun sort(vararg items: Item): List<String> =
        StorageListingPolicy.filterAndSort(items.toList(), { it.isDir }, { it.name }).map { it.name }

    @Test
    fun `dot-prefixed entries are filtered out`() {
        assertTrue(StorageListingPolicy.isHidden(".thumbnails"))
        assertFalse(StorageListingPolicy.isHidden("Download"))
        assertEquals(
            listOf("Download", "a.txt"),
            sort(Item(".hidden", false), Item("Download", true), Item("a.txt", false)),
        )
    }

    @Test
    fun `directories come before files regardless of name`() {
        assertEquals(listOf("zzz", "aaa.txt"), sort(Item("aaa.txt", false), Item("zzz", true)))
    }

    @Test
    fun `names sort case-insensitively`() {
        assertEquals(
            listOf("apple.txt", "Banana.txt", "cherry.txt"),
            sort(Item("cherry.txt", false), Item("Banana.txt", false), Item("apple.txt", false)),
        )
    }

    @Test
    fun `the two Android sandboxes are restricted, and so is everything under them`() {
        assertTrue(StorageListingPolicy.isRestricted("Android/data"))
        assertTrue(StorageListingPolicy.isRestricted("Android/obb"))
        // 含子路径：只判两级会让 Android/data/com.foo 落到「路径不存在」的 404，
        // 而它明明存在，只是系统不给读——那个答案是不诚实的。
        assertTrue(StorageListingPolicy.isRestricted("Android/data/com.foo"))
        assertTrue(StorageListingPolicy.isRestricted("Android/obb/com.foo/cache"))
    }

    @Test
    fun `neighbouring paths are not restricted`() {
        assertFalse(StorageListingPolicy.isRestricted("Android"))
        assertFalse(StorageListingPolicy.isRestricted("Android/media"))
        assertFalse(StorageListingPolicy.isRestricted("AndroidX/data"))
        assertFalse(StorageListingPolicy.isRestricted(""))
    }
}
