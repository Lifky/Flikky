package com.example.flikky.ui.components

import com.example.flikky.data.settings.SenderNameMode
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 气泡上方的发送者名字（v1.23 多设备）。两端同一规则：浏览器端见 app.js 的 `senderLabelVisible`。
 * 一对一时名字只是噪音，所以「自动」= 本会话出现过 2 台以上对端设备时才显示。
 */
class SenderLabelTest {
    @Test fun `auto shows names only once a second peer device has appeared`() {
        assertFalse(senderNamesEnabled(SenderNameMode.AUTO, peerDeviceCount = 1))
        assertTrue(senderNamesEnabled(SenderNameMode.AUTO, peerDeviceCount = 2))
    }

    @Test fun `always and off ignore the device count`() {
        assertTrue(senderNamesEnabled(SenderNameMode.ALWAYS, peerDeviceCount = 1))
        assertFalse(senderNamesEnabled(SenderNameMode.OFF, peerDeviceCount = 5))
    }

    @Test fun `a name heads each run of messages from the same sender`() {
        // 群聊惯例：同一个人连续发的几条只在第一条上标名字。
        val senders = listOf("a", "a", "b", "a", "a")
        val shown = senders.indices.map { i -> senderLabelAt(senders, i) }
        assertEquals(listOf(true, false, true, true, false), shown)
    }

    @Test fun `messages from this side never carry a name`() {
        val senders = listOf("a", null, "a")
        assertEquals(listOf(true, false, true), senders.indices.map { senderLabelAt(senders, it) })
    }

    // 头像分组与名字都按「发送设备」分组：两台电脑交替发言时，按来源（都是 BROWSER）分组会把它们并成一组。
    @Test fun `messages are grouped by the device that sent them`() {
        val a1 = Message.Text(1, Origin.BROWSER, 0, "x", senderDeviceId = "a")
        val b1 = Message.Text(2, Origin.BROWSER, 0, "y", senderDeviceId = "b")
        val phone = Message.Text(3, Origin.PHONE, 0, "z")
        val legacy = Message.Text(4, Origin.BROWSER, 0, "w")
        assertEquals(listOf("a", "b", null, LEGACY_BROWSER_SENDER), listOf(a1, b1, phone, legacy).map { it.peerSenderKey() })
        assertEquals(listOf("a", "b", PHONE_SENDER), listOf(a1, b1, phone).map { it.groupKey() })
    }
}
