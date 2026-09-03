package com.example.flikky.ui.serving.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目录缓存：**回退不重新加载**（用户 2026-09-03 第 7 条，两端都做）。
 *
 * 纯逻辑，不碰 Android —— 缓存的判据（存什么、淘汰谁、什么时候不能存）全在这里，
 * ViewModel 只负责调它。这样这些边界能在 `test/` 跑，不用仪器测试。
 */
class StorageDirectoryCacheTest {

    private fun entries(n: Int, prefix: String = "f"): List<LocalEntry> =
        (1..n).map {
            LocalEntry(
                name = "$prefix$it",
                relativePath = "$prefix$it",
                absolutePath = "/x/$prefix$it",
                isDir = false,
                size = 1,
                mtime = 0,
                mime = null,
                restricted = false,
            )
        }

    @Test
    fun `a stored directory comes back`() {
        val c = StorageDirectoryCache()
        c.put("DCIM", entries(3), scrollIndex = 0, scrollOffset = 0)
        val hit = c.get("DCIM")
        assertNotNull(hit)
        assertEquals(3, hit!!.entries.size)
    }

    @Test
    fun `a directory never visited is a miss`() {
        assertNull(StorageDirectoryCache().get("nope"))
    }

    @Test
    fun `scroll position rides along with the entries`() {
        val c = StorageDirectoryCache()
        c.put("DCIM", entries(200), scrollIndex = 47, scrollOffset = 13)
        val hit = c.get("DCIM")!!
        assertEquals(47, hit.scrollIndex)
        assertEquals(13, hit.scrollOffset)
    }

    @Test
    fun `the least recently used directory is evicted first`() {
        val c = StorageDirectoryCache()
        repeat(StorageDirectoryCache.MAX_DIRS + 1) { c.put("d$it", entries(1), 0, 0) }
        assertNull("the first directory visited must be gone", c.get("d0"))
        assertNotNull("the last one must still be there", c.get("d${StorageDirectoryCache.MAX_DIRS}"))
    }

    @Test
    fun `reading a directory makes it recent again`() {
        // 少了这一条，LRU 就退化成 FIFO：一直在用的目录会因为「存得早」被淘汰。
        val c = StorageDirectoryCache()
        repeat(StorageDirectoryCache.MAX_DIRS) { c.put("d$it", entries(1), 0, 0) }
        assertNotNull(c.get("d0"))
        c.put("fresh", entries(1), 0, 0)
        assertNotNull("d0 was just read, so it must not be the eviction victim", c.get("d0"))
        assertNull("d1 is now the least recently used", c.get("d1"))
    }

    @Test
    fun `a few huge directories are evicted on total entries`() {
        // 判别式：目录数远低于目录上限，但总条目超了。只有按总条目卡的那道上限
        // 才能让最早那个被淘汰 —— 少了它，32 个各含一万条的目录一样撑爆内存。
        val c = StorageDirectoryCache()
        val per = StorageDirectoryCache.MAX_ENTRIES / 3
        c.put("big0", entries(per, "a"), 0, 0)
        c.put("big1", entries(per, "b"), 0, 0)
        c.put("big2", entries(per, "c"), 0, 0)
        c.put("big3", entries(per, "d"), 0, 0)
        assertTrue("only 4 directories, so the dir cap cannot be what evicts", 4 < StorageDirectoryCache.MAX_DIRS)
        assertNull("big0 must have been evicted on the entry budget", c.get("big0"))
        assertTrue(
            "cached entries must stay within budget, was ${c.entryCount}",
            c.entryCount <= StorageDirectoryCache.MAX_ENTRIES,
        )
    }

    @Test
    fun `a directory larger than the whole budget is not cached at all`() {
        // 存它会把其他所有目录挤光，而它自己下次也一定被淘汰 —— 白占一轮。
        val c = StorageDirectoryCache()
        c.put("small", entries(5), 0, 0)
        c.put("huge", entries(StorageDirectoryCache.MAX_ENTRIES + 1, "h"), 0, 0)
        assertNull("the oversized directory must not be stored", c.get("huge"))
        assertNotNull("and it must not have evicted the others on its way out", c.get("small"))
    }

    @Test
    fun `clearing drops everything`() {
        val c = StorageDirectoryCache()
        c.put("a", entries(2), 0, 0)
        c.put("b", entries(2), 0, 0)
        c.clear()
        assertNull(c.get("a"))
        assertNull(c.get("b"))
        assertEquals(0, c.entryCount)
    }

    @Test
    fun `re-storing a directory replaces it instead of double counting`() {
        val c = StorageDirectoryCache()
        c.put("a", entries(10), 0, 0)
        c.put("a", entries(4), 0, 0)
        assertEquals(4, c.get("a")!!.entries.size)
        assertEquals("entryCount must not drift on replace", 4, c.entryCount)
    }

    @Test
    fun `the cache copies what it is given`() {
        // 调用方手里的列表后来被改动，缓存里那份不该跟着变 ——
        // 否则「秒回」回来的是一份被改过的历史。
        val c = StorageDirectoryCache()
        val live = ArrayList(entries(2))
        c.put("a", live, 0, 0)
        live.clear()
        assertEquals(2, c.get("a")!!.entries.size)
    }

    @Test
    fun `removing one directory leaves the others cached`() {
        // 针对性审查发现（2026-09-03）：App 端 `force` 走的是 `storageCache.clear()`,
        // 而浏览器端只丢目标那一个。于是手机上刷新一次 —— 或者授权完成、回到前台 ——
        // 就把**所有**目录的缓存全扔了，秒回的好处一次性归零。两端语义必须一致。
        val c = StorageDirectoryCache()
        c.put("a", entries(3), 0, 0)
        c.put("b", entries(2), 0, 0)
        c.remove("a")
        assertNull("the named directory must be gone", c.get("a"))
        assertNotNull("but the others must survive", c.get("b"))
        assertEquals("entryCount must follow", 2, c.entryCount)
    }

    @Test
    fun `removing a directory that was never cached is harmless`() {
        val c = StorageDirectoryCache()
        c.put("a", entries(3), 0, 0)
        c.remove("nope")
        assertNotNull(c.get("a"))
        assertEquals(3, c.entryCount)
    }
}
