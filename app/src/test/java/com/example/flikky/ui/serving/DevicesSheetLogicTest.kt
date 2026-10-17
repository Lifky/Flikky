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

    // 装机 Screenshot_8：标题是名字，副标题始终是完整的「浏览器 · 系统」—— 不管改没改过名。
    @Test fun `the supporting line always carries the full browser and system`() {
        assertEquals("Chrome · Windows", deviceSupportingLabel(d("a", 1, true, name = "张三的笔记本")))
        assertEquals("Chrome · Windows", deviceSupportingLabel(d("a", 1, true)))
        assertNull(deviceSupportingLabel(d("a", 1, true).copy(defaultLabel = null)))
    }

    private fun da(id: String, at: Long, online: Boolean, avatar: String) =
        d(id, at, online).copy(avatarKey = avatar)

    // 装机 Screenshot_2：顶栏原先拼「X 等 N 台」，必然被截断；也看不出能点。
    @Test fun `the header names a lone device and counts several`() {
        val one = headerSummary(listOf(d("a", 1, true, name = "客厅"), d("c", 0, false)))!!
        assertEquals("客厅", one.primary.name)
        assertEquals(1, one.onlineCount)
        val many = headerSummary(listOf(da("b", 2, true, "icon:face"), da("a", 1, true, "icon:star"), da("c", 0, false, "icon:pets")))!!
        assertEquals(2, many.onlineCount)
        assertEquals(listOf("icon:star", "icon:face"), many.avatarKeys)
        assertNull(headerSummary(listOf(d("c", 0, false))))
    }

    @Test fun `the header stacks at most three avatars`() {
        val list = (1..5).map { da("d$it", it.toLong(), true, "icon:$it") }
        assertEquals(listOf("icon:1", "icon:2", "icon:3"), headerSummary(list)!!.avatarKeys)
    }

    // 装机 Screenshot_3：等待行只说了最早那一台离线；多台都离线时要说清楚是几台。
    @Test fun `with nobody online the waiting row counts every offline device`() {
        val offline = offlinePeers(listOf(da("b", 2, false, "icon:face"), da("a", 1, false, "icon:star")), "浏览器")!!
        assertEquals(2, offline.count)
        assertEquals("Chrome", offline.firstName)
        assertEquals(listOf("icon:star", "icon:face"), offline.avatarKeys)
    }

    @Test fun `with no devices or someone online there is no offline summary`() {
        assertNull(offlinePeers(emptyList(), "浏览器"))
        assertNull(offlinePeers(listOf(d("a", 1, true), d("b", 2, false)), "浏览器"))
    }

    // 新设备连进来时提示一句，让人知道「多了一台」。第一次拿到列表不算（那是进入页面，不是加入）。
    @Test fun `only devices that appear later count as newly joined`() {
        assertEquals(emptyList<String>(), newlyJoined(previousIds = null, current = listOf(d("a", 1, true))).map { it.deviceId })
        assertEquals(listOf("b"), newlyJoined(setOf("a"), listOf(d("a", 1, true), d("b", 2, true))).map { it.deviceId })
        assertEquals(emptyList<String>(), newlyJoined(setOf("a", "b"), listOf(d("a", 1, true))).map { it.deviceId })
    }
}
