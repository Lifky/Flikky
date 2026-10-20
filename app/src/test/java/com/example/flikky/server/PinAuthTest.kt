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
    fun `new host gets a fresh single-use pin`() {
        auth.tryConsume("123456")
        auth.renewPin("654321")
        assertEquals("654321", auth.currentPin())
        assertTrue(auth.tryConsume("123456") is PinAuth.Result.Wrong)
        assertTrue(auth.tryConsume("654321") is PinAuth.Result.Ok)
        assertTrue(auth.tryConsume("654321") is PinAuth.Result.PinAlreadyUsed)
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
    fun `currentPin exposes the pin before use and null after`() {
        assertEquals("123456", auth.currentPin())
        auth.tryConsume("123456")
        assertNull(auth.currentPin())
    }

    @Test
    fun `a disconnect issues a new pin but keeps the brute force counters`() {
        assertTrue(auth.tryConsume("000000") is PinAuth.Result.Wrong)
        assertTrue(auth.tryConsume("111111") is PinAuth.Result.Wrong)
        assertTrue(auth.tryConsume("123456") is PinAuth.Result.Ok)
        auth.issueNextPin("222222")
        assertEquals("222222", auth.currentPin())
        // 窗口内第三次错 → 锁定：计数没有被清零（D75：同 IP 意味着攻击者还在原位）
        assertTrue(auth.tryConsume("999999") is PinAuth.Result.Locked)
    }

    @Test
    fun `a terminated auth stays terminated after a new pin`() {
        repeat(5) { auth.tryConsume("000000") }
        auth.issueNextPin("222222")
        assertTrue(auth.tryConsume("222222") is PinAuth.Result.Terminated)
    }

    @Test
    fun `a new host resets the counters`() {
        auth.tryConsume("000000")
        auth.tryConsume("111111")
        auth.renewPin("654321")
        assertTrue(auth.tryConsume("999999") is PinAuth.Result.Wrong)
    }

    @Test
    fun `a next pin is needed only once the pin is used and nobody holds a credential`() {
        assertFalse(auth.needsNextPin(hasCredentials = false))
        auth.tryConsume("123456")
        assertFalse(auth.needsNextPin(hasCredentials = true))
        assertTrue(auth.needsNextPin(hasCredentials = false))
    }

    @Test
    fun `pin auth keeps no copy of the issued token`() {
        auth.tryConsume("123456")
        val held = PinAuth::class.java.declaredFields.map { f ->
            f.isAccessible = true
            f.get(auth)?.toString().orEmpty()
        }
        assertTrue(held.none { it == "TOKEN" })
    }
    // v1.24（B59#5）：输错 5 次后 PIN 停用到换 IP 为止。手机卡片据此不再显示一串用不了的数字。
    @Test
    fun `five wrong attempts block the pin until the host changes`() {
        assertFalse(auth.blocked.value)
        repeat(PinAuth.TERMINATE_THRESHOLD) {
            now += PinAuth.LOCK_DURATION_MS + 1
            auth.tryConsume("000000")
        }
        assertTrue(auth.blocked.value)
        auth.renewPin("654321")
        assertFalse(auth.blocked.value)
    }

    @Test
    fun `a pin blocked after a disconnect stays blocked and is not replaced`() {
        auth.tryConsume("123456")          // 第一台登录
        auth.issueNextPin("222222")        // 手机断开它，换发（D80）
        repeat(PinAuth.TERMINATE_THRESHOLD) {
            now += PinAuth.LOCK_DURATION_MS + 1
            auth.tryConsume("000000")
        }
        assertTrue(auth.blocked.value)
        assertFalse("a fresh pin would only meet Terminated", auth.needsNextPin(hasCredentials = false))
        assertEquals(PinAuth.Result.Terminated, auth.tryConsume("222222"))
    }
}
