package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LocalHostNameTest {

    @Test fun `names and urls are assembled from the number`() {
        assertEquals("flikky37", LocalHostName.label(37))
        assertEquals("flikky37.local", LocalHostName.fqdn(37))
        assertEquals("http://flikky37.local:8080", LocalHostName.url(LocalHostName.fqdn(37), 8080))
        assertEquals("http://192.168.1.5:8081", LocalHostName.url("192.168.1.5", 8081))
    }

    @Test fun `number bounds are 0 to 999`() {
        assertTrue(LocalHostName.isValidNumber(0))
        assertTrue(LocalHostName.isValidNumber(999))
        assertFalse(LocalHostName.isValidNumber(-1))
        assertFalse(LocalHostName.isValidNumber(1000))
    }

    @Test fun `port bounds are 1024 to 65535`() {
        assertFalse(LocalHostName.isValidPort(1023))
        assertTrue(LocalHostName.isValidPort(1024))
        assertTrue(LocalHostName.isValidPort(65535))
        assertFalse(LocalHostName.isValidPort(65536))
    }

    @Test fun `candidates are the number and the nine above it`() {
        assertEquals((37..46).toList(), LocalHostName.candidates(37))
    }

    @Test fun `candidates wrap past 999 to 0`() {
        assertEquals(listOf(995, 996, 997, 998, 999, 0, 1, 2, 3, 4), LocalHostName.candidates(995))
        assertEquals(0, LocalHostName.candidates(999)[1])
    }

    @Test fun `port scan covers twenty ports`() {
        assertEquals(8080..8099, LocalHostName.portRange(8080))
    }

    @Test fun `port scan is capped at 65535`() {
        assertEquals(65530..65535, LocalHostName.portRange(65530))
        assertEquals(65535..65535, LocalHostName.portRange(65535))
    }

    @Test fun `random default stays in 1 to 99`() {
        val random = Random(7)
        repeat(1000) {
            val n = LocalHostName.randomDefaultNumber(random)
            assertTrue("out of range: $n", n in 1..99)
        }
    }

    @Test fun `number text parsing rejects blanks, junk, leading zeros and overflow`() {
        assertEquals(0, LocalHostName.parseNumber("0"))
        assertEquals(37, LocalHostName.parseNumber("37"))
        assertEquals(999, LocalHostName.parseNumber("999"))
        // "07" 与 "7" 会生成两个不同的名字，用户几乎一定是想要 7 —— 直接拒绝，别悄悄改写。
        assertNull(LocalHostName.parseNumber("07"))
        assertNull(LocalHostName.parseNumber(""))
        assertNull(LocalHostName.parseNumber("1000"))
        assertNull(LocalHostName.parseNumber("3a"))
        assertNull(LocalHostName.parseNumber("-1"))
    }

    @Test fun `port text parsing enforces the range`() {
        assertEquals(8080, LocalHostName.parsePort("8080"))
        assertEquals(1024, LocalHostName.parsePort("1024"))
        assertEquals(65535, LocalHostName.parsePort("65535"))
        assertNull(LocalHostName.parsePort("80"))
        assertNull(LocalHostName.parsePort("1023"))
        assertNull(LocalHostName.parsePort("65536"))
        assertNull(LocalHostName.parsePort("08080"))
        assertNull(LocalHostName.parsePort(""))
    }
}
