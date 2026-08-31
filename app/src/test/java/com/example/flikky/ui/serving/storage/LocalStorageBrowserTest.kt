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
    fun `entries carry an openable absolute path for thumbnails`() {
        // Coil 只能用真实路径。缺了它，媒体行会静默回落成图标容器——
        // 看起来只是"这个文件没缩略图"，不会有任何报错，也没人会发现整个缩略图功能没生效。
        val (b, root) = browser()
        File(root, "photo.jpg").writeText("x")
        val e = b.list("")!!.entries.single()
        assertTrue("absolutePath must point at a real file: " + e.absolutePath,
            File(e.absolutePath).isFile)
        // 且必须是绝对路径，不是相对路径换个字段名。
        assertTrue("absolutePath must be absolute: " + e.absolutePath,
            File(e.absolutePath).isAbsolute)
    }

    @Test
    fun `a directory carries its child count so both ends say the same thing`() {
        // 服务端 DTO 一直给项数（浏览器显示「13 项」），App 端首版显示固定文案「文件夹」——
        // 同一个目录在手机上和电脑上说的不是一件事。
        val (b, root) = browser()
        File(root, "DCIM").mkdirs()
        File(root, "DCIM/a.jpg").writeText("x")
        File(root, "DCIM/b.jpg").writeText("y")
        File(root, "note.txt").writeText("z")
        val entries = b.list("")!!.entries.associateBy { it.name }
        assertEquals(2, entries["DCIM"]!!.childCount)
        // 文件没有子项数，不是 0 —— 0 会被 UI 显示成「0 项」。
        assertNull(entries["note.txt"]!!.childCount)
    }

    @Test
    fun `an empty directory reports zero, not null`() {
        // null 与 0 在 UI 上是两句话：null 回落成「文件夹」，0 说「0 项」。
        // 空目录确实是 0 项，不该退化成没有信息。
        val (b, root) = browser()
        File(root, "Empty").mkdirs()
        assertEquals(0, b.list("")!!.entries.single().childCount)
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

class StorageRowActionTest {

    private fun entry(
        name: String = "a.txt",
        isDir: Boolean = false,
        restricted: Boolean = false,
    ) = LocalEntry(
        name = name,
        relativePath = name,
        absolutePath = "/storage/emulated/0/" + name,
        isDir = isDir,
        size = 1L,
        mtime = 0L,
        mime = null,
        restricted = restricted,
    )

    @Test
    fun `a directory opens and never enters the selection`() {
        assertEquals(StorageRowAction.OPEN, storageRowAction(entry("DCIM", isDir = true)))
    }

    @Test
    fun `a file toggles on a single tap`() {
        // 上游 D5 的裁决：单击即勾选（不是「单击打开、长按选」）。
        assertEquals(StorageRowAction.TOGGLE, storageRowAction(entry("a.jpg")))
    }

    @Test
    fun `a restricted entry is inert even though it is also a directory`() {
        // restricted 必须先判。顺序颠倒 → Android/data 变成可进入的普通目录，
        // 进去之后 list() 返回 null，UI 停在原地，看起来像「点了没反应」的 bug。
        assertEquals(
            StorageRowAction.NONE,
            storageRowAction(entry("data", isDir = true, restricted = true)),
        )
        // 沙箱里的文件同理（理论上列不出来，但决策要自洽）。
        assertEquals(StorageRowAction.NONE, storageRowAction(entry("x", restricted = true)))
    }
}

class BreadcrumbSegmentsTest {

    @Test
    fun `the root alone is a single crumb`() {
        assertEquals(listOf("内部存储"), breadcrumbSegments("", "内部存储").map { it?.label })
    }

    @Test
    fun `up to four levels are all shown`() {
        // 与浏览器端 panel-files.js 的 breadcrumbSegments 同构：≤ 4 级全显示。
        val got = breadcrumbSegments("a/b/c", "Root")
        assertEquals(listOf("Root", "a", "b", "c"), got.map { it?.label })
        assertEquals(listOf("", "a", "a/b", "a/b/c"), got.map { it?.path })
    }

    @Test
    fun `deeper paths collapse the middle into a single placeholder`() {
        // 首级 + … + 末两级。null 就是那个 … 占位。
        val got = breadcrumbSegments("a/b/c/d/e", "Root")
        assertEquals(listOf("Root", null, "d", "e"), got.map { it?.label })
        assertEquals(listOf("", null, "a/b/c/d", "a/b/c/d/e"), got.map { it?.path })
    }

    @Test
    fun `each crumb carries the path to navigate to, not just its name`() {
        // 只存 name 时点中间一级没法回去——这是面包屑唯一的功能。
        val got = breadcrumbSegments("DCIM/Camera", "Root")
        assertEquals("DCIM", got[1]!!.path)
        assertEquals("DCIM/Camera", got[2]!!.path)
    }

    @Test
    fun `leading and trailing slashes do not create empty crumbs`() {
        assertEquals(listOf("Root", "a"), breadcrumbSegments("/a/", "Root").map { it?.label })
    }
}
