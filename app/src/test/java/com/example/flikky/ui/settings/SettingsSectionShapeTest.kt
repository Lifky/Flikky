package com.example.flikky.ui.settings

import com.example.flikky.ui.SettingItemShapeGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正式设置页的分段列表组圆角守卫，判据与快捷设置同源（[SettingItemShapeGuard]）。
 *
 * **这个文件是 2026-08-29 补的洞**：v1.19.0 修 Screenshot_29 时只给快捷设置加了守卫，
 * 正式设置页的 `index` / `sectionItems` 算术一直没人钉。当天给「会话行为」区加
 * 「允许电脑浏览手机存储」一行时做逼红验证，把 `sectionItems` 改回旧值 `7 else 6`
 * —— **一条测试都没红**。同一类缺陷在这个文件里可以毫无阻力地重犯。
 */
class SettingsSectionShapeTest {

    private fun sections() = SettingItemShapeGuard.analyze(
        SettingItemShapeGuard.readSource("ui/settings/SettingsScreen.kt"),
        totalMarker = "sectionItems",
    )

    @Test
    fun `every section's sectionItems matches its row count`() {
        val sections = sections()
        assertTrue(
            "SettingsScreen 里没找到足够的 `val sectionItems =` —— 分区写法变了，先修切片",
            sections.size >= 5,
        )
        val mismatches = sections
            .filter { it.declaredMax != it.rowCount }
            .map { "第${it.ordinal}区: sectionItems=${it.totalExpr} (上界 ${it.declaredMax}) 但有 ${it.rowCount} 行" }
        assertEquals(
            "手写的 sectionItems 与该区实际行数不符。圆角首尾判定靠 index/total，" +
                "差一会让末行拿不到 index == total - 1，底部圆角退化成中间行的小圆角。不符：",
            emptyList<String>(),
            mismatches,
        )
    }

    @Test
    fun `the last row of every section can reach the last index`() {
        val bad = sections()
            .filter { it.maxIndex != it.declaredMax - 1 }
            .map { "第${it.ordinal}区: 最大 index=${it.maxIndex}，但上界=${it.declaredMax}（应为 ${it.declaredMax - 1}）" }
        assertEquals(
            "某一区最大的 index 不等于 sectionItems - 1，末行拿不到「最后一行」的圆角。不符：",
            emptyList<String>(),
            bad,
        )
    }

    @Test
    fun `every row's index is actually seen by the parser`() {
        // 交叉校验：抓到的 index 条数必须等于 SettingItem 行数。多了说明正则抓到了
        // 无关的东西，少了说明某行没被看见——而那种情况下上面两条断言照样全绿。
        val off = sections()
            .filter { it.indexCount != it.rowCount }
            .map { "第${it.ordinal}区: 抓到 ${it.indexCount} 个 index，但有 ${it.rowCount} 行" }
        assertEquals("index 抓取数与行数不符，切片或正则需要修：", emptyList<String>(), off)
    }

    @Test
    fun `no index expression is silently skipped`() {
        // 解析器只认三种真实出现过的写法。冒出第四种时必须报错而不是跳过——
        // 跳过会让上面两条断言在一个「没看见的行」上照样全绿。
        val unparsed = sections()
            .filter { it.unparsedIndices.isNotEmpty() }
            .map { "第${it.ordinal}区: ${it.unparsedIndices}" }
        assertEquals(
            "出现了 SettingItemShapeGuard.resolve 不认识的 index 表达式。" +
                "去那里加一条分支，不要让它被跳过：",
            emptyList<String>(),
            unparsed,
        )
    }
}
