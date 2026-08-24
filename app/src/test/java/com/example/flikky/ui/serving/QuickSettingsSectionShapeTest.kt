package com.example.flikky.ui.serving

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 连接式列表组的圆角靠 `SettingItem(index, total)` 判首尾，所以**每一区手写的 `total`
 * 必须等于该区实际的行数**。
 *
 * v1.19.0 用户截图 29：「会话行为」区的 `total` 是 5，而该区只有 4 行 —— 照搬了设置页的
 * 数字，却没搬它那三行（需要 PIN、允许会话中返回、屏幕常亮）。差一的后果不是少画一行，
 * 而是**最后一行永远拿不到 `index == total - 1`**，于是末行「收藏功能」的底部圆角一直是
 * 中间行的小圆角，与首行「消息操作样式」的顶部大圆角不对称。
 *
 * 这类缺陷肉眼很难发现（差别只有几个 dp），而且每次增删行都可能重犯，所以钉死。
 * 带条件行的区取 `total` 表达式里的**最大值**比较：`if (recall) 4 else 3` 的上界就是行数。
 */
class QuickSettingsSectionShapeTest {

    @Test
    fun `every section's total matches its row count`() {
        val source = sheetSource()
        // 每区都以 `val total = ...` 开头，所以按它切片即可，不必解析花括号。
        val markers = Regex("""val total = ([^\n]+)""").findAll(source).toList()
        assertTrue(
            "QuickSettingsSheet 里没找到任何 `val total =` —— 分区写法变了，先修这个切片",
            markers.size >= 3,
        )

        val mismatches = mutableListOf<String>()
        markers.forEachIndexed { i, m ->
            val sliceEnd = markers.getOrNull(i + 1)?.range?.first ?: source.length
            val slice = source.substring(m.range.last, sliceEnd)
            val rows = Regex("""SettingItem\(""").findAll(slice).count()
            val declared = Regex("""\d+""").findAll(m.groupValues[1]).map { it.value.toInt() }.maxOrNull()
            if (declared != rows) {
                mismatches += "第${i + 1}区: total=${m.groupValues[1].trim()} (上界 $declared) 但有 $rows 行"
            }
        }
        assertEquals(
            "手写的 total 与该区实际行数不符。圆角首尾判定靠 index/total，差一会让末行" +
                "拿不到 index == total - 1，底部圆角变成中间行的小圆角（用户截图 29）。不符：",
            emptyList<String>(),
            mismatches,
        )
    }

    @Test
    fun `the last row of a section can actually reach the last index`() {
        // 上一条比的是数字。这条比的是「末行真的写了能等于 total - 1 的 index」——
        // 条件行那一区的末行 index 也是个表达式（`if (recall) 3 else 2`），
        // 上一条对不上它，所以单独钉。
        val source = sheetSource()
        val markers = Regex("""val total = ([^\n]+)""").findAll(source).toList()
        val bad = mutableListOf<String>()
        markers.forEachIndexed { i, m ->
            val sliceEnd = markers.getOrNull(i + 1)?.range?.first ?: source.length
            val slice = source.substring(m.range.last, sliceEnd)
            val indices = Regex("""index = ([^,]+),""").findAll(slice).toList()
            if (indices.isEmpty()) return@forEachIndexed
            val totalMax = Regex("""\d+""").findAll(m.groupValues[1]).map { it.value.toInt() }.max()
            val maxIndex = indices
                .flatMap { idx -> Regex("""\d+""").findAll(idx.groupValues[1]).map { it.value.toInt() }.toList() }
                .maxOrNull() ?: -1
            if (maxIndex != totalMax - 1) {
                bad += "第${i + 1}区: 最大 index=$maxIndex，但 total 上界=$totalMax（应为 ${totalMax - 1}）"
            }
        }
        assertEquals(
            "某一区最大的 index 不等于 total - 1，说明末行拿不到「最后一行」的圆角。不符：",
            emptyList<String>(),
            bad,
        )
    }

    private fun sheetSource(): String {
        val root = File("src").takeIf { it.isDirectory } ?: File("app/src")
        return root.resolve("main/java/com/example/flikky/ui/serving/QuickSettingsSheet.kt").readText()
    }
}
