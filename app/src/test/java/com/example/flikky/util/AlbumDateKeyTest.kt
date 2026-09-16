package com.example.flikky.util

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 相册日期键：**全项目唯一把 epoch 变成「哪一天」的地方**。
 *
 * 装机验收（2026-09-16，Screenshot_12/13）暴露过一次双端不一致：手机与浏览器各自
 * 按自己设备的时区算日期，跨午夜的照片在两端掉进不同的分组，看起来像「文件搞混了」。
 * 修法是服务端算一次、把键下发给两端，所以这个对象是那条契约的实现。
 */
class AlbumDateKeyTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val losAngeles = ZoneId.of("America/Los_Angeles")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `key is the zero padded local date`() {
        assertEquals("2026-08-10", AlbumDateKey.of(at(shanghai, 2026, 8, 10), shanghai))
        // 零填充不是美观问题：键要能按字典序比较出时间先后（见 labelFor 的 today 判定）。
        assertEquals("2026-01-02", AlbumDateKey.of(at(shanghai, 2026, 1, 2), shanghai))
    }

    @Test
    fun `lexicographic order matches chronological order`() {
        val earlier = AlbumDateKey.of(at(shanghai, 2026, 1, 9), shanghai)
        val later = AlbumDateKey.of(at(shanghai, 2026, 1, 10), shanghai)

        assertTrue("$earlier 应当小于 $later", earlier < later)
    }

    @Test
    fun `the zone decides the day, and a different zone really gives a different key`() {
        // 这条正是装机那个 bug 的最小复现：同一瞬间，两个时区给出不同的日期。
        // 之前浏览器端自己算，于是它和手机对同一张照片给出了不同的分组。
        val instant = at(shanghai, 2026, 8, 10, 1, 0)

        assertEquals("2026-08-10", AlbumDateKey.of(instant, shanghai))
        assertEquals("2026-08-09", AlbumDateKey.of(instant, losAngeles))
    }

    @Test
    fun `local midnight is the boundary`() {
        assertEquals("2026-08-10", AlbumDateKey.of(at(shanghai, 2026, 8, 10, 0, 1), shanghai))
        assertEquals("2026-08-09", AlbumDateKey.of(at(shanghai, 2026, 8, 9, 23, 59), shanghai))
    }

    @Test
    fun `epoch zero still yields a usable key`() {
        // DATE_TAKEN 缺失且 DATE_MODIFIED 也为 0 的项真实存在。它不该让分组崩掉。
        assertEquals("1970-01-01", AlbumDateKey.of(0L, ZoneId.of("UTC")))
    }

    @Test
    fun `parse round trips`() {
        val key = AlbumDateKey.of(at(shanghai, 2026, 8, 10), shanghai)

        assertEquals(Triple(2026, 8, 10), AlbumDateKey.parse(key))
    }

    @Test
    fun `parse rejects anything that is not a key`() {
        // 键会经 DTO 跨网络到达，也会被浏览器回传。解析必须拒绝垃圾而不是抛。
        listOf("", "2026-08", "2026-08-10-01", "abcd-08-10", "2026-8-10", "2026-08-1", " 2026-08-10")
            .forEach { assertNull("必须拒绝：'$it'", AlbumDateKey.parse(it)) }
    }
}
