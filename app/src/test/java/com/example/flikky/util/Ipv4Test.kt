package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ipv4Test {
    @Test fun `parse round trips`() {
        assertEquals("192.168.1.37", Ipv4.parse("192.168.1.37").toString())
        assertEquals("255.255.255.255", Ipv4.parse("255.255.255.255").toString())
    }

    @Test fun `parse rejects anything that is not a dotted quad`() {
        listOf("", "1.2.3", "1.2.3.4.5", "256.1.1.1", "a.b.c.d", "1..2.3", "01.2.3.4x").forEach {
            assertNull(it, Ipv4.parse(it))
        }
    }

    @Test fun `bytes round trip`() {
        val ip = Ipv4.parse("10.0.2.16")!!
        assertEquals(ip, Ipv4.fromBytes(ip.toBytes()))
        assertEquals(listOf(10, 0, 2, 16), ip.toBytes().map { it.toInt() and 0xFF })
    }

    @Test fun `comparison is unsigned`() {
        // 192.x 的最高位是 1：有符号比较会把它排到 10.x 前面，同时探测的让名规则就反了。
        assertTrue(Ipv4.parse("192.168.1.2")!! > Ipv4.parse("10.0.0.1")!!)
        assertTrue(Ipv4.parse("192.168.1.3")!! > Ipv4.parse("192.168.1.2")!!)
    }

    @Test fun `same subnet respects the prefix`() {
        val a = Ipv4.parse("192.168.1.37")!!
        assertTrue(a.sameSubnet(Ipv4.parse("192.168.1.200")!!, 24))
        assertFalse(a.sameSubnet(Ipv4.parse("192.168.2.1")!!, 24))
        assertTrue(a.sameSubnet(Ipv4.parse("192.168.2.1")!!, 16))
        assertTrue(a.sameSubnet(Ipv4.parse("8.8.8.8")!!, 0))
        assertFalse(a.sameSubnet(Ipv4.parse("192.168.1.36")!!, 32))
    }
}
