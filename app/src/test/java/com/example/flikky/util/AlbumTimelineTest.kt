package com.example.flikky.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTimelineTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val now=LocalDateTime.of(2026,9,15,12,0).atZone(zone).toInstant().toEpochMilli()
    private fun at(y:Int,m:Int,d:Int,h:Int=10,min:Int=0)=LocalDateTime.of(y,m,d,h,min).atZone(zone).toInstant().toEpochMilli()
    private fun group(vararg times:Long)=AlbumTimeline.group(times.toList(),{it},now,zone)
    @Test fun `items taken today land in the today bucket`() { assertEquals(AlbumDateLabel.Today,group(at(2026,9,15)).single().label) }
    @Test fun `items taken yesterday land in the yesterday bucket`() { assertEquals(AlbumDateLabel.Yesterday,group(at(2026,9,14)).single().label) }
    @Test fun `earlier days this year keep month and day`() { assertEquals(AlbumDateLabel.SameYear(9,10),group(at(2026,9,10)).single().label) }
    @Test fun `previous years keep the year too`() { assertEquals(AlbumDateLabel.Older(2025,9,10),group(at(2025,9,10)).single().label) }
    @Test fun `items on the same local day share one section`() { assertEquals(3,group(at(2026,9,10,8),at(2026,9,10,20),at(2026,9,10,23,59)).single().items.size) }
    @Test fun `local midnight is the boundary not a rolling 24 hours`() { val s=group(at(2026,9,15,0,1),at(2026,9,14,23,59)); assertEquals(listOf(AlbumDateLabel.Today,AlbumDateLabel.Yesterday),s.map{it.label}) }
    @Test fun `sections run newest first and items inside them too`() { val s=group(at(2026,9,10),at(2026,9,15),at(2026,9,14,8),at(2026,9,14,20)); assertEquals(listOf(AlbumDateLabel.Today,AlbumDateLabel.Yesterday,AlbumDateLabel.SameYear(9,10)),s.map{it.label}); assertTrue(s[1].items[0]>s[1].items[1]) }
    @Test fun `unsorted input is sorted by the grouper`() { assertEquals(listOf(AlbumDateLabel.Today,AlbumDateLabel.SameYear(9,10),AlbumDateLabel.Older(2025,1,1)),group(at(2026,9,10),at(2026,9,15),at(2025,1,1)).map{it.label}) }
    @Test fun `the zone decides the bucket`() {
        val instant=Instant.parse("2026-09-14T23:00:00Z").toEpochMilli()
        assertEquals(AlbumDateLabel.Today,AlbumTimeline.group(listOf(instant),{it},now,ZoneId.of("Asia/Shanghai")).single().label)
        assertEquals(AlbumDateLabel.Yesterday,AlbumTimeline.group(listOf(instant),{it},now,ZoneId.of("UTC")).single().label)
    }
    @Test fun `an empty list yields no sections`() { assertEquals(emptyList<AlbumSection<Long>>(),AlbumTimeline.group(emptyList<Long>(),{it},now,zone)) }
    @Test fun `items dated in the future fall into today rather than vanishing`() { assertEquals(AlbumDateLabel.Today,group(at(2027,1,1)).single().label) }
}
