package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private val TODAY: LocalDate = LocalDate.of(2026, 6, 20)

private fun ms(date: LocalDate): Long =
    date.atStartOfDay(ZONE).toInstant().toEpochMilli() + 3_600_000L

private fun session(
    id: Long,
    name: String,
    startedAt: Long,
    ended: Boolean = true,
    pinned: Boolean = false,
    groupId: Long? = null,
) = SessionEntity(
    id = id,
    startedAt = startedAt,
    endedAt = if (ended) startedAt + 1000 else null,
    name = name,
    pinned = pinned,
    groupId = groupId,
)

class HomeListBuilderTest {

    /*
     * 方向断言（2026-09-08 装机反馈补）。
     *
     * 此前这一组一条都没有：`build` 只收 `SortKey`，comparator 里方向是**写死的**
     * （TIME 恒降序、NAME 恒升序）。于是第二次点同一个键时，设置里的方向翻转了、
     * 菜单里的箭头也翻了，**列表顺序却不动** —— 用户报的就是这个。
     *
     * 教训：给一个既有函数加「可配置的维度」时，光把新类型传进去不够，
     * 要有一条断言证明**那个维度真的被读了**。
     */

    @Test
    fun `time direction is honoured, not hard-coded to descending`() {
        val old = session(1, "old", ms(TODAY.minusDays(3)))
        val new = session(2, "new", ms(TODAY))

        val desc = HomeListBuilder.build(
            listOf(old, new), SortSpec(SortKey.TIME, descending = true),
            GroupMode.NONE, TODAY, ZONE,
        )
        val asc = HomeListBuilder.build(
            listOf(old, new), SortSpec(SortKey.TIME, descending = false),
            GroupMode.NONE, TODAY, ZONE,
        )

        assertEquals("降序：新的在前", listOf(2L, 1L), desc.sessionIds())
        assertEquals("升序：旧的在前", listOf(1L, 2L), asc.sessionIds())
    }

    @Test
    fun `name direction is honoured, not hard-coded to ascending`() {
        val a = session(1, "alpha", ms(TODAY))
        val z = session(2, "zeta", ms(TODAY))

        val asc = HomeListBuilder.build(
            listOf(z, a), SortSpec(SortKey.NAME, descending = false),
            GroupMode.NONE, TODAY, ZONE,
        )
        val desc = HomeListBuilder.build(
            listOf(a, z), SortSpec(SortKey.NAME, descending = true),
            GroupMode.NONE, TODAY, ZONE,
        )

        assertEquals(listOf(1L, 2L), asc.sessionIds())
        assertEquals(listOf(2L, 1L), desc.sessionIds())
    }

    @Test
    fun `direction applies inside every section, not just the flat list`() {
        // 分节模式走的是另一条分支（section() 里各自 sorted()）——
        // 只在 NONE 上验方向，分节分支坏掉照样绿。
        val t1 = session(1, "a", ms(TODAY))
        val t2 = session(2, "b", ms(TODAY) + 5_000)

        val asc = HomeListBuilder.build(
            listOf(t2, t1), SortSpec(SortKey.TIME, descending = false),
            GroupMode.DATE, TODAY, ZONE,
        )
        assertEquals(listOf(1L, 2L), asc.sessionIds())
    }

    @Test
    fun `direction applies to the pinned run as well as the rest`() {
        // GroupMode.NONE 分两段排（置顶 + 其余）。只改一段就会出现
        // 「置顶那几个方向没跟着变」——两段必须用同一个 comparator。
        val p1 = session(1, "p-old", ms(TODAY.minusDays(3)), pinned = true)
        val p2 = session(2, "p-new", ms(TODAY), pinned = true)
        val r1 = session(3, "r-old", ms(TODAY.minusDays(2)))
        val r2 = session(4, "r-new", ms(TODAY))

        val asc = HomeListBuilder.build(
            listOf(p2, p1, r2, r1), SortSpec(SortKey.TIME, descending = false),
            GroupMode.NONE, TODAY, ZONE,
        )
        assertEquals("置顶两个与其余两个都要按升序", listOf(1L, 2L, 3L, 4L), asc.sessionIds())
    }

