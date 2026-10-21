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

    // 悬浮工具栏：气泡挨得紧，同一个人连续几条只在第一条上标（群聊惯例）。时间分隔条后重新开始。
    @Test fun `with the floating toolbar a name heads each run of messages from the same sender`() {
        val senders = listOf("a", "a", "b", PHONE_SENDER, PHONE_SENDER)
        val shown = senders.indices.map { i -> senderLabelAt(senders, i, everyMessage = false) }
        assertEquals(listOf(true, false, true, true, false), shown)
        assertTrue(senderLabelAt(listOf("a", "a"), 1, everyMessage = false, breakBefore = true))
    }

    // 常驻按钮：每条下面都有一排按钮，只标首条会让后面几条上方空出一块，看着像缺了东西（装机反馈）。
    @Test fun `with inline buttons every message carries its name, on both sides`() {
        val senders = listOf("a", "a", PHONE_SENDER)
        assertEquals(listOf(true, true, true), senders.indices.map { senderLabelAt(senders, it, everyMessage = true) })
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
    // v1.24 装机反馈：唯一那台被断开、刷新重进后仍只有一台，名字却出现了 —— 数的是本场出现过的全部设备。
    // 会话页改数仍在会话里的设备（ServingScreen 的 devices = 未被断开的，在线离线都算）。
    @Test fun `the serving chat counts only devices still in the session`() {
        val tab = source("com/example/flikky/ui/serving/ServingChatTab.kt")
        assertTrue(tab.contains("senderNamesEnabled(settings.senderNameMode, peerDeviceCount = presentDeviceCount)"))
        assertTrue(source("com/example/flikky/ui/serving/ServingScreen.kt").contains("presentDeviceCount = devices.size"))
    }

    private fun source(relative: String): String {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { java.io.File(dir, "$it/$relative") }
                .firstOrNull { it.isFile }?.let { return it.readText() }
            dir = dir.parentFile
        }
        error("missing $relative")
    }
}
