package com.example.flikky.ui.favorites

import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.util.NAME_ORDER
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec

/**
 * 收藏列表的排序。纯函数，无 Android 依赖。
 *
 * 收藏有「文件」与「文本」两类（[FavoriteEntity.kind]），两个键因此各有一处特殊语义，
 * 见下面两段注释。浏览器端镜像是 `assets/web/panel-favorites.js` 的 `sortFavorites`，
 * 两份实现由 `app/src/test/resources/sort-order.json` 的 `favorites` 段钉死。
 */
object FavoritesListOrder {

    /**
     * 名称排序键 = 行上显示的那段文字的**原始值**。
     *
     * 刻意不用 `FavoriteRow.primaryText()`：它对空值回落到本地化占位串
     * （`favorites_unnamed_file` / `favorites_empty_text`），按它排会让
     * **切换语言改变排序**。按原始值排是正确的，不是妥协。
     */
    private fun nameKey(item: FavoriteEntity): String =
        item.fileName ?: item.textContent ?: ""

    fun sort(items: List<FavoriteEntity>, spec: SortSpec): List<FavoriteEntity> {
        val byKey: Comparator<FavoriteEntity> = when (spec.key) {
            SortKey.NAME -> compareBy(NAME_ORDER) { nameKey(it) }
            // 「时间」在收藏页是**收藏时间**，不是原消息的时间戳。
            SortKey.TIME -> compareBy { it.createdAt }
            SortKey.SIZE -> compareBy { it.fileSize ?: 0L }
        }
        val directed = if (spec.descending) byKey.reversed() else byKey
        val ordered = if (spec.key == SortKey.SIZE) {
            // 「没有大小」不是「大小为 0」。无文件的项**两个方向都排在末尾** ——
            // 升序时把一堆文本收藏顶到最前面，对用户没有任何意义。
            compareBy<FavoriteEntity> { if (it.fileSize == null) 1 else 0 }.then(directed)
        } else {
            directed
        }
        // 兜底恒定**升序**，与 StorageListingPolicy / FilesListBuilder 一致：
        // 同键的两项在两个方向下相对顺序相同，才是可预测的。
        return items.sortedWith(ordered.thenBy(NAME_ORDER) { nameKey(it) })
    }
}
