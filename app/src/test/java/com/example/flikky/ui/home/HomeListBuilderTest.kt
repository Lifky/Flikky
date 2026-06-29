package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.data.settings.SortMode
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
    @Test
    fun none_pinnedFirst_then_byTime_desc() {
        val a = session(1, "A", ms(TODAY.minusDays(3)))
        val b = session(2, "B", ms(TODAY))
        val p = session(3, "P", ms(TODAY.minusDays(5)), pinned = true)

        val out = HomeListBuilder.build(listOf(a, b, p), SortMode.TIME, GroupMode.NONE, TODAY, ZONE)

        assertEquals(listOf(3L, 2L, 1L), out.sessionIds())
        assertEquals(emptyList<String>(), out.headers())
    }

    @Test
    fun none_byName_asc_caseInsensitive() {
        val a = session(1, "banana", ms(TODAY))
        val b = session(2, "Apple", ms(TODAY))

        val out = HomeListBuilder.build(listOf(a, b), SortMode.NAME, GroupMode.NONE, TODAY, ZONE)

        assertEquals(listOf(2L, 1L), out.sessionIds())
    }

    @Test
    fun status_groups_running_pinned_ended_without_empty_headers() {
        val running = session(1, "Running", ms(TODAY), ended = false)
        val pinned = session(2, "Pinned", ms(TODAY.minusDays(1)), pinned = true)
        val ended = session(3, "Ended", ms(TODAY.minusDays(2)))

        val out = HomeListBuilder.build(
            listOf(ended, pinned, running),
            SortMode.TIME,
            GroupMode.STATUS,
            TODAY,
            ZONE,
        )

        assertEquals(listOf("进行中", "置顶", "已结束"), out.headers())
        assertEquals(listOf(1L, 2L, 3L), out.sessionIds())
    }

    @Test
    fun status_omits_empty_groups() {
        val ended = session(1, "Ended", ms(TODAY))

        val out = HomeListBuilder.build(listOf(ended), SortMode.TIME, GroupMode.STATUS, TODAY, ZONE)

        assertEquals(listOf("已结束"), out.headers())
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
            SortMode.TIME,
            GroupMode.DATE,
            TODAY,
            ZONE,
        )

        assertEquals(listOf("置顶", "今天", "昨天", "更早"), out.headers())
        assertEquals(listOf(1L, 2L, 3L, 4L), out.sessionIds())
    }

    @Test
    fun date_group_places_running_by_start_date() {
        val runningYesterday = session(1, "Running", ms(TODAY.minusDays(1)), ended = false)

        val out = HomeListBuilder.build(
            listOf(runningYesterday),
            SortMode.TIME,
            GroupMode.DATE,
            TODAY,
            ZONE,
        )

        assertEquals(listOf("昨天"), out.headers())
        assertEquals(listOf(1L), out.sessionIds())
    }

    @Test
    fun sort_applies_inside_each_group() {
        val z = session(1, "zeta", ms(TODAY))
        val a = session(2, "Alpha", ms(TODAY.minusDays(1)))
        val p2 = session(3, "beta", ms(TODAY.minusDays(2)), pinned = true)
        val p1 = session(4, "Apple", ms(TODAY.minusDays(3)), pinned = true)

        val out = HomeListBuilder.build(listOf(z, a, p2, p1), SortMode.NAME, GroupMode.DATE, TODAY, ZONE)

        assertEquals(listOf("置顶", "今天", "昨天"), out.headers())
        assertEquals(listOf(4L, 3L, 1L, 2L), out.sessionIds())
    }

    @Test
    fun empty_list_has_no_headers() {
        val out = HomeListBuilder.build(emptyList(), SortMode.TIME, GroupMode.DATE, TODAY, ZONE)

        assertEquals(emptyList<String>(), out.headers())
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
            HomeListItem.Header("今天"),
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
            HomeListItem.Header("置顶"),
            HomeListItem.SessionItem(session(1, "A", ms(TODAY))),
            HomeListItem.SessionItem(session(2, "B", ms(TODAY))),
            HomeListItem.Header("今天"),
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
        filterIsInstance<HomeListItem.Header>().map { it.label }

    private fun List<HomeListItem>.sessionIds() =
        filterIsInstance<HomeListItem.SessionItem>().map { it.session.id }
}
