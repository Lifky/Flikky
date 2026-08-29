package com.example.flikky.data

import com.example.flikky.server.routes.StorageResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedStorageBrowserTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun browser(): Pair<SharedStorageBrowser, File> {
        val root = tmp.newFolder("emulated0")
        return SharedStorageBrowser(root) to root
    }

    private fun <T> ok(result: StorageResult<T>): T {
        assertTrue("expected Ok, got $result", result is StorageResult.Ok)
        return (result as StorageResult.Ok).value
    }

    @Test
    fun `listing the root returns directories first, hidden entries dropped`() {
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        File(root, ".thumbnails").mkdirs()
        File(root, "note.txt").writeText("hello")
        val listing = ok(b.list(""))
        assertEquals("", listing.path)
        assertEquals(listOf("DCIM", "note.txt"), listing.entries.map { it.name })
        assertEquals(true, listing.entries[0].isDir)
        assertEquals(5L, listing.entries[1].size)
    }

    @Test
    fun `a traversal attempt is InvalidPath, not NotFound`() {
        val (b, _) = browser()
        // 这两件事在前端提示不同（spec 4.4），不能都塌成 NotFound。
        assertEquals(StorageResult.InvalidPath, b.list("../.."))
        assertEquals(StorageResult.InvalidPath, b.open("../../etc/passwd"))
    }

    @Test
    fun `a missing path is NotFound`() {
        val (b, _) = browser()
        assertEquals(StorageResult.NotFound, b.list("nope"))
        assertEquals(StorageResult.NotFound, b.open("nope.txt"))
    }

    @Test
    fun `the Android sandboxes are Restricted, including their descendants`() {
        val (b, root) = browser()
        File(root, "Android/data/com.foo").mkdirs()
        assertEquals(StorageResult.Restricted, b.list("Android/data"))
        assertEquals(StorageResult.Restricted, b.list("Android/data/com.foo"))
        // Android 本身可列，且 data 以 restricted 标记出现，而不是被隐藏。
        val listing = ok(b.list("Android"))
        assertEquals(listOf("data"), listing.entries.map { it.name })
        assertEquals(true, listing.entries[0].restricted)
    }

    @Test
    fun `open returns a handle whose file name is the on-disk name`() {
        val (b, root) = browser()
        File(root, "Download").mkdirs()
        File(root, "Download/报告 v2.pdf").writeText("x")
        val handle = ok(b.open("Download/报告 v2.pdf"))
        assertEquals("报告 v2.pdf", handle.fileName)
        assertEquals(true, handle.file.isFile)
    }

    @Test
    fun `opening a directory is NotFound, not Ok`() {
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        assertEquals(StorageResult.NotFound, b.open("DCIM"))
    }
}
