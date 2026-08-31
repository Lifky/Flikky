package com.example.flikky.ui.serving.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageNavigationTest {

    private fun entry(name: String) = LocalEntry(
        name = name,
        relativePath = name,
        absolutePath = "/root/" + name,
        isDir = false,
        size = 1L,
        mtime = 0L,
        mime = null,
        restricted = false,
    )

    private val loaded = LocalStorageState(
        path = "DCIM",
        entries = listOf(entry("a.jpg")),
        selected = setOf("Download/x.pdf"),
        loading = false,
    )

    @Test
    fun `begin advances the path immediately so the tap has a visible effect`() {
        val next = StorageNavigation.begin(loaded, "DCIM/Camera")
        assertEquals("DCIM/Camera", next.path)
        assertTrue("begin must set loading", next.loading)
    }

    @Test
    fun `begin clears the old entries rather than showing them under a new path`() {
        // 留着旧列表 = 把上一个目录的内容画在新目录的面包屑下面，用户会以为点错了。
        val next = StorageNavigation.begin(loaded, "DCIM/Camera")
        assertEquals(emptyList<LocalEntry>(), next.entries)
    }

    @Test
    fun `begin keeps the selection, because it accumulates across directories`() {
        val next = StorageNavigation.begin(loaded, "DCIM/Camera")
        assertEquals(setOf("Download/x.pdf"), next.selected)
    }

    @Test
    fun `begin normalises the requested path`() {
        assertEquals("", StorageNavigation.begin(loaded, "/").path)
        assertEquals("DCIM", StorageNavigation.begin(loaded, "/DCIM/").path)
        assertEquals("", StorageNavigation.begin(loaded, "  ").path)
    }

    @Test
    fun `settle lands the listing and clears loading`() {
        val pending = StorageNavigation.begin(loaded, "DCIM/Camera")
        val listed = LocalStorageState(
            path = "DCIM/Camera",
            entries = listOf(entry("b.jpg")),
        )
        val next = StorageNavigation.settle(pending, listed, fallback = loaded)
        assertEquals("DCIM/Camera", next.path)
        assertEquals(listOf("b.jpg"), next.entries.map { it.name })
        assertFalse("settle must clear loading", next.loading)
    }

    @Test
    fun `settle keeps the selection made before the navigation`() {
        val pending = StorageNavigation.begin(loaded, "DCIM/Camera")
        val listed = LocalStorageState(path = "DCIM/Camera", entries = emptyList())
        val next = StorageNavigation.settle(pending, listed, fallback = loaded)
        assertEquals(setOf("Download/x.pdf"), next.selected)
    }

    @Test
    fun `a failed listing retreats to the last good location`() {
        // begin 已经把路径改成请求的那个。失败后停在原地，面包屑会指向一个没进去的
        // 目录而列表是空的 —— 看起来像「这个文件夹是空的」，而事实是「没能打开」。
        val pending = StorageNavigation.begin(loaded, "Android/data")
        val next = StorageNavigation.settle(pending, listed = null, fallback = loaded)
        assertEquals("DCIM", next.path)
        assertEquals(listOf("a.jpg"), next.entries.map { it.name })
    }

    @Test
    fun `a failed listing also clears loading`() {
        // 最容易漏的一条：失败分支忘了清 loading，进度条永远转着，
        // 而失败在真机上很难复现（要恰好点到沙箱目录或被删掉的目录）。
        val pending = StorageNavigation.begin(loaded, "Android/data")
        val next = StorageNavigation.settle(pending, listed = null, fallback = loaded)
        assertFalse("the failure branch must clear loading too", next.loading)
    }

    @Test
    fun `a failed listing keeps the selection as well`() {
        val pending = StorageNavigation.begin(loaded, "Android/data")
        val next = StorageNavigation.settle(pending, listed = null, fallback = loaded)
        assertEquals(setOf("Download/x.pdf"), next.selected)
    }

    @Test
    fun `settle takes the selection from the pending state, not the fallback`() {
        // 导航途中用户仍可勾选（列表已清空，但上一批选择还在）。
        // 从 fallback 取 selected 会把导航开始后的改动丢掉。
        val pending = StorageNavigation.begin(loaded, "X")
            .copy(selected = setOf("Download/x.pdf", "new.txt"))
        val next = StorageNavigation.settle(pending, listed = null, fallback = loaded)
        assertEquals(setOf("Download/x.pdf", "new.txt"), next.selected)
    }
}
