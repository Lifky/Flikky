package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrMatrixTest {
    private val url = "http://192.168.1.7:8080"

    @Test fun `same text encodes to an identical matrix`() {
        val a = QrMatrix.encode(url)!!
        val b = QrMatrix.encode(url)!!
        assertEquals(a.size, b.size)
        for (y in 0 until a.size) for (x in 0 until a.size) assertEquals(a.isDark(x, y), b.isDark(x, y))
    }
    @Test fun `module count is a legal QR version size`() {
        val m = QrMatrix.encode(url)!!
        assertTrue(m.size in 21..177)
        assertEquals(0, (m.size - 17) % 4)
    }
    @Test fun `finder patterns have their standard structure`() {
        val m = QrMatrix.encode(url)!!
        assertTrue(m.isDark(0, 0)); assertFalse(m.isDark(1, 1)); assertTrue(m.isDark(3, 3))
        assertTrue(m.isDark(m.size - 1, 0)); assertFalse(m.isDark(m.size - 2, 1))
        assertTrue(m.isDark(0, m.size - 1)); assertFalse(m.isDark(1, m.size - 2))
    }
    @Test fun `reads outside the matrix are light instead of crashing`() {
        val m = QrMatrix.encode(url)!!
        assertFalse(m.isDark(-1, 0)); assertFalse(m.isDark(0, -1)); assertFalse(m.isDark(m.size, 0)); assertFalse(m.isDark(0, m.size))
    }
    @Test fun `blank text yields null`() { assertNull(QrMatrix.encode("")); assertNull(QrMatrix.encode("   ")) }
    @Test fun `text beyond QR capacity yields null instead of throwing`() { assertNull(QrMatrix.encode("x".repeat(10_000))) }
    @Test fun `quiet zone is the spec mandated four modules`() { assertEquals(4, QrMatrix.QUIET_ZONE) }
}
