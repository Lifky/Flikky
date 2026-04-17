package com.example.flikky.session

import org.junit.Assert.assertEquals
import org.junit.Test

class TransferStatsTest {
    @Test
    fun `initial rate is zero`() {
        val stats = TransferStats(nowMs = { 0L })
        assertEquals(0L, stats.bytesPerSecond())
    }

    @Test
    fun `rate reflects bytes in last 1 second window`() {
        var now = 1_000L
        val stats = TransferStats(nowMs = { now })
        stats.recordBytes(500)
        now = 1_500L
        stats.recordBytes(500)
        now = 1_900L
        assertEquals(1000L, stats.bytesPerSecond())
    }

    @Test
    fun `old samples outside 1 second window expire`() {
        var now = 0L
        val stats = TransferStats(nowMs = { now })
        stats.recordBytes(1000)
        now = 2_500L
        assertEquals(0L, stats.bytesPerSecond())
    }

    @Test
    fun `totalBytes accumulates all records`() {
        var now = 0L
        val stats = TransferStats(nowMs = { now })
        stats.recordBytes(300)
        now = 5_000L
        stats.recordBytes(700)
        assertEquals(1000L, stats.totalBytes())
    }

    @Test
    fun `fileCount increments only via incrementFileCount`() {
        val stats = TransferStats(nowMs = { 0L })
        assertEquals(0, stats.fileCount())
        stats.incrementFileCount()
        stats.incrementFileCount()
        assertEquals(2, stats.fileCount())
    }
}
