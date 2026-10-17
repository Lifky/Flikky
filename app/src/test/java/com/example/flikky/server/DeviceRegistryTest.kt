package com.example.flikky.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRegistryTest {
    private val chrome =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private var seq = 0
    private var now = 1_000L
    private fun registry(maxPending: Int = DeviceRegistry.MAX_PENDING) = DeviceRegistry(
        nowMs = { now++ },
        tokenSupplier = { "tok-${++seq}" },
        idSupplier = { "dev-$seq" },
        defaultAvatar = { "icon:desktop_windows" },
        maxPending = maxPending,
    )

    @Test fun `a pin login is visible at once, labelled from the user agent and resolvable by its token`() {
        val r = registry()
        val issued = r.issue(chrome, viaPin = true, token = "PIN-TOKEN")
        assertEquals("PIN-TOKEN", issued.token)
        assertTrue(issued.device.visible)
        assertEquals("Chrome · Windows", issued.device.defaultLabel)
        assertEquals("icon:desktop_windows", issued.device.avatarKey)
        assertEquals(issued.device.deviceId, r.resolve("PIN-TOKEN")?.deviceId)
        assertNull(r.resolve("other"))
        assertNull(r.resolve(null))
        assertEquals(listOf(issued.device.deviceId), r.devices.value.map { it.deviceId })
    }

    @Test fun `an open issue stays hidden until its first websocket`() {
        val r = registry()
        val issued = r.issue(null, viaPin = false)
        assertTrue(r.devices.value.isEmpty())
        r.attach(issued.device.deviceId)
        assertTrue(r.devices.value.single().visible)
        assertTrue(r.devices.value.single().online)
    }

    @Test fun `connections are counted and an offline device stays listed`() {
        val r = registry()
        val id = r.issue(null, viaPin = true).device.deviceId
        r.attach(id); r.attach(id); r.detach(id)
        assertTrue(r.device(id)!!.online)
        r.detach(id)
        assertFalse(r.device(id)!!.online)
        assertEquals(1, r.devices.value.size)
    }

    @Test fun `revoking removes the device and its credential`() {
        val r = registry()
        val issued = r.issue(null, viaPin = true)
        assertTrue(r.revoke(issued.device.deviceId))
        assertNull(r.resolve(issued.token))
        assertTrue(r.devices.value.isEmpty())
        assertFalse(r.revoke(issued.device.deviceId))
        assertFalse(r.hasCredentials())
    }

    @Test fun `revokeAll clears everything`() {
        val r = registry()
        val a = r.issue(null, viaPin = true)
        val b = r.issue(null, viaPin = false)
        r.revokeAll()
        assertNull(r.resolve(a.token)); assertNull(r.resolve(b.token))
        assertFalse(r.hasCredentials())
    }

    @Test fun `a flood of open issues only evicts the oldest pending, never a visible or pin device`() {
        val r = registry(maxPending = 3)
        val pinned = r.issue(null, viaPin = true)
        val seen = r.issue(null, viaPin = false).also { r.attach(it.device.deviceId) }
        val pending = List(3) { r.issue(null, viaPin = false) }
        val extra = r.issue(null, viaPin = false)
        assertNull("oldest pending is evicted", r.resolve(pending[0].token))
        assertNotNull(r.resolve(pending[1].token))
        assertNotNull(r.resolve(extra.token))
        assertNotNull("pin device survives", r.resolve(pinned.token))
        assertNotNull("visible device survives", r.resolve(seen.token))
    }

    @Test fun `ordinals are given on becoming visible and pending issues take no number`() {
        val r = registry()
        val early = r.issue(chrome, viaPin = false)
        val pinned = r.issue(chrome, viaPin = true)
        assertEquals(1, pinned.device.defaultOrdinal)
        r.attach(early.device.deviceId)
        assertEquals(2, r.device(early.device.deviceId)!!.defaultOrdinal)
    }

    @Test fun `ordinals never repeat after a revoke`() {
        val r = registry()
        val a = r.issue(chrome, viaPin = true)
        val b = r.issue(chrome, viaPin = true)
        r.revoke(a.device.deviceId)
        val c = r.issue(chrome, viaPin = true)
        assertEquals(2, r.device(b.device.deviceId)!!.defaultOrdinal)
        assertEquals(3, c.device.defaultOrdinal)
    }

    @Test fun `only the first hello is adopted, later edits still apply`() {
        val r = registry()
        val id = r.issue(null, viaPin = true).device.deviceId
        assertFalse(r.isAnnounced(id))
        r.applyHello(id, name = "旧名字", avatarKey = "icon:star", avatarExplicit = true)
        assertTrue(r.isAnnounced(id))
        r.update(id, name = "手机改的名字")
        r.applyHello(id, name = "旧名字", avatarKey = "icon:face", avatarExplicit = true)
        assertEquals("手机改的名字", r.device(id)!!.name)
        assertEquals("icon:star", r.device(id)!!.avatarKey)
    }

    @Test fun `the phone default reaches only devices without their own pick`() {
        val r = registry()
        val own = r.issue(null, viaPin = true).device.deviceId
        val follow = r.issue(null, viaPin = true).device.deviceId
        r.update(own, avatarKey = "icon:star", avatarExplicit = true)
        val changed = r.applyDefaultAvatar("icon:face")
        assertEquals(listOf(follow), changed.map { it.deviceId })
        assertEquals("icon:star", r.device(own)!!.avatarKey)
        assertEquals("icon:face", r.device(follow)!!.avatarKey)
        assertTrue(r.applyDefaultAvatar("icon:face").isEmpty())
    }

    @Test fun `the registry keeps no plaintext token`() {
        val r = registry()
        r.issue(null, viaPin = true, token = "SECRET-TOKEN-VALUE")
        val held = DeviceRegistry::class.java.declaredFields.map { f ->
            f.isAccessible = true
            f.get(r)?.toString().orEmpty()
        }
        assertTrue(held.none { "SECRET-TOKEN-VALUE" in it })
    }

    @Test fun `the hash is a stable sha-256 hex digest`() {
        assertEquals(64, DeviceRegistry.hashOf("x").length)
        assertEquals(DeviceRegistry.hashOf("x"), DeviceRegistry.hashOf("x"))
        assertFalse(DeviceRegistry.hashOf("x") == DeviceRegistry.hashOf("y"))
    }

    // 默认名只留浏览器名后，Windows 上的 Edge 与 Mac 上的 Edge 都叫「Edge」：编号按名字算，不按完整标签。
    @Test fun `the same browser on different systems still gets distinct names`() {
        val uas = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0",
        )
        var n = 0
        val r = DeviceRegistry(idSupplier = { "d${++n}" })
        val ordinals = uas.map { r.issue(it, viaPin = true).device.defaultOrdinal }
        assertEquals(listOf(1, 2), ordinals)
    }
}
