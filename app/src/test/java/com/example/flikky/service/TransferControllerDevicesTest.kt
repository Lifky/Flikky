package com.example.flikky.service

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.routes.WsHub
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferControllerDevicesTest {
    private fun registry() = DeviceRegistry(idSupplier = { "dev-1" }).also { it.issue(null, viaPin = true, token = "T") }

    private fun controller(hub: () -> WsHub?, registry: DeviceRegistry, revoked: () -> Unit = {}) = TransferController(
        session = SessionState(nowMs = { 0L }).apply { startNew(1L) },
        stats = TransferStats(nowMs = { 0L }),
        fileStore = mockk(relaxed = true),
        repository = mockk(relaxed = true),
        wsHub = hub,
        nowMs = { 0L },
        senderId = "phone-test",
        scope = TestScope(),
        devices = registry,
        onDeviceRevoked = revoked,
    )

    @Test fun `renaming a device pushes the new record only to that device`() = runTest {
        val hub = mockk<WsHub>(relaxed = true)
        val r = registry()
        controller({ hub }, r).renameDevice("dev-1", "  张三的笔记本 ")
        assertEquals("张三的笔记本", r.device("dev-1")!!.name)
        coVerify(exactly = 1) { hub.sendToDevice("dev-1", "device_updated", match { "张三的笔记本" in it }) }
    }

    @Test fun `changing a device avatar from the phone marks it as that device's own`() = runTest {
        val r = registry()
        controller({ mockk(relaxed = true) }, r).setDeviceAvatar("dev-1", "icon:star")
        assertTrue(r.device("dev-1")!!.avatarExplicit)
        assertEquals("icon:star", r.device("dev-1")!!.avatarKey)
    }

    @Test fun `an unacceptable avatar key is ignored`() = runTest {
        val r = registry()
        controller({ mockk(relaxed = true) }, r).setDeviceAvatar("dev-1", "<b>")
        assertFalse(r.device("dev-1")!!.avatarExplicit)
    }

    @Test fun `disconnecting revokes the credential, closes the device and reports back`() = runTest {
        val hub = mockk<WsHub>(relaxed = true)
        val r = registry()
        var reported = 0
        controller({ hub }, r) { reported++ }.disconnectDevice("dev-1")
        assertNull(r.resolve("T"))
        coVerify(exactly = 1) { hub.closeDevice("dev-1") }
        assertEquals(1, reported)
    }

    @Test fun `disconnecting an unknown device does nothing`() = runTest {
        val hub = mockk<WsHub>(relaxed = true)
        var reported = 0
        controller({ hub }, DeviceRegistry()) { reported++ }.disconnectDevice("nope")
        coVerify(exactly = 0) { hub.closeDevice(any()) }
        assertEquals(0, reported)
    }
}
