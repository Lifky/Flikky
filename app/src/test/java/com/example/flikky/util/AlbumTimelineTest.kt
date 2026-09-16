package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分组规则。
 *
 * v1.21.0 装机返工后本对象**不再自己算日期**（那是双端不一致的根因，见 [AlbumDateKey]
 * 与 D65），所以这里传的是服务端下发的键。每条断言守的意图与改造前完全相同：
 * 四档标签、午夜边界、倒序、未来日期不消失 —— 只是判据从「传时区」换成「传键」。
 */
class AlbumTimelineTest {

    private val todayKey = "2026-09-15"
    private val yesterdayKey = "2026-09-14"

    private data class Item(val key: String, val at: Long)

    private fun group(vararg items: Item) =
        AlbumTimeline.group(items.toList(), { it.key }, { it.at }, todayKey, yesterdayKey)

    @Test
    fun `items taken today land in the today bucket`() {
        val sections = group(Item("2026-09-15", 900L))

        assertEquals(1, sections.size)
        assertEquals(AlbumDateLabel.Today, sections.single().label)
    }

    @Test
    fun `items taken yesterday land in the yesterday bucket`() {
        assertEquals(AlbumDateLabel.Yesterday, group(Item("2026-09-14", 800L)).single().label)
    }

    @Test
    fun `earlier days this year keep month and day`() {
        assertEquals(
            AlbumDateLabel.SameYear(month = 9, day = 10),
            group(Item("2026-09-10", 700L)).single().label,
        )
    }

    @Test
    fun `previous years keep the year too`() {
        assertEquals(
            AlbumDateLabel.Older(year = 2025, month = 9, day = 10),
            group(Item("2025-09-10", 600L)).single().label,
        )
    }

    @Test
    fun `items on the same day share one section`() {
        val sections = group(
            Item("2026-09-10", 300L),
            Item("2026-09-10", 200L),
            Item("2026-09-10", 100L),
        )

        assertEquals(1, sections.size)
        assertEquals(3, sections.single().items.size)
    }

    @Test
    fun `the day boundary comes from the key, not from a rolling window`() {
        // 改造前这条验的是「本地午夜是边界，不是滚动 24 小时」。现在边界已经由
        // 服务端的键决定，所以这里验的是：**两个相差两分钟但键不同的项必须分属两节**。
        // 同一个意图 —— 分档不看时间差，只看「哪一天」。
        val sections = group(Item("2026-09-15", 1_000L), Item("2026-09-14", 999L))

        assertEquals(2, sections.size)
        assertEquals(AlbumDateLabel.Today, sections[0].label)
        assertEquals(AlbumDateLabel.Yesterday, sections[1].label)
    }

    @Test
    fun `sections run newest first and items inside them too`() {
        val sections = group(
            Item("2026-09-10", 100L),
            Item("2026-09-15", 400L),
            Item("2026-09-14", 200L),
            Item("2026-09-14", 300L),
        )

        assertEquals(
            listOf(AlbumDateLabel.Today, AlbumDateLabel.Yesterday, AlbumDateLabel.SameYear(9, 10)),
            sections.map { it.label },
        )
        val yesterday = sections[1].items
        assertTrue("同一节内也必须新的在前", yesterday[0].at > yesterday[1].at)
    }

    @Test
    fun `unsorted input is sorted by the grouper`() {
        // 调用方不该负责排序：MediaStore 的查询顺序、浏览器的流式到达顺序都可能乱。
        val sections = group(
            Item("2026-09-10", 100L),
            Item("2026-09-15", 300L),
            Item("2025-01-01", 50L),
        )

        assertEquals(
            listOf(AlbumDateLabel.Today, AlbumDateLabel.SameYear(9, 10), AlbumDateLabel.Older(2025, 1, 1)),
            sections.map { it.label },
        )
    }

    @Test
    fun `an empty list yields no sections`() {
        assertEquals(emptyList<AlbumSection<Item>>(), group())
    }

    @Test
    fun `items dated in the future fall into today rather than vanishing`() {
        // 相机时间设错的照片真实存在。它们不该消失，也不该造出一个「未来」分组。
        val sections = group(Item("2027-01-01", 9_999L))

        assertEquals(1, sections.size)
        assertEquals(AlbumDateLabel.Today, sections.single().label)
        assertTrue(sections.single().items.isNotEmpty())
    }

    @Test
    fun `an unparseable key degrades to today instead of breaking the grouping`() {
        // 键经网络到达，坏值不该让整段分组消失或显示 1970 年。
        val sections = group(Item("", 10L), Item("not-a-key", 5L))

        assertTrue(sections.all { it.label == AlbumDateLabel.Today })
        assertEquals(2, sections.sumOf { it.items.size })
    }

    @Test
    fun `labelFor is the single mapping both ends rely on`() {
        // 浏览器端渲染同一批键，走的是同一组档位。把映射单独暴露出来，
        // 是为了让两端的 i18n 各自渲染而分档规则只有一份。
        assertEquals(AlbumDateLabel.Today, AlbumTimeline.labelFor(todayKey, todayKey, yesterdayKey))
        assertEquals(AlbumDateLabel.Yesterday, AlbumTimeline.labelFor(yesterdayKey, todayKey, yesterdayKey))
        assertEquals(
            AlbumDateLabel.SameYear(1, 2),
            AlbumTimeline.labelFor("2026-01-02", todayKey, yesterdayKey),
        )
        assertEquals(
            AlbumDateLabel.Older(2024, 12, 31),
            AlbumTimeline.labelFor("2024-12-31", todayKey, yesterdayKey),
        )
    }
}
