package com.example.flikky.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryScanTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `scan returns entries already in the shared listing order`() {
        val d = tmp.newFolder("d")
        File(d, "b.txt").writeText("xx")
        File(d, "A.txt").writeText("x")
        File(d, "DCIM").mkdirs()
        File(d, ".hidden").writeText("x")
        // 目录优先 + 名称不区分大小写，隐藏项过滤 —— 与 StorageListingPolicy 同源。
        assertEquals(listOf("DCIM", "A.txt", "b.txt"), DirectoryScan.scan(d)!!.map { it.name })
    }

    @Test
    fun `one stat per entry yields isDir, size and mtime together`() {
        // 这是本对象存在的理由：原先每条目 3 次 stat（isDirectory / length / lastModified），
        // readAttributes 一次拿全。2000 项的目录从 6000 次系统调用降到 2000 次。
        val d = tmp.newFolder("d")
        val f = File(d, "a.bin")
        f.writeText("12345")
        File(d, "sub").mkdirs()
        val byName = DirectoryScan.scan(d)!!.associateBy { it.name }
        assertEquals(false, byName["a.bin"]!!.isDir)
        assertEquals(5L, byName["a.bin"]!!.size)
        assertTrue("mtime must be populated", byName["a.bin"]!!.mtime > 0L)
        assertEquals(true, byName["sub"]!!.isDir)
        // 目录的 size 不报文件系统给的目录大小（各平台不一致，且对用户无意义）。
        assertEquals(0L, byName["sub"]!!.size)
    }

    @Test
    fun `an entry that vanishes mid-scan is skipped, not fatal`() {
        // readAttributes 对已消失的条目会抛 IOException，而 File.isDirectory() 只返回 false。
        // 换成 nio 之后必须逐条兜住，否则「扫描期间有文件被删」会让整个目录列不出来。
        //
        // 逼红实测：第一版靠 onCancelCheck 钩子删文件，而钩子当时是每 128 条才调一次，
        // 这个只有 2 个条目的目录里它一次都没触发——删除从未发生，断言空转全绿。
        // 钩子改成逐条调用（且在读属性之前）之后，「首次回调时删掉的那个条目
        // 一定还没被成功读过」才成立，这条断言才真的在测东西。
        val d = tmp.newFolder("d")
        File(d, "a.txt").writeText("x")
        File(d, "b.txt").writeText("x")
        val doomed = File(d, "doomed.txt")
        doomed.writeText("x")
        var first = true
        val scanned = DirectoryScan.scan(d) {
            if (first) {
                first = false
                doomed.delete()
            }
        }
        assertTrue("a mid-scan deletion must not fail the whole listing", scanned != null)
        assertEquals(
            "the two surviving entries must still be listed",
            listOf("a.txt", "b.txt"),
            scanned!!.map { it.name },
        )
    }

    @Test
    fun `a non-directory or unreadable path returns null`() {
        val f = tmp.newFile("plain.txt")
        assertNull(DirectoryScan.scan(f))
        assertNull(DirectoryScan.scan(File(tmp.root, "does-not-exist")))
    }

    @Test
    fun `an empty directory scans to an empty list, not null`() {
        // null 表示「读不了」，空列表表示「读了，里面没东西」。UI 对这两者的反应不同。
        assertEquals(emptyList<ScannedEntry>(), DirectoryScan.scan(tmp.newFolder("empty")))
    }

    @Test
    fun `the first batch is small so the first rows paint fast`() {
        val entries = (1..500).map { ScannedEntry("f$it", false, 1L, 1L) }
        val batches = DirectoryScan.batches(entries)
        assertEquals(DirectoryScan.FIRST_BATCH, batches.first().size)
        assertTrue(
            "the first batch must be smaller than the later ones, or there is no point",
            batches.first().size < batches[1].size,
        )
    }

    @Test
    fun `batch sizes grow, so a huge directory needs few appends`() {
        // 固定批长的代价：调用方每批做一次 `entries + batch`，那是 O(n) 拷贝。
        // 10000 项按每批 96 算是 104 次追加、累计约 50 万次元素复制 —— 加载期间
        // 一直在制造临时数组与 GC 压力，而这些成本对用户毫无产出。
        // 指数增长把追加次数压到 O(log n)、累计拷贝压到约 2n，而前几批仍然很小
        // （用户此刻看的就是前几批）。
        val n = 10_000
        val entries = (1..n).map { ScannedEntry("f$it", false, 1L, 1L) }
        val batches = DirectoryScan.batches(entries)

        // 判据不是「批数少于某个我随手编的数字」（第一版写了 12，实测 24，
        // 而那个 12 没有任何依据）。真正要压的是**调用方的累计拷贝量**：
        // 每收到一批就 `entries + batch` 一次，代价是当时的总长度。
        // 固定 96 条时这个和约 50 万（≈50n）；翻倍后应在几倍 n 的量级。
        var running = 0
        var copied = 0L
        batches.forEach { b ->
            running += b.size
            copied += running
        }
        assertTrue(
            "total copy work must stay within a small multiple of n, got $copied for n=$n " +
                "across ${batches.size} batches",
            copied <= 5L * n,
        )
        // 前三批必须仍然小：首屏观感全靠它们。
        assertTrue("first three batches too large: " + batches.take(3).map { it.size },
            batches.take(3).all { it.size <= 128 })
        // 尺寸不许回缩（末批除外，它是余数）。
        val sizes = batches.map { it.size }
        for (i in 1 until sizes.size - 1) {
            assertTrue(
                "batch sizes must not shrink before the last one: $sizes",
                sizes[i] >= sizes[i - 1],
            )
        }
    }

    @Test
    fun `batch growth is capped so one append is never enormous`() {
        // 无上限的指数增长最后一批会是几万条，一次追加把主线程顶住 —— 
        // 正好抵掉分批的意义。
        val entries = (1..200_000).map { ScannedEntry("f$it", false, 1L, 1L) }
        val batches = DirectoryScan.batches(entries)
        assertTrue(
            "no single batch may exceed the cap, got max ${batches.maxOf { it.size }}",
            batches.all { it.size <= DirectoryScan.MAX_BATCH },
        )
    }

    @Test
    fun `batching preserves order and loses nothing`() {
        // 分批是纯切分。少一条或顺序变了，用户看到的目录内容就是错的。
        val entries = (1..250).map { ScannedEntry("f$it", it % 3 == 0, it.toLong(), 0L) }
        val batches = DirectoryScan.batches(entries)
        assertEquals(entries, batches.flatten())
        assertTrue("more than one batch expected for 250 entries", batches.size > 1)
    }

    @Test
    fun `a listing that fits in one batch is emitted as one batch`() {
        val entries = (1..5).map { ScannedEntry("f$it", false, 1L, 0L) }
        assertEquals(listOf(entries), DirectoryScan.batches(entries))
    }

    @Test
    fun `an empty listing produces no batches at all`() {
        // 一个空批次会让 UI 以为「来了一批，但是空的」，与「没有内容」混淆。
        assertEquals(emptyList<List<ScannedEntry>>(), DirectoryScan.batches(emptyList<ScannedEntry>()))
    }

    @Test
    fun `the cancellation hook runs during the scan`() {
        // 取消检查点：扫描是个没有挂起点的紧循环，不主动检查的话协程取消了它还在跑，
        // 白烧几千次系统调用（用户要求的「避免无效开销」）。
        val d = tmp.newFolder("d")
        repeat(600) { File(d, "f$it").writeText("x") }
        var checks = 0
        DirectoryScan.scan(d) { checks++ }
        // 逐条调用：600 个条目就该有 600 次检查。只断言 > 0 时，
        // 「每 128 条一次」也能过——而那个粒度让上面那条测试空转。
        assertEquals("the hook must run once per entry", 600, checks)
    }
}
