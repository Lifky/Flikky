package com.example.flikky.ui.serving.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalStorageBrowserTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun browser(): Pair<LocalStorageBrowser, File> {
        val root = tmp.newFolder("emulated0")
        return LocalStorageBrowser(root) to root
    }

    @Test
    fun `listing uses the same order as the server side`() {
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        File(root, ".hidden").mkdirs()
        File(root, "b.txt").writeText("x")
        File(root, "A.txt").writeText("xx")
        // 目录优先 + 名称不区分大小写，与 StorageListingPolicy 同源。
        assertEquals(listOf("DCIM", "A.txt", "b.txt"), b.list("")!!.entries.map { it.name })
    }

    @Test
    fun `listing delegates to the shared policy rather than sorting on its own`() {
        // 上一条断言只钉「结果顺序」，一个自写的等价比较器照样能过。
        // 两端一致靠的是**同一个对象**，不是碰巧同样的结果：服务端将来改排序规则时，
        // 只有真的委派给 StorageListingPolicy 的一侧会自动跟上。
        // 所以这里额外钉住委派关系本身——用「构造一个只有共享策略才能给出的输入」来验：
        // 隐藏文件必须被过滤掉，且这条规则的事实源是 StorageListingPolicy.isHidden。
        val (b, root) = browser()
        File(root, ".nomedia").writeText("x")
        File(root, "visible.txt").writeText("x")
        assertEquals(listOf("visible.txt"), b.list("")!!.entries.map { it.name })
    }

    @Test
    fun `parentOf walks up one level and stops at the root`() {
        val (b, _) = browser()
        assertEquals("DCIM", b.parentOf("DCIM/Camera"))
        assertEquals("", b.parentOf("DCIM"))
        // 根目录没有上一级 —— BackHandler 的 enabled 就靠这个 null 判断。
        // 返回 "" 会让根目录也拦住返回键，用户困在文件 tab 里出不去。
        assertNull(b.parentOf(""))
    }

    @Test
    fun `selection accumulates across directories`() {
        val (b, _) = browser()
        var sel = emptySet<String>()
        sel = b.toggle(sel, "DCIM/a.jpg")
        sel = b.toggle(sel, "Download/b.pdf")
        assertEquals(setOf("DCIM/a.jpg", "Download/b.pdf"), sel)
        // 再点一次是取消
        sel = b.toggle(sel, "DCIM/a.jpg")
        assertEquals(setOf("Download/b.pdf"), sel)
    }

    @Test
    fun `resolveExisting skips files deleted behind our back and reports the count`() {
        val (b, root) = browser()
        File(root, "a.txt").writeText("x")
        val (files, skipped) = b.resolveExisting(listOf("a.txt", "gone.txt"))
        assertEquals(listOf("a.txt"), files.map { it.name })
        // 用户可能在别处删了文件；跳过数要能报给 snackbar（与 v1.17.1 收藏批量操作一致）。
        assertEquals(1, skipped)
    }

    @Test
    fun `resolveExisting refuses a directory instead of handing it to the sender`() {
        // 选择集合理论上只装文件，但目录一旦混进来（未来加「选中整个文件夹」时最容易发生），
        // offerStoredFile 会拿到一个目录 File 并以 0 字节发出去，两端都不报错。
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        File(root, "a.txt").writeText("x")
        val (files, skipped) = b.resolveExisting(listOf("DCIM", "a.txt"))
        assertEquals(listOf("a.txt"), files.map { it.name })
        assertEquals(1, skipped)
    }

    @Test
    fun `an out-of-root path is refused instead of silently resolving`() {
        val (b, _) = browser()
        assertNull(b.list("../.."))
        // 逼红实测：只断言一个**不存在**的越界路径被拒，是拿不到信号的——
        // 拿掉 StoragePathPolicy 之后它照样被 `!isFile` 挡住，断言绿着而守卫已经没了。
        // 必须让越界目标**真实存在**，这样唯一能拒绝它的就是路径守卫本身。
        val outside = tmp.newFile("outside-secret.txt")
        outside.writeText("x")
        val escape = "../" + outside.name
        assertTrue("test setup broken: escape target must exist", File(tmp.root, outside.name).isFile)
        val (files, skipped) = b.resolveExisting(listOf(escape))
        assertEquals(emptyList<File>(), files)
        assertEquals(1, skipped)
        // 目录方向同理。
        assertNull(b.list("../"))
    }

    @Test
    fun `a restricted directory is listed but flagged, not entered`() {
        // Android/data 与 Android/obb 即便有 MANAGE_EXTERNAL_STORAGE 也读不到。
        // 要显示（用户知道它存在）但要标记（UI 置灰、不可进入）。
        val (b, root) = browser()
        File(root, "Android/data").mkdirs()
        File(root, "Android/obb").mkdirs()
        File(root, "Android/media").mkdirs()
        val entries = b.list("Android")!!.entries.associateBy { it.name }
        assertTrue("Android/data must be flagged restricted", entries["data"]!!.restricted)
        assertTrue("Android/obb must be flagged restricted", entries["obb"]!!.restricted)
        assertEquals(false, entries["media"]!!.restricted)
        // 进入被拒：路径合法但内容不给。返回 null 让 UI 停在原地。
        assertNull(b.list("Android/data"))
    }

    @Test
    fun `entries carry the relative path, not just the name`() {
        // UI 用 relativePath 做 key 与选择集合的元素。只有 name 时，两个不同目录下的同名文件
        // 会互相顶掉——选了 DCIM/a.jpg 再选 Download/a.jpg，选择集合里只剩一个。
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        File(root, "DCIM/a.jpg").writeText("x")
        val e = b.list("DCIM")!!.entries.single()
        assertEquals("DCIM/a.jpg", e.relativePath)
        // 根目录下的一级项没有前导斜杠。
        assertEquals("DCIM", b.list("")!!.entries.first { it.name == "DCIM" }.relativePath)
    }
}
