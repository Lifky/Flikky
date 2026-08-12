package com.example.flikky.ui.favorites

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 收藏行的点击去向与行内菜单集。文本收藏与文件收藏是两类东西：
 * 文本不能预览/分享文件/存相册/另存为，文件不能"复制"。策略层保证两者永远不会串。
 */
class FavoriteActionPolicyTest {

    private fun actions(kind: String, mime: String?, hasFile: Boolean = true) =
        FavoriteActionPolicy.rowMenu(kind, mime, hasFile).map { it.action }

    // ---- 点击去向：与文件总览零差异（只有图片进应用内预览）----

    @Test
    fun textFavoriteTapCopies() {
        assertEquals(FavoriteTap.COPY_TEXT, FavoriteActionPolicy.tap("TEXT", null))
    }

    @Test
    fun imageFavoriteTapPreviews() {
        assertEquals(FavoriteTap.PREVIEW_IMAGE, FavoriteActionPolicy.tap("FILE", "image/png"))
    }

    @Test
    fun videoFavoriteTapOpensExternally() {
        assertEquals(FavoriteTap.OPEN_EXTERNAL, FavoriteActionPolicy.tap("FILE", "video/mp4"))
    }

    /** SVG 归类为 OTHER（v1.17.0 裁决），不能当图片预览。 */
    @Test
    fun svgFavoriteTapOpensExternally() {
        assertEquals(FavoriteTap.OPEN_EXTERNAL, FavoriteActionPolicy.tap("FILE", "image/svg+xml"))
    }

    @Test
    fun documentFavoriteTapOpensExternally() {
        assertEquals(FavoriteTap.OPEN_EXTERNAL, FavoriteActionPolicy.tap("FILE", "application/pdf"))
    }

    // ---- 菜单集 ----

    @Test
    fun textFavoriteMenuIsCopyMoveDelete() {
        assertEquals(
            listOf(
                FavoriteRowAction.COPY,
                FavoriteRowAction.MOVE,
                FavoriteRowAction.DELETE,
            ),
            actions("TEXT", null),
        )
    }

    @Test
    fun mediaFavoriteMenuOffersGallery() {
        assertEquals(
            listOf(
                FavoriteRowAction.SHARE,
                FavoriteRowAction.MOVE,
                FavoriteRowAction.OPEN_WITH,
                FavoriteRowAction.GALLERY,
                FavoriteRowAction.SAVE_AS,
                FavoriteRowAction.DELETE,
            ),
            actions("FILE", "image/png"),
        )
    }

    @Test
    fun nonMediaFavoriteMenuHidesGallery() {
        assertEquals(
            listOf(
                FavoriteRowAction.SHARE,
                FavoriteRowAction.MOVE,
                FavoriteRowAction.OPEN_WITH,
                FavoriteRowAction.SAVE_AS,
                FavoriteRowAction.DELETE,
            ),
            actions("FILE", "application/pdf"),
        )
    }

    /** SVG 与分类一致：不给存相册（存进去是黑图）。 */
    @Test
    fun svgFavoriteMenuHidesGallery() {
        assertEquals(
            listOf(
                FavoriteRowAction.SHARE,
                FavoriteRowAction.MOVE,
                FavoriteRowAction.OPEN_WITH,
                FavoriteRowAction.SAVE_AS,
                FavoriteRowAction.DELETE,
            ),
            actions("FILE", "image/svg+xml"),
        )
    }

    /** 落盘副本缺失时，涉及文件的项置灰，但分组/删除仍可用（否则孤儿收藏无法清理）。 */
    @Test
    fun missingFileDisablesFileActionsButKeepsMoveAndDelete() {
        val entries = FavoriteActionPolicy.rowMenu("FILE", "image/png", hasFile = false)
            .associate { it.action to it.enabled }

        assertEquals(false, entries[FavoriteRowAction.SHARE])
        assertEquals(false, entries[FavoriteRowAction.OPEN_WITH])
        assertEquals(false, entries[FavoriteRowAction.GALLERY])
        assertEquals(false, entries[FavoriteRowAction.SAVE_AS])
        assertEquals(true, entries[FavoriteRowAction.MOVE])
        assertEquals(true, entries[FavoriteRowAction.DELETE])
    }
}
