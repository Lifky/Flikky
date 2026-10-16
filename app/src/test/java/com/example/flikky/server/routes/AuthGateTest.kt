package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthGateTest {
    private fun pin() = PinAuth(nowMs = { 0L }, pinSupplier = { "123456" }, tokenSupplier = { "PIN-TOKEN" })

    @Test fun `a correct pin grants a visible device bound to the issued token`() {
        val registry = DeviceRegistry(idSupplier = { "dev-1" })
        val gate = AuthGate(required = true, pinAuth = pin(), registry = registry)
        val granted = gate.authenticate("123456", userAgent = null) as AuthAttempt.Granted
        assertEquals("PIN-TOKEN", granted.issued.token)
        assertEquals("dev-1", gate.deviceFor("PIN-TOKEN")?.deviceId)
        assertTrue(registry.devices.value.single().visible)
    }

    @Test fun `a wrong pin is denied and issues nothing`() {
        val registry = DeviceRegistry()
        val gate = AuthGate(required = true, pinAuth = pin(), registry = registry)
        val denied = gate.authenticate("000000", userAgent = null) as AuthAttempt.Denied
        assertEquals(PinAuth.Result.Wrong, denied.result)
        assertFalse(registry.hasCredentials())
    }

    @Test fun `with pin required nothing is issued without a pin`() {
        assertNull(AuthGate(required = true, pinAuth = pin()).issueOpen(null))
    }

    @Test fun `with pin off a request without a valid cookie is not authorized`() {
        val gate = AuthGate(required = false, pinAuth = pin(), registry = DeviceRegistry())
        assertFalse(gate.isAuthorized(null))
        assertFalse(gate.isAuthorized("guess"))
    }

    @Test fun `with pin off an open device authorizes its own token only`() {
        val gate = AuthGate(required = false, pinAuth = pin(), registry = DeviceRegistry())
        val issued = gate.issueOpen(null)!!
        assertTrue(gate.isAuthorized(issued.token))
        assertFalse(issued.device.visible)
    }
}