    @Test
    fun none_pinnedFirst_then_byTime_desc() {
        val a = session(1, "A", ms(TODAY.minusDays(3)))
        val b = session(2, "B", ms(TODAY))
        val p = session(3, "P", ms(TODAY.minusDays(5)), pinned = true)

        val out = HomeListBuilder.build(listOf(a, b, p), SortSpec.natural(SortKey.TIME), GroupMode.NONE, TODAY, ZONE)

        assertEquals(listOf(3L, 2L, 1L), out.sessionIds())
        assertEquals(emptyList<HomeSection>(), out.headers())
    }

    @Test
    fun none_byName_asc_caseInsensitive() {
        val a = session(1, "banana", ms(TODAY))
        val b = session(2, "Apple", ms(TODAY))

        val out = HomeListBuilder.build(listOf(a, b), SortSpec.natural(SortKey.NAME), GroupMode.NONE, TODAY, ZONE)

        assertEquals(listOf(2L, 1L), out.sessionIds())
    }

    @Test
    fun none_byName_sameNameDifferentCaseHasADeterministicOrder() {
        // 主页的名称比较器必须是共享的 NAME_ORDER，不是 String.CASE_INSENSITIVE_ORDER。
        //
        // 两者的区别**不在**大小写敏感性（那两个都不敏感），而在同名兜底：
        // NAME_ORDER 有 `.thenBy { it }`，所以 "Session"(S=83) 恒在 "session"(115) 前；
        // CASE_INSENSITIVE_ORDER 对这两个返回 0，顺序只能靠排序稳定性 ——
        // 也就是**跟着数据库返回顺序变**。
        //
        // 逼红实测：换回 CASE_INSENSITIVE_ORDER 时这一条红（倒序输入下得到相反结果）。
        val lower = session(1, "session", ms(TODAY))
        val upper = session(2, "Session", ms(TODAY))

        val out = HomeListBuilder.build(listOf(lower, upper), SortSpec.natural(SortKey.NAME), GroupMode.NONE, TODAY, ZONE)

        assertEquals(listOf(2L, 1L), out.sessionIds())
    }

    @Test
    fun status_groups_running_pinned_ended_without_empty_headers() {
        val running = session(1, "Running", ms(TODAY), ended = false)
        val pinned = session(2, "Pinned", ms(TODAY.minusDays(1)), pinned = true)
        val ended = session(3, "Ended", ms(TODAY.minusDays(2)))

        val out = HomeListBuilder.build(
            listOf(ended, pinned, running),
            SortSpec.natural(SortKey.TIME),
            GroupMode.STATUS,
            TODAY,
            ZONE,
        )

        assertEquals(listOf(HomeSection.RUNNING, HomeSection.PINNED, HomeSection.ENDED), out.headers())
        assertEquals(listOf(1L, 2L, 3L), out.sessionIds())
    }

    @Test
    fun status_omits_empty_groups() {
        val ended = session(1, "Ended", ms(TODAY))

        val out = HomeListBuilder.build(listOf(ended), SortSpec.natural(SortKey.TIME), GroupMode.STATUS, TODAY, ZONE)

        assertEquals(listOf(HomeSection.ENDED), out.headers())
        assertEquals(listOf(1L), out.sessionIds())
    }

