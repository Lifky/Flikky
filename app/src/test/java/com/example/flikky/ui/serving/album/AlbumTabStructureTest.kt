package com.example.flikky.ui.serving.album

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural guards for the album tab behavior that compilation cannot verify. */
class AlbumTabStructureTest {

    private val tab by lazy { productCode("ui/serving/album/ServingAlbumTab.kt") }
    private val grid by lazy { productCode("ui/serving/album/AlbumGrid.kt") }

    @Test
    fun `all three permission states are handled`() {
        assertTrue(tab.contains("AlbumAccess.None"))
        assertTrue(tab.contains("AlbumAccess.Partial"))
        assertTrue(tab.contains("AlbumAccess.Full"))
    }

    @Test
    fun `partial access shows the real count, not a vague label`() {
        assertTrue(tab.contains("R.string.album_scope_partial"))
        val permissionDispatch = tab.substring(tab.indexOf("when (access)")).substringBefore("var selecting")
        val partialBranch = permissionDispatch.substringAfter("AlbumAccess.Partial")
            .substringBefore("AlbumAccess.Full")
        assertFalse(
            "partial access was routed back to the permission card",
            partialBranch.contains("AlbumPermissionCard") || partialBranch.contains("return"),
        )
        val partialContent = tab.substring(tab.indexOf("if (access == AlbumAccess.Partial)")).take(300)
        assertTrue(
            "partial access does not pass the real count to its scope banner",
            partialContent.contains("AlbumScopeBanner(count = visibleCount"),
        )
    }

    @Test
    fun `partial access still shows the grid`() {
        // 原判据是「`AlbumAccess.Partial` 之后 900 字符内出现 AlbumGrid(」，而 2026-09-16
        // 加入视图切换与相册簿分支后那个窗口装不下了 —— 意图（Partial 是可用状态，
        // 要继续显示已授权的那部分，而不是像 None 一样只给一张授权卡）没有变，
        // 所以改成直接验那个意图：Partial 不走 return，且它与 Full 落在同一条分支上。
        val accessBranch = tab.substring(tab.indexOf("when (access)")).take(400)
        assertTrue(
            "Partial 必须与 Full 同走「继续渲染内容」这条分支：\n$accessBranch",
            Regex("""AlbumAccess\.Partial,\s*AlbumAccess\.Full\s*->""").containsMatchIn(accessBranch),
        )
        assertTrue(
            "只有 None 才允许提前 return 成授权卡",
            Regex("""AlbumAccess\.None\s*->\s*\{[^}]*AlbumPermissionCard""").containsMatchIn(accessBranch),
        )
        assertTrue("相册 tab 不再渲染时间线了", tab.contains("AlbumGrid("))
    }

    @Test
    fun `the lock fab never requests a permission`() {
        val lockBlock = tab.substring(tab.indexOf("PeerChannelLockFab(")).take(500)
        assertFalse(
            "the lock FAB requests permission and violates B44",
            lockBlock.contains("onRequestPermission") || lockBlock.contains("onChangeScope"),
        )
    }

    @Test
    fun `changing the photo scope is its own explicit entry point`() {
        assertTrue(tab.contains("R.string.album_scope_change"))
        assertTrue(tab.contains("onChangeScope"))
    }

    @Test
    fun `grouping goes through the shared timeline, not re-derived here`() {
        assertTrue("the grid does not use the shared grouping", grid.contains("AlbumTimeline.group("))
        assertFalse(
            "the grid derives dates locally",
            grid.contains("LocalDate.now(") || grid.contains("Calendar.getInstance("),
        )
    }

    @Test
    fun `date headers span the full row`() {
        assertTrue(grid.contains("maxLineSpan"))
    }

    @Test
    fun `the grid does not measure its own cell size`() {
        assertFalse(
            "the grid measures its own cells",
            grid.contains("onGloballyPositioned") || grid.contains("BoxWithConstraints"),
        )
    }

    @Test
    fun `tapping previews and long pressing selects`() {
        assertTrue("the cell does not use a combined click target", grid.contains("combinedClickable("))
        assertTrue(grid.contains("onLongClick"))
        assertTrue(grid.contains("onPreview") || grid.contains("onTap"))
    }

    @Test
    fun `selection uses no checkbox`() {
        assertFalse("the album introduced a checkbox", grid.contains("Checkbox("))
    }

    @Test
    fun `no remote image source is loaded`() {
        listOf(tab, grid).forEach {
            assertFalse(it.contains("http://"))
            assertFalse(it.contains("https://"))
        }
    }

    private fun productCode(relative: String): String {
        val file = File("src/main/java/com/example/flikky/$relative")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/$relative")
        assertTrue("source file not found: $relative", file.isFile)
        return stripCommentsAndImports(file.readText())
    }

    private fun stripCommentsAndImports(source: String): String = source
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\r\n]*"""), "")
        .replace(Regex("""(?m)^\s*import\s+[^\r\n]+\r?\n"""), "")
}
