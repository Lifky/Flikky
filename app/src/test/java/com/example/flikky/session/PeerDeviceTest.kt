package com.example.flikky.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerDeviceTest {
    private fun device(name: String = "", label: String? = "Chrome · Windows", ordinal: Int = 1) =
        PeerDevice(deviceId = "d", name = name, defaultLabel = label, defaultOrdinal = ordinal, avatarKey = "icon:star", connectedAt = 0L)

    // 装机 Screenshot_6/7：「Edge · Windows」在顶栏必被截断，只留浏览器名好看得多；系统交给设备面板的副标题。
    @Test fun `the default name is just the browser, with the ordinal from the second device on`() {
        assertEquals("Chrome", device().defaultName("浏览器"))
        assertEquals("Chrome 2", device(ordinal = 2).defaultName("浏览器"))
        assertEquals("浏览器 3", device(label = null, ordinal = 3).defaultName("浏览器"))
    }

    @Test fun `a custom name wins over the default`() {
        assertEquals("张三的笔记本", device(name = "张三的笔记本").displayName("浏览器"))
        assertEquals("Chrome", device().displayName("浏览器"))
    }

    @Test fun `online means at least one connection`() {
        assertFalse(device().online)
        assertTrue(device().copy(connections = 1).online)
    }

    @Test fun `a message avatar comes from its sender device, else the fallback`() {
        val avatars = mapOf("a" to "icon:face")
        assertEquals("icon:face", peerAvatarFor("a", avatars, "icon:desktop_windows"))
        assertEquals("icon:desktop_windows", peerAvatarFor("gone", avatars, "icon:desktop_windows"))
        assertEquals("icon:desktop_windows", peerAvatarFor(null, avatars, "icon:desktop_windows"))
    }
}