    @Test
    fun date_groups_pinned_today_yesterday_earlier() {
        val pinned = session(1, "Pinned", ms(TODAY.minusDays(5)), pinned = true)
        val today = session(2, "Today", ms(TODAY))
        val yesterday = session(3, "Yesterday", ms(TODAY.minusDays(1)))
        val earlier = session(4, "Earlier", ms(TODAY.minusDays(2)))

        val out = HomeListBuilder.build(
            listOf(earlier, yesterday, today, pinned),
            SortSpec.natural(SortKey.TIME),
            GroupMode.DATE,
            TODAY,
            ZONE,
        )

        assertEquals(
            listOf(HomeSection.PINNED, HomeSection.TODAY, HomeSection.YESTERDAY, HomeSection.EARLIER),
            out.headers(),
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), out.sessionIds())
    }

    @Test
    fun date_group_places_running_by_start_date() {
        val runningYesterday = session(1, "Running", ms(TODAY.minusDays(1)), ended = false)

        val out = HomeListBuilder.build(
            listOf(runningYesterday),
            SortSpec.natural(SortKey.TIME),
            GroupMode.DATE,
            TODAY,
            ZONE,
        )

        assertEquals(listOf(HomeSection.YESTERDAY), out.headers())
        assertEquals(listOf(1L), out.sessionIds())
    }

    @Test
    fun sort_applies_inside_each_group() {
        val z = session(1, "zeta", ms(TODAY))
        val a = session(2, "Alpha", ms(TODAY.minusDays(1)))
        val p2 = session(3, "beta", ms(TODAY.minusDays(2)), pinned = true)
        val p1 = session(4, "Apple", ms(TODAY.minusDays(3)), pinned = true)

        val out = HomeListBuilder.build(listOf(z, a, p2, p1), SortSpec.natural(SortKey.NAME), GroupMode.DATE, TODAY, ZONE)

        assertEquals(listOf(HomeSection.PINNED, HomeSection.TODAY, HomeSection.YESTERDAY), out.headers())
        assertEquals(listOf(4L, 3L, 1L, 2L), out.sessionIds())
    }

    @Test
    fun empty_list_has_no_headers() {
        val out = HomeListBuilder.build(emptyList(), SortSpec.natural(SortKey.TIME), GroupMode.DATE, TODAY, ZONE)

        assertEquals(emptyList<HomeSection>(), out.headers())
        assertEquals(emptyList<Long>(), out.sessionIds())
    }

    @Test
    fun filterByGroup_nullActiveGroup_returnsAllSessions() {
        val ungrouped = session(1, "Ungrouped", ms(TODAY))
        val grouped = session(2, "Grouped", ms(TODAY), groupId = 7L)

        val out = HomeListBuilder.filterByGroup(listOf(ungrouped, grouped), activeGroupId = null)

        assertEquals(listOf(1L, 2L), out.map { it.id })
    }

    @Test
    fun filterByGroup_activeGroup_returnsOnlyMatchingSessions() {
        val a = session(1, "A", ms(TODAY), groupId = 7L)
        val b = session(2, "B", ms(TODAY), groupId = 8L)
        val ungrouped = session(3, "Ungrouped", ms(TODAY))

        val out = HomeListBuilder.filterByGroup(listOf(a, b, ungrouped), activeGroupId = 7L)

        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test
    fun filterByGroup_activeGroupWithoutMembers_returnsEmptyList() {
        val ungrouped = session(1, "Ungrouped", ms(TODAY))
        val other = session(2, "Other", ms(TODAY), groupId = 8L)

        val out = HomeListBuilder.filterByGroup(listOf(ungrouped, other), activeGroupId = 7L)

        assertEquals(emptyList<Long>(), out.map { it.id })
    }

    @Test
    fun segmentPositions_singleSection_runsFirstMiddleLast() {
        val items = listOf(
            HomeListItem.Header(HomeSection.TODAY),
            HomeListItem.SessionItem(session(1, "A", ms(TODAY))),
            HomeListItem.SessionItem(session(2, "B", ms(TODAY))),
            HomeListItem.SessionItem(session(3, "C", ms(TODAY))),
        )

        // Header -> (0,0); the three SessionItems form one run of size 3.
        assertEquals(
            listOf(0 to 0, 0 to 3, 1 to 3, 2 to 3),
            HomeListBuilder.segmentPositions(items),
        )
    }

    @Test
    fun segmentPositions_noHeaders_oneContinuousRun() {
        val items = listOf(
            HomeListItem.SessionItem(session(1, "A", ms(TODAY))),
            HomeListItem.SessionItem(session(2, "B", ms(TODAY))),
        )

        assertEquals(listOf(0 to 2, 1 to 2), HomeListBuilder.segmentPositions(items))
    }

    @Test
    fun segmentPositions_headerResetsRun() {
        val items = listOf(
            HomeListItem.Header(HomeSection.PINNED),
            HomeListItem.SessionItem(session(1, "A", ms(TODAY))),
            HomeListItem.SessionItem(session(2, "B", ms(TODAY))),
            HomeListItem.Header(HomeSection.TODAY),
            HomeListItem.SessionItem(session(3, "C", ms(TODAY))),
        )

        assertEquals(
            listOf(0 to 0, 0 to 2, 1 to 2, 0 to 0, 0 to 1),
            HomeListBuilder.segmentPositions(items),
        )
    }

    @Test
    fun segmentPositions_empty_isEmpty() {
        assertEquals(emptyList<Pair<Int, Int>>(), HomeListBuilder.segmentPositions(emptyList()))
    }

    private fun List<HomeListItem>.headers() =
        filterIsInstance<HomeListItem.Header>().map { it.section }

    private fun List<HomeListItem>.sessionIds() =
        filterIsInstance<HomeListItem.SessionItem>().map { it.session.id }
}
