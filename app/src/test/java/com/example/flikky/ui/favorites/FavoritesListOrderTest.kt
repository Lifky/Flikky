package com.example.flikky.ui.favorites

import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.util.SortKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.example.flikky.util.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesListOrderTest {

    private fun file(id: Long, name: String?, size: Long?, at: Long) = FavoriteEntity(
        id = id,
        sourceSessionId = 1,
        sourceMessageId = id,
        kind = "FILE",
        fileName = name,
        fileSize = size,
        createdAt = at,
    )

    private fun text(id: Long, body: String, at: Long) = FavoriteEntity(
        id = id,
        sourceSessionId = 1,
        sourceMessageId = id,
        kind = "TEXT",
        textContent = body,
        createdAt = at,
    )

    @Test
    fun `name order uses the raw stored value, not the localised placeholder`() {
        // 空名回落到 favorites_unnamed_file 这类本地化占位串；按它排会让
        // **切换语言改变排序**。所以排序键是原始值，缺失即空串（排最前）。
        val items = listOf(
            file(1, "beta.apk", 10, 100),
            text(2, "alpha 片段", 200),
            file(3, null, 10, 300),
        )
        val sorted = FavoritesListOrder.sort(items, SortSpec(SortKey.NAME, descending = false))
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `time means createdAt, the moment it was saved`() {
        // 不是原消息的时间戳 —— 「按时间」在收藏页指的是「什么时候存进弹药库的」。
        val items = listOf(file(1, "a", 1, 300), file(2, "b", 1, 100), file(3, "c", 1, 200))
        assertEquals(
            listOf(1L, 3L, 2L),
            FavoritesListOrder.sort(items, SortSpec(SortKey.TIME, descending = true)).map { it.id },
        )
    }

    @Test
    fun `items with no file sort last by size in both directions`() {
        // 「没有大小」不是「大小为 0」—— 与 StorageListingPolicy.visibleCount
        // 那条「null（读不到）与 0 是两件事」同一个道理。
        // 升序时把一堆文本收藏顶到最前面，对用户没有任何意义。
        val items = listOf(file(1, "big", 900, 100), text(2, "无大小", 200), file(3, "zero", 0, 300))

        assertEquals(
            listOf(1L, 3L, 2L),
            FavoritesListOrder.sort(items, SortSpec(SortKey.SIZE, descending = true)).map { it.id },
        )
        assertEquals(
            listOf(3L, 1L, 2L),
            FavoritesListOrder.sort(items, SortSpec(SortKey.SIZE, descending = false)).map { it.id },
        )
    }

    @Test
    fun `equal keys fall back to name so the order is stable`() {
        val items = listOf(file(1, "b", 5, 100), file(2, "A", 5, 100), file(3, "a", 5, 100))
        assertEquals(
            listOf(2L, 3L, 1L),
            FavoritesListOrder.sort(items, SortSpec(SortKey.SIZE, descending = true)).map { it.id },
        )
    }

    @Test
    fun `every combination in the shared fixture matches`() {
        // 跨端共同 oracle：JS 侧 `panel-favorites-sort.test.js` 读**同一份文件**。
        // 收藏排序也是同一套语义的两份实现，与文件浏览一样的分叉风险。
        val text = checkNotNull(javaClass.getResourceAsStream("/sort-order.json")) {
            "sort-order.json 不在测试 classpath 上"
        }.bufferedReader().use { it.readText() }
        val fav = Json.parseToJsonElement(text).jsonObject["favorites"]!!.jsonObject

        val items = fav["items"]!!.jsonArray.map {
            val o = it.jsonObject
            val id = o["id"]!!.jsonPrimitive.content.toLong()
            val name = o["name"]!!.jsonPrimitive.content
            val size = o["size"]!!.jsonPrimitive.contentOrNull?.toLongOrNull()
            val at = o["createdAt"]!!.jsonPrimitive.content.toLong()
            if (o["kind"]!!.jsonPrimitive.content == "FILE") {
                file(id, name, size, at)
            } else {
                text(id, name, at)
            }
        }

        val expected = fav["expected"]!!.jsonObject
        assertEquals("fixture 必须覆盖 3 个键 × 2 个方向", 6, expected.size)
        for ((raw, ids) in expected) {
            val spec = checkNotNull(SortSpec.parse(raw)) { "fixture 里的键名 $raw 解析不了" }
            assertEquals(
                "$raw 的顺序与 fixture 不一致",
                ids.jsonArray.map { it.jsonPrimitive.content.toLong() },
                FavoritesListOrder.sort(items, spec).map { it.id },
            )
        }
    }

    @Test
    fun `the name tie-break stays ascending even when the key is descending`() {
        // 兜底跟着方向翻转的话，同大小的两项在降序下会反过来 —— 那让「同一批
        // 收藏在两个方向下的相对顺序」变得不可预测。与 filterAndSort 同一裁决。
        val items = listOf(file(1, "b", 5, 100), file(2, "a", 5, 100))
        assertEquals(
            listOf(2L, 1L),
            FavoritesListOrder.sort(items, SortSpec(SortKey.SIZE, descending = true)).map { it.id },
        )
        assertEquals(
            listOf(2L, 1L),
            FavoritesListOrder.sort(items, SortSpec(SortKey.SIZE, descending = false)).map { it.id },
        )
    }
}
