package com.example.flikky.ui.settings

import com.example.flikky.ui.SettingItemShapeGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 可展开设置行**收起时**的间隔守卫（2026-09-08 装机反馈）。
 *
 * ## 现象
 *
 * 「导入与导出」从展开缩回时，它与下一行之间的间隔会先大一截、卡一下才恢复正常
 * （Screenshot_11 vs Screenshot_12）。
 *
 * ## 机制
 *
 * `AnimatedVisibility` 是 `Column(verticalArrangement = spacedBy(SegmentedGap))`
 * 的**一个子项**。收起动画期间它的高度趋近 0，但**仍然占着一个子项位** ——
 * 于是父 Column 在它前后各放一个 gap：
 *
 * ```
 * 表头 |gap| AnimatedVisibility(高≈0) |gap| 下一行     ← 两倍间隔
 * ```
 *
 * 动画结束、节点被移除之后才变回：
 *
 * ```
 * 表头 |gap| 下一行                                    ← 一倍间隔
 * ```
 *
 * 「卡一下才正常」就是这一下从两倍跳回一倍。
 *
 * ## 修法
 *
 * 把「表头 + 展开区」包成父 Column 的**单一子项**，gap 移到 `AnimatedVisibility`
 * 内部（跟着内容一起被 shrinkVertically 裁掉）。这样收起态的高度恰好等于表头高度，
 * 父 Column 前后各一个 gap 也就是正确的一倍。
 *
 * ## 为什么是源码扫描
 *
 * 「间隔在动画中途是几倍」要测就得驱动 Compose 的动画时钟并逐帧量布局 ——
 * 那是仪器测试的活，而这个缺陷的**判据是结构性的**（AnimatedVisibility 不能
 * 直接坐在 spacedBy 的 Column 里）。结构用源码钉住，成本与收益都合适。
 */
class SettingExpandableGapTest {

    private val screen by lazy {
        SettingItemShapeGuard.readSource("ui/settings/SettingsScreen.kt")
    }

    private val group by lazy {
        SettingItemShapeGuard.readSource("ui/settings/components/SettingExpandableGroup.kt")
    }

    @Test
    fun `settings screen never puts AnimatedVisibility straight into the spaced column`() {
        // SettingSection 的内层 Column 用的就是 spacedBy(SegmentedGap)，
        // 所以 SettingsScreen 里任何一处裸的 AnimatedVisibility 都会重犯这个缺陷。
        val bare = Regex("""^\s{20}AnimatedVisibility\(""", RegexOption.MULTILINE)
            .findAll(screen).count()
        assertEquals(
            "SettingsScreen 里又出现了直接坐在 SettingSection 的 spacedBy Column 里的 " +
                "AnimatedVisibility —— 收起动画期间会是两倍间隔。请改用 SettingExpandableGroup。",
            0,
            bare,
        )
    }

    @Test
    fun `both expandable rows go through the shared group`() {
        // 两处：撤回 beta 的「允许对端撤回」、以及「导入与导出」那四行。
        val uses = Regex("""SettingExpandableGroup\(""").findAll(screen).count()
        assertTrue(
            "期望两处可展开区都走共用组件，实际找到 $uses 处",
            uses >= 2,
        )
    }

    @Test
    fun `the group makes the header and the expandable area one single slot`() {
        // 关键结构：表头与 AnimatedVisibility 同在一个 Column 里 ——
        // 于是对父 Column 只算一个子项，前后各一个 gap 就是正确的一倍。
        // **只看函数体**：KDoc 里那张示意图也写着 `AnimatedVisibility(高≈0)`，
        // 扫整份文件会把注释当成第一处（写这条时就踩到了 —— 与
        // 「注释里的字面量也会被源码守卫扫到」同一族）。
        val body0 = group.substring(group.indexOf("fun SettingExpandableGroup("))
        val header = body0.indexOf("header()")
        val av = body0.indexOf("AnimatedVisibility(")
        assertTrue("共用组件里找不到 header() 调用", header > 0)
        assertTrue("共用组件里找不到 AnimatedVisibility", av > 0)
        assertTrue("表头必须在 AnimatedVisibility 之前", header < av)

        // 两者之间不许再插一层带 arrangement 间距的容器：那等于把 gap 放回外面。
        val between = body0.substring(header, av)
        assertTrue(
            "表头与展开区之间多了一层容器或间距，gap 又回到了 AnimatedVisibility 外面：$between",
            !between.contains("verticalArrangement") && !between.contains("Spacer("),
        )

        // 外层 Column 本身也不许有 arrangement 间距。
        val outerAt = body0.indexOf("Column(")
        assertTrue("共用组件外层不是 Column", outerAt in 1 until header)
        val outer = body0.substring(outerAt, header)
        assertTrue(
            "外层 Column 设了 verticalArrangement —— 表头与展开区之间会永久多一个 gap：$outer",
            !outer.contains("verticalArrangement"),
        )
    }

    @Test
    fun `the gap lives inside the animated content so it shrinks away`() {
        // gap 写在 AnimatedVisibility 的内容上（padding(top = SegmentedGap)），
        // shrinkVertically 裁内容高度时把它一起裁掉。写在外面就等于永久多一个 gap。
        val at = group.indexOf("AnimatedVisibility(")
        assertTrue("共用组件里没有 AnimatedVisibility", at > 0)
        val body = group.substring(at)
        assertTrue(
            "AnimatedVisibility 的内容没有把 SegmentedGap 作为顶部内边距 —— " +
                "gap 留在外面就不会随收起消失，缺陷照旧",
            Regex("""padding\(top = ListItemDefaults\.SegmentedGap\)""").containsMatchIn(body),
        )
    }

    @Test
    fun `the group keeps the official expand and collapse motion`() {
        // 动效规格照搬原来那两处，逐字相同 —— 这次修的是布局，不是动效。
        //
        // **只扫函数体**：这几个名字在 import 行里也出现，扫整份文件时把
        // exit 整个删掉都不会红（逼红实测零条红）。这是本会话第三次踩
        // 「源码守卫扫到了注释/import」这一族。
        val body = group.substring(group.indexOf("fun SettingExpandableGroup("))
        listOf("expandVertically", "shrinkVertically", "fadeIn", "fadeOut").forEach {
            assertTrue("共用组件丢了 $it，动效与原来不一致", body.contains(it))
        }
    }
}
