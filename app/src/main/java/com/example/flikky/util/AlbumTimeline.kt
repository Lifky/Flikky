package com.example.flikky.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface AlbumDateLabel {
    data object Today : AlbumDateLabel
    data object Yesterday : AlbumDateLabel
    data class SameYear(val month: Int, val day: Int) : AlbumDateLabel
    data class Older(val year: Int, val month: Int, val day: Int) : AlbumDateLabel
}

data class AlbumSection<T>(val label: AlbumDateLabel, val items: List<T>)

object AlbumTimeline {
    fun <T> group(items: List<T>, takenAtMs: (T) -> Long, nowMs: Long, zone: ZoneId): List<AlbumSection<T>> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        return items.sortedByDescending(takenAtMs)
            .groupBy { Instant.ofEpochMilli(takenAtMs(it)).atZone(zone).toLocalDate() }
            .map { (date, rows) -> AlbumSection(label(date, today, yesterday), rows) }
    }
    private fun label(date: LocalDate, today: LocalDate, yesterday: LocalDate): AlbumDateLabel = when {
        date >= today -> AlbumDateLabel.Today
        date == yesterday -> AlbumDateLabel.Yesterday
        date.year == today.year -> AlbumDateLabel.SameYear(date.monthValue, date.dayOfMonth)
        else -> AlbumDateLabel.Older(date.year, date.monthValue, date.dayOfMonth)
    }
}
