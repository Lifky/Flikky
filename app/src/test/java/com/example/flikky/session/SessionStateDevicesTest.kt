package com.example.flikky.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateDevicesTest {
    private fun d(id: String, avatar: String) = PeerDevice(deviceId = id, avatarKey = avatar, connectedAt = 0L, visible = true)

    @Test fun `devices are mirrored and every seen avatar is remembered`() {
        val s = SessionState(nowMs = { 0L })
        s.setDevices(listOf(d("a", "icon:star")))
        s.setDevices(listOf(d("b", "icon:face")))
        assertEquals(listOf("b"), s.devices.value.map { it.deviceId })
        assertEquals(mapOf("a" to "icon:star", "b" to "icon:face"), s.knownDeviceAvatars.value)
    }

    @Test fun `a new session forgets the previous devices`() {
        val s = SessionState(nowMs = { 0L })
        s.setDevices(listOf(d("a", "icon:star")))
        s.startNew(2L)
        assertTrue(s.devices.value.isEmpty())
        assertTrue(s.knownDeviceAvatars.value.isEmpty())
        s.setDevices(listOf(d("a", "icon:star")))
        s.reset()
        assertTrue(s.devices.value.isEmpty())
        assertTrue(s.knownDeviceAvatars.value.isEmpty())
    }

    // 被断开的设备离开 [devices] 后，名册里仍要有它的名字与头像（它发过的消息还在）。
    @Test fun `every device seen this session is remembered with its latest record`() {
        val s = SessionState(nowMs = { 0L })
        s.setDevices(listOf(d("a", "icon:star").copy(name = "客厅")))
        s.setDevices(listOf(d("a", "icon:face").copy(name = "书房")))
        s.setDevices(emptyList())
        assertEquals("书房", s.knownDevices.value.getValue("a").name)
        s.startNew(3L)
        assertTrue(s.knownDevices.value.isEmpty())
    }
}
