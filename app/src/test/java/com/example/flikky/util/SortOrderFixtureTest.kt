package com.example.flikky.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跨端共同 oracle 的 Kotlin 侧。JS 侧是 `app/src/test/web/sort-order-fixture.test.js`，
 * 读的是**同一个文件**。
 *
 * 为什么需要它：[StorageListingPolicy] 与 `assets/web/sort.js` 是同一套语义的两份实现。
 * 两端各写各的测试时，「顺序不一致」这种缺陷两边都是绿的（backlog B26 描述的正是这个）。
 * 共享 fixture 让两份实现有同一个基准。
 *
 * **加排序键时必须同步扩这份 fixture**，否则新键的跨端一致性没有任何保护。
 */
class SortOrderFixtureTest {

    private data class Row(val name: String, val isDir: Boolean, val size: Long, val mtime: Long)

    private val fixture by lazy {
        val text = checkNotNull(javaClass.getResourceAsStream("/sort-order.json")) {
            "sort-order.json 不在测试 classpath 上"
        }.bufferedReader().use { it.readText() }
        Json.parseToJsonElement(text).jsonObject
    }

    private val rows by lazy {
        fixture["entries"]!!.jsonArray.map {
            val o = it.jsonObject
            Row(
                name = o["name"]!!.jsonPrimitive.content,
                isDir = o["isDir"]!!.jsonPrimitive.content.toBoolean(),
                size = o["size"]!!.jsonPrimitive.content.toLong(),
                mtime = o["mtime"]!!.jsonPrimitive.content.toLong(),
            )
        }
    }

    @Test
    fun `every combination in the fixture matches filterAndSort`() {
        val expected = fixture["expected"]!!.jsonObject
        assertEquals("fixture 必须覆盖 3 个键 × 2 个方向", 6, expected.size)
        for ((raw, names) in expected) {
            val spec = checkNotNull(SortSpec.parse(raw)) { "fixture 里的键名 $raw 解析不了" }
            val actual = StorageListingPolicy.filterAndSort(
                rows,
                { it.isDir },
                { it.name },
                false,
                { it.size },
                { it.mtime },
                spec,
            ).map { it.name }
            assertEquals(
                "$raw 的顺序与 fixture 不一致",
                names.jsonArray.map { it.jsonPrimitive.content },
                actual,
            )
        }
    }

    @Test
    fun `the six expected orders are pairwise distinct`() {
        // 两个判据在现有用例上等价 = 其中一个坏掉也测不出来。
        // 这条断言保证 fixture 本身有区分力。
        val orders = fixture["expected"]!!.jsonObject.values.map { it.toString() }
        assertEquals(
            "有两个期望顺序完全相同，fixture 失去区分力",
            orders.size,
            orders.toSet().size,
        )
    }

    @Test
    fun `hidden entries never appear in any expected order`() {
        val hidden = rows.filter { StorageListingPolicy.isHidden(it.name) }.map { it.name }
        assertTrue("fixture 里应当有隐藏项，否则过滤这一层没被覆盖", hidden.isNotEmpty())
        fixture["expected"]!!.jsonObject.forEach { (raw, names) ->
            val listed = names.jsonArray.map { it.jsonPrimitive.content }
            hidden.forEach { assertTrue("$raw 里不该出现隐藏项 $it", it !in listed) }
        }
    }
}
