package com.example.flikky.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **收起到零的动画不许用带回弹的 spec**（2026-09-10 装机反馈）。
 *
 * ## 现象与那条关键对比
 *
 * 「导入与导出」收起时，它与「删除全部数据」之间那条分隔白线的高度会闪一下。
 * 而「允许对端撤回」收起时**没有**这个现象 —— 两处用的是同一个
 * `SettingExpandableGroup`、同一条弹簧。
 *
 * 这个对比就是根因的指纹。
 *
 * ## 机制
 *
 * 官方 MotionScheme 的 spatial 系列**刻意欠阻尼**（反射实测：expressive 方案
 * `fastSpatial` 阻尼比 `0.6`、`defaultSpatial` / `slowSpatial` `0.8`；
 * effects 系列才是 `1.0`）。那是「有质量的东西在动」的手感，进场时正是要它。
 *
 * 但收起的目标值是 **0**：欠阻尼弹簧先冲过头到负值（被钳在 0），再弹回几个 dp
 * 才落定。ζ=0.6 的二次过冲约 0.9% —— **幅度按内容高度等比放大**：
 *
 * ```
 * 允许对端撤回：展开区 1 行 ≈ 72dp   → 回弹 <1dp  看不出
 * 导入与导出：  展开区 5 行 ≈ 380dp  → 回弹 3~4dp 看得出
 * ```
 *
 * 所以「一个跳、一个不跳」不是两个不同的缺陷，是同一条弹簧在两种内容高度下的
 * 两种可见度。
 *
 * ## 规则
 *
 * 进场保留官方回弹（展开有弹性是对的）；**收起一律用
 * `Motion.spatialFastNoBounce()`**（临界阻尼，刚度仍是官方值）。
 *
 * ## 为什么是源码扫描
 *
 * 要测「动画第 N 帧的高度有没有超过目标值」得驱动 Compose 动画时钟并逐帧取布局，
 * 那是仪器测试的活；而这个缺陷的判据是结构性的（收起不得引用回弹 spec）。
 * 与 `SettingExpandableGapTest` / `SegmentedIndexStabilityTest` 同一取舍。
 */
class CollapseMotionTest {

    /** 带回弹的访问器 —— 阻尼比 < 1，收到 0 时会弹回来。 */
    private val bouncy = listOf("spatial", "spatialFast", "spatialSlow")

    private fun uiSources(): List<Pair<String, String>> {
        val root = File("src").takeIf { it.isDirectory } ?: File("app/src")
        val dir = root.resolve("main/java/com/example/flikky/ui")
        return dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // 剥注释：本文件与被扫文件的注释里都逐字写着 `shrinkVertically(Motion.spatialFast())`
            // 当反例说明。不剥的话「有没有用回弹 spec」这条会匹配到注释 ——
            // 把真正的调用改回去都不会红（本项目已经踩过四次这一族）。
            .map { it.name to stripComments(it.readText()) }
            .toList()
    }

    private fun stripComments(src: String): String = src
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""(?m)^\s*//.*$"""), "")
        .replace(Regex("""(?m)\s//.*$"""), "")

    @Test
    fun `there are collapse animations to check`() {
        // 防切片失效：写法一变，下面那条会在零个匹配上通过。
        val n = uiSources().sumOf { (_, src) ->
            Regex("""shrink(?:Vertically|Horizontally|Out)\(""").findAll(src).count()
        }
        assertTrue("一处 shrink 动画都没扫到 —— 写法变了？先修切片再谈守卫", n > 0)
    }

    @Test
    fun `no collapse animation uses a bouncy spatial spec`() {
        val offenders = mutableListOf<String>()
        uiSources().forEach { (name, src) ->
            Regex("""shrink(?:Vertically|Horizontally|Out)\(\s*Motion\.(\w+)\(""")
                .findAll(src)
                .forEach { m ->
                    if (m.groupValues[1] in bouncy) offenders += "$name → Motion.${m.groupValues[1]}()"
                }
        }
        assertEquals(
            "这些收起动画用了**带回弹**的 spatial spec（官方阻尼比 0.6~0.8）。" +
                "收到 0 的弹簧会先冲过头再弹回几个 dp，内容越高越明显 —— " +
                "用户看到的是「间隔高度闪一下」。请改用 Motion.spatialFastNoBounce()：",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `entering animations keep the official bounce`() {
        // 反向守卫：别把回弹一刀切掉。展开有弹性是 M3 Expressive 刻意的手感，
        // 本次改的只是「收起」这一半。
        val src = uiSources().first { it.first == "SettingExpandableGroup.kt" }.second
        assertTrue(
            "展开动画不该用不回弹的 spec —— 进场的弹性是 M3 Expressive 要的",
            Regex("""expandVertically\(\s*Motion\.spatial\w*\(""").containsMatchIn(src) &&
                !src.contains("expandVertically(Motion.spatialFastNoBounce()"),
        )
    }
}
