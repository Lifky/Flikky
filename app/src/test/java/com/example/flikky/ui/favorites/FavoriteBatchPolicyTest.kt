package com.example.flikky.ui.favorites

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 多选工具栏的子集语义：一次选中里往往混着文本收藏、缺副本的孤儿收藏和非媒体文件，
 * 文件类批量操作只作用于适用的那部分，其余按"跳过"计数报给用户，而不是整批失败或静默丢弃。
 */
class FavoriteBatchPolicyTest {

    private val text = FavoriteBatchItem(1L, "TEXT", null, hasFile = false)
    private val png = FavoriteBatchItem(2L, "FILE", "image/png", hasFile = true)
    private val mp4 = FavoriteBatchItem(3L, "FILE", "video/mp4", hasFile = true)
    private val pdf = FavoriteBatchItem(4L, "FILE", "application/pdf", hasFile = true)
    private val orphan = FavoriteBatchItem(5L, "FILE", "image/png", hasFile = false)
    private val svg = FavoriteBatchItem(6L, "FILE", "image/svg+xml", hasFile = true)

    private val mixed = listOf(text, png, mp4, pdf, orphan, svg)

    @Test
    fun fileTargetsDropTextAndOrphans() {
        assertEquals(listOf(2L, 3L, 4L, 6L), FavoriteBatchPolicy.fileTargets(mixed).map { it.id })
    }

    @Test
    fun mediaTargetsAlsoDropNonMedia() {
        assertEquals(listOf(2L, 3L), FavoriteBatchPolicy.mediaTargets(mixed).map { it.id })
    }

    /** SVG 归类 OTHER，存相册会得到黑图，所以不算媒体。 */
    @Test
    fun svgIsNotAMediaTarget() {
        assertEquals(emptyList<Long>(), FavoriteBatchPolicy.mediaTargets(listOf(svg)).map { it.id })
    }

    @Test
    fun skippedCountsWhatTheActionCannotTouch() {
        assertEquals(2, FavoriteBatchPolicy.skipped(mixed, FavoriteBatchPolicy.fileTargets(mixed)))
        assertEquals(4, FavoriteBatchPolicy.skipped(mixed, FavoriteBatchPolicy.mediaTargets(mixed)))
    }

    @Test
    fun textOnlySelectionHasNoFileTargets() {
        assertEquals(emptyList<Long>(), FavoriteBatchPolicy.fileTargets(listOf(text)).map { it.id })
        assertEquals(0, FavoriteBatchPolicy.fileTargets(listOf(text)).size)
    }
}
