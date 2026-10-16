package com.example.flikky.ui.serving

import com.example.flikky.session.PeerDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicesSheetLogicTest {
    private fun d(id: String, at: Long, online: Boolean, name: String = "") = PeerDevice(
        deviceId = id, name = name, defaultLabel = "Chrome · Windows", avatarKey = "icon:star",
        connectedAt = at, connections = if (online) 1 else 0, visible = true,
    )

    @Test fun `online devices come first, then by connection time`() {
        val list = listOf(d("off-early", 1, false), d("on-late", 3, true), d("on-early", 2, true))
        assertEquals(listOf("on-early", "on-late", "off-early"), devicesInSheetOrder(list).map { it.deviceId })
    }

    @Test fun `the default name is repeated in the supporting line only under a custom name`() {
        assertEquals("Chrome · Windows", deviceSupportingLabel(d("a", 1, true, name = "张三的笔记本"), "浏览器"))
        assertNull(deviceSupportingLabel(d("a", 1, true), "浏览器"))
    }

    @Test fun `the header shows the earliest online device with the online count`() {
        val list = listOf(d("b", 2, true), d("a", 1, true), d("c", 0, false))
        assertEquals("a" to 2, headerPeer(list)?.let { it.first.deviceId to it.second })
        assertNull(headerPeer(listOf(d("c", 0, false))))
    }

    @Test fun `with nobody online the card names the device that still holds access`() {
        val offline = d("a", 1, false, name = "张三的笔记本")
        assertEquals(CardPeerState(pinUsedBy = "张三的笔记本", offlineName = "张三的笔记本"),
            connectionCardPeer(requirePin = true, devices = listOf(offline), unknownLabel = "浏览器"))
        assertEquals(CardPeerState(pinUsedBy = null, offlineName = "张三的笔记本"),
            connectionCardPeer(requirePin = false, devices = listOf(offline), unknownLabel = "浏览器"))
    }

    @Test fun `with no devices or someone online the card stays as it was`() {
        assertEquals(CardPeerState(null, null), connectionCardPeer(true, emptyList(), "浏览器"))
        assertEquals(CardPeerState(null, null), connectionCardPeer(true, listOf(d("a", 1, true)), "浏览器"))
    }
}
