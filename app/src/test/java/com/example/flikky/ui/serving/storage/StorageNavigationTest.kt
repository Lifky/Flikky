package com.example.flikky.ui.serving.storage

import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageNavigationTest {

    private fun entry(
        name: String,
        isDir: Boolean = false,
        size: Long = 1L,
        mtime: Long = 0L,
        relativePath: String = name,
    ) = LocalEntry(
        name = name,
        relativePath = relativePath,
        absolutePath = "/root/" + name,
        isDir = isDir,
        size = size,
        mtime = mtime,
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

    @Test
    fun `head normalises the path and clears the list to receive the first batch`() {
        val pending = StorageNavigation.begin(loaded, "dcim")
        val next = StorageNavigation.head(pending, "DCIM")
        assertEquals("DCIM", next.path)
        assertEquals(emptyList<LocalEntry>(), next.entries)
        assertTrue("head must stay loading: no entries have arrived yet", next.loading)
    }

    @Test
    fun `append concatenates and never reorders`() {
        // 批次到达时顺序已经是全局有序的（DirectoryScan 先排完再切批）。
        // 在这里重排会让已经画出来的行在用户眼前跳位。
        val a = listOf(entry("a"), entry("b"))
        val b = listOf(entry("c"))
        var s = StorageNavigation.head(StorageNavigation.begin(loaded, "X"), "X")
        s = StorageNavigation.append(s, s.path, a)
        s = StorageNavigation.append(s, s.path, b)
        assertEquals(listOf("a", "b", "c"), s.entries.map { it.name })
    }

    @Test
    fun `append records where the newest batch starts, for the row stagger`() {
        // UI 用 index - lastBatchStart 算逐行入场的阶梯序号。记错了，
        // 整批的阶梯就会从中间开始，或者整批一起闪。
        var s = StorageNavigation.head(StorageNavigation.begin(loaded, "X"), "X")
        s = StorageNavigation.append(s, s.path, listOf(entry("a"), entry("b")))
        assertEquals(0, s.lastBatchStart)
        s = StorageNavigation.append(s, s.path, listOf(entry("c"), entry("d")))
        assertEquals(2, s.lastBatchStart)
        s = StorageNavigation.append(s, s.path, listOf(entry("e")))
        assertEquals(4, s.lastBatchStart)
    }

    @Test
    fun `append keeps loading on, because more batches are coming`() {
        var s = StorageNavigation.head(StorageNavigation.begin(loaded, "X"), "X")
        s = StorageNavigation.append(s, s.path, listOf(entry("a")))
        assertTrue("the progress indicator must stay while batches keep arriving", s.loading)
    }

    @Test
    fun `append preserves a selection made while the list was still growing`() {
        // 用户可以在列表还在生长时就勾选已经出现的行。追加批次不该把它清掉。
        var s = StorageNavigation.head(StorageNavigation.begin(loaded, "X"), "X")
        s = StorageNavigation.append(s, s.path, listOf(entry("a")))
        s = s.copy(selected = setOf("a"))
        s = StorageNavigation.append(s, s.path, listOf(entry("b")))
        assertEquals(setOf("a"), s.selected)
    }

    @Test
    fun `complete clears loading and touches nothing else`() {
        var s = StorageNavigation.head(StorageNavigation.begin(loaded, "X"), "X")
        s = StorageNavigation.append(s, s.path, listOf(entry("a"), entry("b")))
        val before = s.entries
        val done = StorageNavigation.complete(s)
        assertFalse("complete must clear loading", done.loading)
        assertEquals(before, done.entries)
        assertEquals(s.lastBatchStart, done.lastBatchStart)
        assertEquals(s.path, done.path)
    }

    @Test
    fun `an empty directory completes with no batches and an empty list`() {
        // DirectoryScan.batches(emptyList()) 返回零个批次，所以 append 一次都不调。
        // 空目录必须清掉 loading，否则进度条永远转着。
        val s = StorageNavigation.complete(
            StorageNavigation.head(StorageNavigation.begin(loaded, "Empty"), "Empty"),
        )
        assertFalse(s.loading)
        assertEquals(emptyList<LocalEntry>(), s.entries)
    }
    @Test
    fun `appending a batch that belongs to another directory changes nothing`() {
        // 装机验收（2026-09-03，秒回上线后的严重回归）：
        // 「进入深度子文件夹并返回后，大概率会重叠渲染进入的多个文件夹中的文件列表」。
        //
        // 根因：`storageJob.cancel()` 是协作式的。`flowOn(IO)` 在生产者与消费者之间
        // 放了一个 channel，取消之后仍可能有一批已经派发到 Main 的数据跑完 ——
        // 而 `append` 是「往当前 state 上接」，此刻 state 已经被缓存恢复的那个目录替换了。
        // 于是两个目录的条目并进一个列表，`relativePath` 撞 key，
        // LazyColumn 把它们画在同一个位置上（截图里能看到行叠着行）。
        //
        // 第一道防线是 ViewModel 里的世代号（守卫在 ServingTabsStructureTest）。
        // 这里是第二道：append 只接**属于当前路径**的批次。
        val current = LocalStorageState(
            path = "DCIM",
            entries = listOf(entry("DCIM/a.jpg")),
            loading = false,
        )
        val out = StorageNavigation.append(current, "Music", listOf(entry("Music/x.mp3")))
        assertEquals("a batch for another directory must be dropped", current, out)
    }

    @Test
    fun `appending a batch for the current directory still works`() {
        // 反向守卫：上面那条不许把正常追加也挡掉。
        val current = LocalStorageState(
            path = "DCIM",
            entries = listOf(entry("DCIM/a.jpg")),
            loading = true,
        )
        val out = StorageNavigation.append(current, "DCIM", listOf(entry("DCIM/b.jpg")))
        assertEquals(2, out.entries.size)
        assertEquals(1, out.lastBatchStart)
        assertTrue(out.loading)
    }

    @Test
    fun `appending never produces two entries with the same relative path`() {
        // relativePath 就是 LazyColumn 的 key。撞 key 的后果不是异常，是把行画在
        // 同一个位置上 —— 视觉损坏，而且很难从代码上看出来。所以这条单独钉住。
        val current = LocalStorageState(
            path = "DCIM",
            entries = listOf(entry("DCIM/a.jpg"), entry("DCIM/b.jpg")),
            loading = true,
        )
        val out = StorageNavigation.append(
            current,
            "DCIM",
            listOf(entry("DCIM/b.jpg"), entry("DCIM/c.jpg")),
        )
        assertEquals(
            "a repeated entry must not be added twice",
            listOf("DCIM/a.jpg", "DCIM/b.jpg", "DCIM/c.jpg"),
            out.entries.map { it.relativePath },
        )
        assertEquals(
            "keys must stay unique",
            out.entries.size,
            out.entries.map { it.relativePath }.toSet().size,
        )
    }
    @Test
    fun `begin starts at the top unless a resume position is given`() {
        val out = StorageNavigation.begin(loaded, "X")
        assertEquals("-1 means: start from the top", -1, out.restoredScrollIndex)
        assertEquals(0, out.restoredScrollOffset)
    }

    @Test
    fun `begin carries a resume position through`() {
        // 刷新同一个目录时用它把位置带过去 —— 列表状态是每个目录
        // 一份的，刷新会先清空再重建，不带位置就弹回顶部。
        val out = StorageNavigation.begin(loaded, "X", 47, 13)
        assertEquals(47, out.restoredScrollIndex)
        assertEquals(13, out.restoredScrollOffset)
        assertTrue("it is still the start of a listing", out.loading)
        assertTrue("and the list starts empty", out.entries.isEmpty())
    }

    @Test
    fun `an offset without an index is discarded`() {
        // 偏移量离开下标没有意义。允许它单独存在的话，
        // 列表会从第 0 项开始、却带着一个莫名其妙的像素偏移。
        val out = StorageNavigation.begin(loaded, "X", -1, 999)
        assertEquals(0, out.restoredScrollOffset)
    }

    // ── 原地重排与过滤（Task 10 / 11）────────────────────────────────────

    @Test
    fun `resort reorders in place without touching the path or the selection`() {
        val state = LocalStorageState(
            path = "DCIM",
            entries = listOf(entry("b.txt", size = 1), entry("a.txt", size = 9)),
            selected = setOf("a.txt"),
        )

        val sorted = StorageNavigation.resort(state, SortSpec(SortKey.SIZE, descending = true))

        assertEquals(listOf("a.txt", "b.txt"), sorted.entries.map { it.name })
        assertEquals("DCIM", sorted.path)
        assertEquals(setOf("a.txt"), sorted.selected)
    }

    @Test
    fun `resort drops the remembered scroll position`() {
        // 顺序全变之后，停在原来的下标上看到的是一堆无关的东西 ——
        // 那个位置已经没有意义了。
        val state = LocalStorageState(
            path = "DCIM",
            entries = listOf(entry("a.txt"), entry("b.txt")),
            restoredScrollIndex = 7,
            restoredScrollOffset = 40,
        )

        val sorted = StorageNavigation.resort(state, SortSpec(SortKey.NAME, descending = true))

        assertEquals(-1, sorted.restoredScrollIndex)
        assertEquals(0, sorted.restoredScrollOffset)
    }

    @Test
    fun `resort keeps hidden entries that are already in the list`() {
        // 列表里的隐藏项是「显示隐藏文件」开着时列进来的。重排是**排序**，
        // 不是重新过滤；再滤一次会把它们悄悄删掉，而用户刚才明明看得见。
        val state = LocalStorageState(
            path = "",
            entries = listOf(entry(".nomedia"), entry("a.txt")),
        )

        val sorted = StorageNavigation.resort(state, SortSpec(SortKey.NAME, descending = false))

        assertEquals(2, sorted.entries.size)
    }

    @Test
    fun `resort keeps directories first regardless of direction`() {
        val state = LocalStorageState(
            path = "",
            entries = listOf(entry("z.txt", size = 9), entry("Docs", isDir = true, size = 0)),
        )

        val desc = StorageNavigation.resort(state, SortSpec(SortKey.SIZE, descending = true))

        assertEquals(listOf("Docs", "z.txt"), desc.entries.map { it.name })
    }

}

class StorageTravelDirectionTest {

    @Test
    fun `going deeper is forward and coming back up is not`() {
        assertTrue(StorageNavigationDirection.forward("DCIM", "DCIM/Camera"))
        assertFalse(StorageNavigationDirection.forward("DCIM/Camera", "DCIM"))
        assertTrue(StorageNavigationDirection.forward("", "DCIM"))
        assertFalse(StorageNavigationDirection.forward("DCIM", ""))
    }

    @Test
    fun `a lateral move is forward regardless of name length`() {
        // 这条是判据的分水岭。用字符串长度时 DCIM -> A 会判成「返回」，
        // 因为 "A" 比 "DCIM" 短 —— 而它其实是同一层的平移。
        assertTrue("DCIM -> Music", StorageNavigationDirection.forward("DCIM", "Music"))
        assertTrue("DCIM -> A", StorageNavigationDirection.forward("DCIM", "A"))
        assertTrue("A -> DCIM", StorageNavigationDirection.forward("A", "DCIM"))
    }

    @Test
    fun `refreshing the same path is forward, not a retreat`() {
        // 授权完成后会重读当前目录。判成「返回」会让列表反向滑一下，很怪。
        assertTrue(StorageNavigationDirection.forward("DCIM", "DCIM"))
        assertTrue(StorageNavigationDirection.forward("", ""))
    }

    @Test
    fun `depth ignores leading, trailing and doubled separators`() {
        assertEquals(0, StorageNavigationDirection.depth(""))
        assertEquals(0, StorageNavigationDirection.depth("/"))
        assertEquals(1, StorageNavigationDirection.depth("/DCIM/"))
        assertEquals(2, StorageNavigationDirection.depth("DCIM//Camera"))
    }

    @Test
    fun `jumping several levels up is still a retreat`() {
        // 点面包屑最左边那一级：跨好几层回根。
        assertFalse(StorageNavigationDirection.forward("a/b/c/d", ""))
        assertFalse(StorageNavigationDirection.forward("a/b/c/d", "a"))
    }
}
