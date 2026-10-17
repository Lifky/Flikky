package com.example.flikky.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话里已有设备时，新设备要手机允许才能进（v1.23 装机反馈，用户裁决）。
 * 请求凭证只经 HttpOnly Cookie 交给发起请求的页面；服务端只存它的 SHA-256。
 * 允许时不当场签发会话凭据，等请求方来领时才签发 —— 会话 token 明文从不在服务端停留。
 */
class JoinRequestsTest {
    private var now = 0L
    private var seq = 0
    private fun requests(maxPending: Int = 3) = JoinRequests(
        nowMs = { now },
        secretSupplier = { "secret-${++seq}" },
        idSupplier = { "req-$seq" },
        maxPending = maxPending,
    )

    @Test fun `a new request waits for the phone`() {
        val r = requests()
        val created = r.create(userAgent = "UA")!!
        assertEquals(JoinRequests.Status.Pending, r.status(created.secret))
        assertEquals(listOf(created.request.id), r.pending.value.map { it.id })
    }

    @Test fun `an approved request is claimed exactly once`() {
        val r = requests()
        val created = r.create("UA")!!
        assertTrue(r.approve(created.request.id))
        val claimed = r.claim(created.secret)
        assertNotNull(claimed)
        assertEquals("UA", claimed!!.userAgent)
        assertNull("a second claim must fail", r.claim(created.secret))
        assertEquals(JoinRequests.Status.Unknown, r.status(created.secret))
        assertTrue(r.pending.value.isEmpty())
    }

    @Test fun `a pending request cannot be claimed`() {
        val r = requests()
        val created = r.create("UA")!!
        assertNull(r.claim(created.secret))
        assertEquals(JoinRequests.Status.Pending, r.status(created.secret))
    }

    @Test fun `a denied request says so and leaves the queue`() {
        val r = requests()
        val created = r.create("UA")!!
        assertTrue(r.deny(created.request.id))
        assertEquals(JoinRequests.Status.Denied, r.status(created.secret))
        assertTrue(r.pending.value.isEmpty())
        assertNull(r.claim(created.secret))
    }

    @Test fun `an unanswered request expires after a minute`() {
        val r = requests()
        val created = r.create("UA")!!
        now += JoinRequests.TTL_MS + 1
        assertEquals(JoinRequests.Status.Expired, r.status(created.secret))
        assertTrue(r.pending.value.isEmpty())
        assertTrue("approving an expired request does nothing", !r.approve(created.request.id))
    }

    @Test fun `the queue is capped so a flood cannot bury the phone in dialogs`() {
        val r = requests(maxPending = 2)
        assertNotNull(r.create("a"))
        assertNotNull(r.create("b"))
        assertNull(r.create("c"))
        now += JoinRequests.TTL_MS + 1
        assertNotNull("expired entries free their slots", r.create("d"))
    }

    @Test fun `an unknown or empty secret is unknown`() {
        val r = requests()
        assertEquals(JoinRequests.Status.Unknown, r.status(null))
        assertEquals(JoinRequests.Status.Unknown, r.status("nope"))
    }

    @Test fun `the phone sees a readable label, never the secret`() {
        val r = requests()
        r.create("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
        val view = r.pending.value.single()
        assertEquals("Chrome · Windows", view.label)
        assertTrue(view.toString().contains("secret").not())
    }

    @Test fun `clearing drops everything`() {
        val r = requests()
        val created = r.create("UA")!!
        r.clear()
        assertTrue(r.pending.value.isEmpty())
        assertEquals(JoinRequests.Status.Unknown, r.status(created.secret))
    }
}
