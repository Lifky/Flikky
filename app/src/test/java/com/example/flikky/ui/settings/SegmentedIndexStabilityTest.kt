package com.example.flikky.ui.settings

import com.example.flikky.ui.SettingItemShapeGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分段圆角的 `index` / `total` **不许随展开态变化**（2026-09-09 装机反馈）。
 *
 * ## 现象
 *
 * 「导入与导出」从展开缩回时，它与「删除全部数据」之间那条分隔白线的高度会先
 * 不对、卡一下才恢复。**从未展开到展开没有这个问题** —— 只在收起时出现。
 *
 * ## 机制
 *
 * 旧写法是 `val sectionItems = if (importExportExpanded) 9 else 4`。
 *
 * 这个值跟着 flag **瞬间**翻转，而 `AnimatedVisibility` 的收起动画还要跑 ~200ms
 * —— 期间那 5 行仍在组合里，却已经带着越界的 index 在渲染：
 *
 * ```
 * 收起瞬间：content 行 index=3..7，但 total 已经是 4
 *          → index 3 满足 `index == count - 1`，被当成「末行」
 *          → 突然拿到下圆角，位置正好在两个 listitem 的交界处
 * ```
 *
 * 「会话行为」区更糟：`followingIndexOffset` 同时翻转，后面 5 行的 index
 * 全体位移一位，收起动画期间出现**重复 index**。
 *
 * 展开方向没有这个现象，因为 flag 先变 true、`total` 先变大，
 * 那几行是带着正确的 index 出现的。**这正是缺陷只在收起时出现的原因**，
 * 也是本守卫盯「不随展开态变化」而不是盯某个具体数值的理由。
 *
 * ## 为什么固定上界是安全的
 *
 * `ListItemDefaults.segmentedShapes` 只区分首行（`index == 0`）、
 * 末行（`index == count - 1`）与中间行。收起后可见的 index 有断档
 * （数据区少了 3..7）也不影响任何一行的圆角判定。
 *
 * ## 为什么是源码扫描
 *
 * 「动画中途第 N 帧的圆角是多少」要驱动 Compose 动画时钟并逐帧取形状 ——
 * 那是仪器测试的活。而这个缺陷的**判据是结构性的**（分段算术不得依赖动画态），
 * 结构用源码钉住，成本与收益都合适。与 [SettingExpandableGapTest] 同一取舍。
 */
class SegmentedIndexStabilityTest {

    private val screen by lazy {
        SettingItemShapeGuard.readSource("ui/settings/SettingsScreen.kt")
    }

    /** 驱动可展开区的那些 flag —— 分段算术碰到它们任何一个都是缺陷。 */
    private val expandFlags = listOf("importExportExpanded", "recallBetaEnabled")

    @Test
    fun `the screen still declares its sections this way`() {
        // 防切片失效：哪天分区写法变了，下面几条会在零个匹配上通过。
        // 普通字符串而不是原始字符串：`"""… = """"` 里的第 4 个引号会被 Kotlin
        // 当成内容（原始字符串在「后面没有跟引号的第一个三引号」处结束），
        // 正则就变成了 `val sectionItems = "`，永不匹配 —— 写这条时踩到了。
        val decls = Regex("val sectionItems = ").findAll(screen).count()
        assertTrue(
            "SettingsScreen 里 `val sectionItems = ` 只找到 $decls 处 —— 分区写法变了，先修切片再谈守卫",
            decls >= 6,
        )
        assertTrue(
            "SettingsScreen 里已经没有 SettingExpandableGroup —— 请重新评估本守卫",
            screen.contains("SettingExpandableGroup("),
        )
    }

    @Test
    fun `no section total depends on an expand flag`() {
        val offenders = Regex("""val sectionItems = ([^\r\n]+)""")
            .findAll(screen)
            .map { it.groupValues[1].trim() }
            .filter { expr -> expandFlags.any { expr.contains(it) } }
            .toList()
        assertEquals(
            "这些 `sectionItems` 跟着展开态变化 —— 收起动画期间那几行会带着越界 index 渲染，" +
                "交界处的圆角跳一下（用户看到的「白线卡一下」）。请改用固定上界：",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no row index depends on an expand flag`() {
        // 直接依赖：`index = if (expanded) 4 else 3`
        val direct = Regex("""index = ([^,\r\n]+)""")
            .findAll(screen)
            .map { it.groupValues[1].trim() }
            .filter { expr -> expandFlags.any { expr.contains(it) } }
            .toList()
        assertEquals("这些 index 直接依赖展开态：", emptyList<String>(), direct)

        // 间接依赖：index 里引用的偏移变量本身跟着 flag 变。
        // 旧代码就是这一种（`followingIndexOffset`），而且比直接依赖更糟 ——
        // 它让后面所有行的 index 一起位移，收起期间出现重复 index。
        val indirect = Regex("""val (\w*(?:Offset|Index)\w*) = ([^\r\n]+)""")
            .findAll(screen)
            .filter { m -> expandFlags.any { m.groupValues[2].contains(it) } }
            .map { it.groupValues[1] }
            .filter { name -> Regex("""index = [^,\r\n]*\b$name\b""").containsMatchIn(screen) }
            .toList()
        assertEquals(
            "这些偏移变量跟着展开态变化，又被 index 引用 —— " +
                "收起动画期间后续行的 index 会整体位移、出现重复：",
            emptyList<String>(),
            indirect,
        )
    }
}
