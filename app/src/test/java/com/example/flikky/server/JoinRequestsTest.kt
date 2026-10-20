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
    private var codeSeq = 0
    private fun requests(maxPending: Int = 3, codeSupplier: () -> String = { "%04d".format(++codeSeq) }) = JoinRequests(
        nowMs = { now },
        secretSupplier = { "secret-${++seq}" },
        idSupplier = { "req-$seq" },
        codeSupplier = codeSupplier,
        maxPending = maxPending,
    )

    private fun JoinRequests.isPending(secret: String) = status(secret) is JoinRequests.Status.Pending

    @Test fun `a new request waits for the phone`() {
        val r = requests()
        val created = r.create(userAgent = "UA")!!
        assertTrue(r.isPending(created.secret))
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
        assertTrue(r.isPending(created.secret))
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
    // v1.24 D82：请求方页面与手机显示同一个比对码，用户核对一致再允许。
    @Test fun `each request carries a four digit code the phone and the page both see`() {
        val r = requests(codeSupplier = { "4821" })
        val created = r.create("UA")!!
        assertEquals("4821", created.request.code)
        assertEquals("4821", r.pending.value.single().code)
        assertEquals(JoinRequests.Status.Pending("4821"), r.status(created.secret))
    }

    @Test fun `requests waiting at the same time never share a code`() {
        val codes = ArrayDeque(listOf("1234", "1234", "1234", "5678"))
        val r = requests(codeSupplier = { codes.removeFirst() })
        val first = r.create("a")!!
        val second = r.create("b")!!
        assertEquals("1234", first.request.code)
        assertEquals("a code already on screen is skipped", "5678", second.request.code)
    }

    @Test fun `a code is free again once its request has left the queue`() {
        val r = requests(codeSupplier = { "1234" })
        val first = r.create("a")!!
        r.deny(first.request.id)
        assertEquals("1234", r.create("b")!!.request.code)
    }

    @Test fun `a supplier stuck on taken codes still yields a unique one`() {
        val r = requests(codeSupplier = { "0000" })
        val codes = List(3) { r.create("ua-$it")!!.request.code }
        assertEquals(3, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("""\d{4}""")) })
    }
}
