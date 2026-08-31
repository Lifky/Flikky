package com.example.flikky.ui.serving.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageSendSummaryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun browser(): Pair<LocalStorageBrowser, File> {
        val root = tmp.newFolder("emulated0")
        return LocalStorageBrowser(root) to root
    }

    @Test
    fun `summary counts selected files and sums their sizes`() {
        val (b, root) = browser()
        File(root, "a.txt").writeText("12345")
        File(root, "b.txt").writeText("123")
        val s = b.selectionSummary(setOf("a.txt", "b.txt"))
        assertEquals(2, s.count)
        assertEquals(8L, s.totalBytes)
        assertEquals(0, s.skipped)
    }

    @Test
    fun `sizes of files that vanished are excluded from the summary`() {
        // 用户可能在别处删了文件；合计不该继续算它，否则「合计大小」与实际发出的量不符，
        // 用户看到 8 KB 却只收到 3 KB，会以为传输丢了数据。
        val (b, root) = browser()
        File(root, "a.txt").writeText("123")
        val s = b.selectionSummary(setOf("a.txt", "gone.txt"))
        assertEquals(1, s.count)
        assertEquals(3L, s.totalBytes)
        assertEquals(1, s.skipped)
    }

    @Test
    fun `an empty selection summarises to zero rather than throwing`() {
        val (b, _) = browser()
        val s = b.selectionSummary(emptySet())
        assertEquals(0, s.count)
        assertEquals(0L, s.totalBytes)
        assertEquals(0, s.skipped)
    }

    @Test
    fun `a directory in the selection is skipped, not counted as a zero-byte file`() {
        // 目录混进选择集合时，count 里多一项、totalBytes 不变，用户会看到
        // 「已选 2 项 · 3 B」然后只收到 1 个文件。必须算进 skipped。
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        File(root, "a.txt").writeText("123")
        val s = b.selectionSummary(setOf("DCIM", "a.txt"))
        assertEquals(1, s.count)
        assertEquals(3L, s.totalBytes)
        assertEquals(1, s.skipped)
    }

    @Test
    fun `an out-of-root path never contributes to the summary`() {
        val (b, _) = browser()
        val outside = tmp.newFile("outside.txt")
        outside.writeText("1234567890")
        val s = b.selectionSummary(setOf("../" + outside.name))
        assertEquals(0, s.count)
        assertEquals(0L, s.totalBytes)
        assertEquals(1, s.skipped)
    }

    @Test
    fun `the summary agrees with what resolveExisting will actually send`() {
        // 两个方法各算一遍是分叉的起点：摘要说 2 项、发送只发 1 个，两边测试都能是绿的。
        // 这条把它们钉在一起。
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        File(root, "a.txt").writeText("123")
        File(root, "b.txt").writeText("45")
        val selection = setOf("DCIM", "a.txt", "b.txt", "gone.txt")
        val s = b.selectionSummary(selection)
        val (files, skipped) = b.resolveExisting(selection)
        assertEquals(files.size, s.count)
        assertEquals(skipped, s.skipped)
        assertEquals(files.sumOf { it.length() }, s.totalBytes)
    }
}
