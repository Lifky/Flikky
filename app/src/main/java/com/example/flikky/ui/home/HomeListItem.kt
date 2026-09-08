package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.util.NAME_ORDER
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface HomeListItem {
    data class Header(val section: HomeSection) : HomeListItem
    data class SessionItem(val session: SessionEntity) : HomeListItem
}

enum class HomeSection { RUNNING, PINNED, ENDED, TODAY, YESTERDAY, EARLIER }

object HomeListBuilder {
    /**
     * For each entry in [items], its position within the contiguous run of [HomeListItem.SessionItem]s
     * it belongs to (a [HomeListItem.Header] breaks the run), as `(indexInRun, runSize)`. Headers map
     * to `(0, 0)`.
     *
     * Drives the per-position corners of the official `SegmentedListItem`: first item in a run gets the
     * large top corners, last gets the large bottom corners, middles stay small — so each date/status
     * section reads as one connected segmented group.
     */
    fun segmentPositions(items: List<HomeListItem>): List<Pair<Int, Int>> {
        val result = MutableList(items.size) { 0 to 0 }
        var i = 0
        while (i < items.size) {
            if (items[i] is HomeListItem.SessionItem) {
                var j = i
                while (j < items.size && items[j] is HomeListItem.SessionItem) j++
                for (k in i until j) result[k] = (k - i) to (j - i)
                i = j
            } else {
                i++
            }
        }
        return result
    }

    fun filterByGroup(sessions: List<SessionEntity>, activeGroupId: Long?): List<SessionEntity> =
        if (activeGroupId == null) {
            sessions
        } else {
            sessions.filter { it.groupId == activeGroupId }
        }

    fun build(
        sessions: List<SessionEntity>,
        sort: SortSpec,
        group: GroupMode,
        today: LocalDate,
        zone: ZoneId,
    ): List<HomeListItem> {
        // 键决定比较什么，**方向由 sort.descending 决定**，不写死。
        //
        // 早先这里是 `SortKey.TIME -> compareByDescending`、`NAME -> compareBy`,
        // 方向焊在分支里 —— 于是第二次点同一个键时设置翻转了、菜单箭头也翻了，
        // 列表顺序却不动（2026-09-08 装机反馈）。守卫见 HomeListBuilderTest
        // 的四条 direction 用例。
        val byKey: Comparator<SessionEntity> = when (sort.key) {
            SortKey.NAME -> compareBy(NAME_ORDER) { it.name }
            // 会话没有大小。UI 不给这个选项（HomeSortSheet 只列名称与时间），
            // 真的收到时按时间处理而不是抛 —— 一个排序键不值得让主页崩掉。
            SortKey.TIME, SortKey.SIZE -> compareBy { it.startedAt }
        }
        val directed = if (sort.descending) byKey.reversed() else byKey
        // 兜底恒**升序**，与 StorageListingPolicy / FavoritesListOrder 同一裁决：
        // 兜底跟着方向翻转的话，同名或同时刻的两项在两个方向下相对顺序会反过来。
        val comparator = directed.thenBy(NAME_ORDER) { it.name }

        fun sorted(list: List<SessionEntity>) = list.sortedWith(comparator)
        fun section(section: HomeSection, list: List<SessionEntity>): List<HomeListItem> =
            if (list.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    add(HomeListItem.Header(section))
                    addAll(sorted(list).map { HomeListItem.SessionItem(it) })
                }
            }

        return when (group) {
            GroupMode.NONE -> {
                val pinned = sessions.filter { it.pinned }
                val rest = sessions.filter { !it.pinned }
                (sorted(pinned) + sorted(rest)).map { HomeListItem.SessionItem(it) }
            }
            GroupMode.STATUS -> {
                val running = sessions.filter { it.endedAt == null }
                val pinned = sessions.filter { it.endedAt != null && it.pinned }
                val ended = sessions.filter { it.endedAt != null && !it.pinned }
                section(HomeSection.RUNNING, running) +
                    section(HomeSection.PINNED, pinned) +
                    section(HomeSection.ENDED, ended)
            }
            GroupMode.DATE -> {
                val pinned = sessions.filter { it.pinned }
                val nonPinned = sessions.filter { !it.pinned }
                val yesterday = today.minusDays(1)

                fun dateOf(session: SessionEntity): LocalDate =
                    Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()

                val todayList = nonPinned.filter { dateOf(it) == today }
                val yesterdayList = nonPinned.filter { dateOf(it) == yesterday }
                val earlier = nonPinned.filter { dateOf(it) < yesterday }

                section(HomeSection.PINNED, pinned) +
                    section(HomeSection.TODAY, todayList) +
                    section(HomeSection.YESTERDAY, yesterdayList) +
                    section(HomeSection.EARLIER, earlier)
            }
        }
    }
}
