package com.example.flikky.ui.favorites

import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.FilesListBuilder

/** 收藏行点击后的去向。 */
enum class FavoriteTap { COPY_TEXT, PREVIEW_IMAGE, OPEN_EXTERNAL }

/** 收藏行菜单动作，按展示顺序。 */
enum class FavoriteRowAction { COPY, SHARE, MOVE, OPEN_WITH, GALLERY, SAVE_AS, DELETE }

data class FavoriteMenuEntry(val action: FavoriteRowAction, val enabled: Boolean)

/**
 * 收藏行的纯策略：点击去向 + 行内菜单集。
 *
 * 文本收藏与文件收藏是两类东西——文本没有落盘副本，谈不上预览/分享文件/存相册/另存为；
 * 文件收藏则没有"复制"。两套集合在这里分叉，UI 只负责渲染，不再各自判断。
 * 点击去向与文件总览保持零差异：只有图片进应用内预览，其余交外部应用。
 */
object FavoriteActionPolicy {
    const val KIND_TEXT = "TEXT"

    fun tap(kind: String, mime: String?): FavoriteTap = when {
        kind == KIND_TEXT -> FavoriteTap.COPY_TEXT
        FilesListBuilder.categoryOf(mime) == FileCategory.IMAGE -> FavoriteTap.PREVIEW_IMAGE
        else -> FavoriteTap.OPEN_EXTERNAL
    }

    /** @param hasFile 落盘副本是否可用；缺失时涉及文件的项置灰但仍可见，用户才知道为什么点不动。 */
    fun rowMenu(kind: String, mime: String?, hasFile: Boolean): List<FavoriteMenuEntry> = buildList {
        if (kind == KIND_TEXT) {
            add(FavoriteMenuEntry(FavoriteRowAction.COPY, true))
            add(FavoriteMenuEntry(FavoriteRowAction.MOVE, true))
            add(FavoriteMenuEntry(FavoriteRowAction.DELETE, true))
            return@buildList
        }
        add(FavoriteMenuEntry(FavoriteRowAction.SHARE, hasFile))
        add(FavoriteMenuEntry(FavoriteRowAction.MOVE, true))
        add(FavoriteMenuEntry(FavoriteRowAction.OPEN_WITH, hasFile))
        if (FilesListBuilder.isMedia(mime)) {
            add(FavoriteMenuEntry(FavoriteRowAction.GALLERY, hasFile))
        }
        add(FavoriteMenuEntry(FavoriteRowAction.SAVE_AS, hasFile))
        add(FavoriteMenuEntry(FavoriteRowAction.DELETE, true))
    }
}
