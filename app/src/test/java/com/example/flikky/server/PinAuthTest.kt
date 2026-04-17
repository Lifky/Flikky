package com.example.flikky.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinAuthTest {
    private var now = 0L
    private lateinit var auth: PinAuth

    @Before
    fun setUp() {
        now = 1_000L
        auth = PinAuth(nowMs = { now }, pinSupplier = { "123456" }, tokenSupplier = { "TOKEN" })
    }

    @Test
    fun `correct pin issues token and invalidates pin`() {
        val r1 = auth.tryConsume("123456")
        assertEquals(PinAuth.Result.Ok("TOKEN"), r1)
        val r2 = auth.tryConsume("123456")
        assertTrue(r2 is PinAuth.Result.PinAlreadyUsed)
    }

    @Test
    fun `wrong pin increments counter, locks after 3`() {
        assertTrue(auth.tryConsume("000000") is PinAuth.Result.Wrong)
        assertTrue(auth.tryConsume("111111") is PinAuth.Result.Wrong)
        val locked = auth.tryConsume("222222")
        assertTrue(locked is PinAuth.Result.Locked)
        val stillLocked = auth.tryConsume("123456")
        assertTrue(stillLocked is PinAuth.Result.Locked)
    }

    @Test
    fun `lock expires after 30 seconds`() {
        repeat(3) { auth.tryConsume("000000") }
        now += 30_000L
        val r = auth.tryConsume("123456")
        assertEquals(PinAuth.Result.Ok("TOKEN"), r)
    }

    @Test
    fun `5 wrong attempts across locks terminate auth`() {
        repeat(5) { auth.tryConsume("000000") }
        val r = auth.tryConsume("123456")
        assertTrue(r is PinAuth.Result.Terminated)
    }

    @Test
    fun `validateToken recognizes issued token`() {
        auth.tryConsume("123456")
        assertTrue(auth.validateToken("TOKEN"))
        assertFalse(auth.validateToken("other"))
    }

    @Test
    fun `validateToken returns false before any pin consumed`() {
        assertFalse(auth.validateToken("anything"))
    }

    @Test
    fun `currentPin exposes the pin before use and null after`() {
        assertEquals("123456", auth.currentPin())
        auth.tryConsume("123456")
        assertNull(auth.currentPin())
    }
}
