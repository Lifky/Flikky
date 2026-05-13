package com.example.flikky.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkRebinderTest {

    @Test
    fun `initial event with no ipv4 is StayPut`() {
        val r = NetworkRebinder()
        val intent = r.onLink(LinkInfo(ipv4 = null))
        assertTrue(intent is RebindIntent.StayPut)
        assertNull(r.snapshot())
    }

    @Test
    fun `primed ip echoed back is StayPut`() {
        val r = NetworkRebinder()
        r.prime("192.168.1.5")
        val intent = r.onLink(LinkInfo(ipv4 = "192.168.1.5"))
        assertTrue(intent is RebindIntent.StayPut)
        assertEquals("192.168.1.5", r.snapshot())
    }

    @Test
    fun `primed ip replaced by new ip emits Rebind and updates snapshot`() {
        val r = NetworkRebinder()
        r.prime("192.168.1.5")
        val intent = r.onLink(LinkInfo(ipv4 = "192.168.2.10"))
        assertTrue(intent is RebindIntent.Rebind)
        assertEquals("192.168.2.10", (intent as RebindIntent.Rebind).newIp)
        assertEquals("192.168.2.10", r.snapshot())
    }

    @Test
    fun `primed ip going away emits Lost but keeps snapshot for same-ip recovery`() {
        val r = NetworkRebinder()
        r.prime("192.168.1.5")
        val intent = r.onLink(LinkInfo(ipv4 = null))
        assertTrue(intent is RebindIntent.Lost)
        // currentIp kept so that the next event with the same IP is Restored,
        // not Rebind (Ktor wasn't stopped, no need to restart).
        assertEquals("192.168.1.5", r.snapshot())
    }

    @Test
    fun `null-then-new emits Rebind`() {
        val r = NetworkRebinder()
        val first = r.onLink(LinkInfo(ipv4 = null))
        assertTrue(first is RebindIntent.StayPut)
        val second = r.onLink(LinkInfo(ipv4 = "10.0.0.3"))
        assertTrue(second is RebindIntent.Rebind)
        assertEquals("10.0.0.3", (second as RebindIntent.Rebind).newIp)
        assertEquals("10.0.0.3", r.snapshot())
    }

    @Test
    fun `same ip twice only rebinds once`() {
        val r = NetworkRebinder()
        val first = r.onLink(LinkInfo(ipv4 = "10.0.0.3"))
        assertTrue(first is RebindIntent.Rebind)
        val second = r.onLink(LinkInfo(ipv4 = "10.0.0.3"))
        assertTrue(second is RebindIntent.StayPut)
        assertEquals("10.0.0.3", r.snapshot())
    }

    @Test
    fun `lost then same ip back emits Restored not Rebind`() {
        val r = NetworkRebinder()
        r.prime("192.168.1.50")
        assertTrue(r.onLink(LinkInfo(null)) is RebindIntent.Lost)
        val back = r.onLink(LinkInfo("192.168.1.50"))
        assertTrue("expected Restored, got $back", back is RebindIntent.Restored)
        assertEquals("192.168.1.50", r.snapshot())
    }

    @Test
    fun `lost then different ip emits Rebind`() {
        val r = NetworkRebinder()
        r.prime("192.168.1.50")
        assertTrue(r.onLink(LinkInfo(null)) is RebindIntent.Lost)
        val back = r.onLink(LinkInfo("192.168.2.20"))
        assertTrue(back is RebindIntent.Rebind)
        assertEquals("192.168.2.20", (back as RebindIntent.Rebind).newIp)
    }

    @Test
    fun `consecutive lost events collapse to StayPut`() {
        val r = NetworkRebinder()
        r.prime("192.168.1.50")
        assertTrue(r.onLink(LinkInfo(null)) is RebindIntent.Lost)
        assertTrue(r.onLink(LinkInfo(null)) is RebindIntent.StayPut)
    }

    @Test
    fun `rebind then lost then recover walks full cycle`() {
        val r = NetworkRebinder()
        r.prime("192.168.1.5")
        assertTrue(r.onLink(LinkInfo("192.168.1.50")) is RebindIntent.Rebind)
        assertTrue(r.onLink(LinkInfo(null)) is RebindIntent.Lost)
        val back = r.onLink(LinkInfo("192.168.1.50"))
        assertTrue("expected Restored, got $back", back is RebindIntent.Restored)
    }
}
