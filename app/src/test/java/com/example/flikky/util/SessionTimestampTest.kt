package com.example.flikky.util

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimestampTest {

    @Test
    fun `first message always gets divider`() {
        assertTrue(SessionTimestamp.shouldInsertDividerBefore(null, 1_000L))
    }

    @Test
    fun `gap over five minutes gets divider`() {
        val prev = 1_000_000L
        assertTrue(SessionTimestamp.shouldInsertDividerBefore(prev, prev + 5 * 60 * 1000 + 1))
    }

    @Test
    fun `gap of exactly five minutes gets divider`() {
        val prev = 1_000_000L
        assertTrue(SessionTimestamp.shouldInsertDividerBefore(prev, prev + 5 * 60 * 1000))
        assertFalse(SessionTimestamp.shouldInsertDividerBefore(prev, prev + 5 * 60 * 1000 - 1))
    }

    @Test
    fun `continuous messages anchor gaps to the last inserted divider`() {
        val minute = 60_000L

        assertEquals(
            setOf(0, 2, 4),
            SessionTimestamp.dividerIndices(
                listOf(0L, 4 * minute, 5 * minute, 9 * minute, 10 * minute),
            ),
        )
    }

    @Test
    fun `format is fixed yy MM dd HH mm`() {
        val tz = TimeZone.getTimeZone("GMT+08:00")
        val calendar = java.util.Calendar.getInstance(tz).apply {
            set(2026, java.util.Calendar.AUGUST, 14, 13, 49, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals("26/08/14 13:49", SessionTimestamp.format(calendar.timeInMillis, tz))
    }

    @Test
    fun `format pads single digits`() {
        val tz = TimeZone.getTimeZone("GMT")
        val calendar = java.util.Calendar.getInstance(tz).apply {
            set(2031, java.util.Calendar.JANUARY, 5, 8, 7, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals("31/01/05 08:07", SessionTimestamp.format(calendar.timeInMillis, tz))
    }
}
