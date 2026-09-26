package com.example.flikky.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionAddressesTest {

    @Test fun `the ip address is always the primary url`() {
        val a = connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Owned(37))
        assertEquals("http://192.168.1.37:8080", a.primaryUrl)
    }

    @Test fun `owned and probing show the local url`() {
        assertEquals("http://flikky37.local:8080",
            connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Owned(37)).localUrl)
        assertEquals("http://flikky37.local:8080",
            connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Probing(37)).localUrl)
    }

    @Test fun `renamed shows the name actually in use`() {
        assertEquals("http://flikky38.local:8080",
            connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Renamed(37, 38)).localUrl)
    }

    @Test fun `disabled and unavailable have no local url`() {
        assertNull(connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Disabled).localUrl)
        assertNull(connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Unavailable).localUrl)
    }

    @Test fun `a port that differs from the setting yields a notice`() {
        assertEquals(PortNotice(8080, 8081),
            connectionAddresses("192.168.1.37", 8081, 8080, LocalNameStatus.Disabled).portNotice)
    }

    @Test fun `the same port yields no notice`() {
        assertNull(connectionAddresses("192.168.1.37", 8080, 8080, LocalNameStatus.Disabled).portNotice)
    }

    @Test fun `an unknown requested or bound port yields no notice`() {
        assertNull(connectionAddresses("192.168.1.37", 8081, 0, LocalNameStatus.Disabled).portNotice)
        assertNull(connectionAddresses("192.168.1.37", 0, 8080, LocalNameStatus.Disabled).portNotice)
    }

    @Test fun `session state starts disabled and records updates`() {
        val s = SessionState(nowMs = { 0L })
        assertEquals(LocalNameStatus.Disabled, s.snapshot.value.localName)
        s.updateRequestedPort(8080)
        s.updateLocalName(LocalNameStatus.Owned(37))
        assertEquals(8080, s.snapshot.value.requestedPort)
        assertEquals(LocalNameStatus.Owned(37), s.snapshot.value.localName)
    }

    @Test fun `a new session clears the previous name and port`() {
        val s = SessionState(nowMs = { 0L })
        s.updateRequestedPort(8080)
        s.updateLocalName(LocalNameStatus.Owned(37))
        s.startNew(2L)
        assertEquals(0, s.snapshot.value.requestedPort)
        assertEquals(LocalNameStatus.Disabled, s.snapshot.value.localName)
    }
}
