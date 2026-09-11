package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ByteFormatTest {

    @Test
    fun `formats binary unit boundaries with at most one decimal place`() {
        assertEquals("0 B", formatBytes(0L))
        assertEquals("1 B", formatBytes(1L))
        assertEquals("1023 B", formatBytes(1023L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1024.0 KB", formatBytes(1_048_575L))
        assertEquals("1.0 MB", formatBytes(1_048_576L))
        assertEquals("1.0 GB", formatBytes(1_073_741_824L))
    }

    @Test
    fun `keeps one fractional digit without trailing hundredths`() {
        assertEquals("1.5 MB", formatBytes(1_572_864L))
        assertFalse(formatBytes(1_572_864L).contains("1.50"))
    }

    @Test
    fun `invalid values never leak NaN into user-visible text`() {
        assertEquals("0 B", formatBytes(-1L))
        assertEquals("0 B", formatBytes(Double.NaN))
        assertEquals("0 B", formatBytes(Double.POSITIVE_INFINITY))
        assertEquals("0 B", formatBytes(Double.NEGATIVE_INFINITY))
    }
}
