package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortSpecTest {

    @Test
    fun `natural direction is ascending for name and descending for time and size`() {
        assertFalse(SortSpec.natural(SortKey.NAME).descending)
        assertTrue(SortSpec.natural(SortKey.TIME).descending)
        assertTrue(SortSpec.natural(SortKey.SIZE).descending)
    }

    @Test
    fun `tapping the current key flips its direction`() {
        val spec = SortSpec(SortKey.SIZE, descending = true)
        assertEquals(SortSpec(SortKey.SIZE, descending = false), spec.tap(SortKey.SIZE))
        assertEquals(
            SortSpec(SortKey.SIZE, descending = true),
            spec.tap(SortKey.SIZE).tap(SortKey.SIZE),
        )
    }

    @Test
    fun `tapping a new key uses that key's natural direction, not the current one`() {
        // 当前是「名称降序」，点「时间」应得到时间的自然方向（降序），而不是继承
        // 当前的 descending —— 两者在这个用例里恰好都是 true，所以再补一个方向
        // 相反的用例把它们分开。
        assertEquals(
            SortSpec(SortKey.TIME, descending = true),
            SortSpec(SortKey.NAME, descending = true).tap(SortKey.TIME),
        )
        assertEquals(
            SortSpec(SortKey.NAME, descending = false),
            SortSpec(SortKey.SIZE, descending = true).tap(SortKey.NAME),
        )
    }

    @Test
    fun `format and parse round-trip every combination`() {
        for (key in SortKey.entries) {
            for (desc in listOf(true, false)) {
                val spec = SortSpec(key, desc)
                assertEquals(spec, SortSpec.parse(spec.format()))
            }
        }
    }

    @Test
    fun `parse returns null for anything it does not recognise instead of guessing`() {
        assertNull(SortSpec.parse(null))
        assertNull(SortSpec.parse(""))
        assertNull(SortSpec.parse("   "))
        assertNull(SortSpec.parse("NAME"))
        assertNull(SortSpec.parse("NAME:"))
        assertNull(SortSpec.parse("NOPE:asc"))
        assertNull(SortSpec.parse("NAME:sideways"))
        assertNull(SortSpec.parse("NAME:asc:extra"))
    }

    @Test
    fun `name order is case-insensitive first, raw string as tie-break`() {
        assertEquals(
            listOf("Alpha", "alpha", "beta"),
            listOf("beta", "alpha", "Alpha").sortedWith(NAME_ORDER),
        )
    }

    @Test
    fun `a lowercase name sorts before a later letter's uppercase name`() {
        // 这一条把「先 lowercase 再比」与「直接按码位比」分开 —— 上一条分不开：
        // 纯码位序下 Alpha(A=65) < alpha(97) < beta(98)，结果恰好相同。
        // 这里 ASCII 序会把 Beta(B=66) 排到 alpha(97) 前面，大小写不敏感则不会。
        // 逼红实测：去掉 lowercase 时上一条零条红，这一条红。
        assertEquals(
            listOf("alpha", "Beta"),
            listOf("Beta", "alpha").sortedWith(NAME_ORDER),
        )
    }

    @Test
    fun `name order compares digits by code unit, not numerically`() {
        // 「10 在 2 之前」是码位序的正确结果，也是两端相等的前提。
        // 任何一端偷偷改成「自然数字排序」，跨端 fixture 会立刻炸。
        assertEquals(
            listOf("10", "100", "2"),
            listOf("2", "100", "10").sortedWith(NAME_ORDER),
        )
    }
}
