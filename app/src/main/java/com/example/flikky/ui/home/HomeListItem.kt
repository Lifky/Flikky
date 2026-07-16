package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.data.settings.SortMode
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
        sort: SortMode,
        group: GroupMode,
        today: LocalDate,
        zone: ZoneId,
    ): List<HomeListItem> {
        val comparator = when (sort) {
            SortMode.TIME -> compareByDescending<SessionEntity> { it.startedAt }
            SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }

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
