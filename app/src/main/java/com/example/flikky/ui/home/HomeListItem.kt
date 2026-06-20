package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.data.settings.SortMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface HomeListItem {
    data class Header(val label: String) : HomeListItem
    data class SessionItem(val session: SessionEntity) : HomeListItem
}

object HomeListBuilder {
    const val SEC_RUNNING = "进行中"
    const val SEC_PINNED = "置顶"
    const val SEC_ENDED = "已结束"
    const val BUCKET_TODAY = "今天"
    const val BUCKET_YESTERDAY = "昨天"
    const val BUCKET_EARLIER = "更早"

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
        fun section(label: String, list: List<SessionEntity>): List<HomeListItem> =
            if (list.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    add(HomeListItem.Header(label))
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
                section(SEC_RUNNING, running) + section(SEC_PINNED, pinned) + section(SEC_ENDED, ended)
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

                section(SEC_PINNED, pinned) +
                    section(BUCKET_TODAY, todayList) +
                    section(BUCKET_YESTERDAY, yesterdayList) +
                    section(BUCKET_EARLIER, earlier)
            }
        }
    }
}
