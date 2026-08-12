package com.example.flikky.ui.favorites

import com.example.flikky.ui.files.FilesListBuilder

/** 多选批量操作需要的最小收藏信息。 */
data class FavoriteBatchItem(
    val id: Long,
    val kind: String,
    val mime: String?,
    val hasFile: Boolean,
)

/**
 * 多选工具栏的子集语义。
 *
 * 一次选中通常混着文本收藏、缺落盘副本的孤儿收藏和非媒体文件。文件类批量操作只作用于适用的
 * 子集，动不了的按"跳过"报数——既不整批失败，也不静默丢弃（用户会以为都办成了）。
 */
object FavoriteBatchPolicy {

    /** 分享 / 另存为的目标：有落盘副本的文件收藏。 */
    fun fileTargets(items: List<FavoriteBatchItem>): List<FavoriteBatchItem> =
        items.filter { it.kind != FavoriteActionPolicy.KIND_TEXT && it.hasFile }

    /** 存相册的目标：再收窄到图片/视频（SVG 归 OTHER，存进去是黑图）。 */
    fun mediaTargets(items: List<FavoriteBatchItem>): List<FavoriteBatchItem> =
        fileTargets(items).filter { FilesListBuilder.isMedia(it.mime) }

    /** 本次选中里这个动作够不着的条数。 */
    fun skipped(items: List<FavoriteBatchItem>, targets: List<FavoriteBatchItem>): Int =
        items.size - targets.size
}
