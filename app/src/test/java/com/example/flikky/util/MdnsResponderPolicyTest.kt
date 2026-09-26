package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MdnsResponderPolicyTest {
    private val name = "flikky37.local"
    private val own = Ipv4.parse("192.168.1.37")!!
    private val peer = Ipv4.parse("192.168.1.10")!!
    private val offLink = Ipv4.parse("10.9.9.9")!!
    private var now = 10_000L
    private val policy = MdnsResponderPolicy(nowMs = { now })

    private fun q(
        qName: String = name, type: Int = MdnsCodec.TYPE_A, klass: Int = MdnsCodec.CLASS_IN,
        qu: Boolean = false, id: Int = 0, isResponse: Boolean = false,
    ) = MdnsMessage(id, isResponse, listOf(MdnsQuestion(qName, type, klass, qu)), emptyList(), emptyList())

    private fun respond(msg: MdnsMessage, src: Ipv4 = peer, port: Int = 5353) =
        policy.respond(MdnsIncoming(msg, src, port), name, own, prefixLength = 24)

    private fun MdnsSend.decoded(): MdnsMessage = MdnsCodec.decode(
        when (this) { is MdnsSend.Multicast -> bytes; is MdnsSend.Unicast -> bytes },
    )!!

    @Test fun `a multicast A query gets one multicast cache-flush answer`() {
        val out = respond(q())
        assertEquals(1, out.size)
        assertTrue(out[0] is MdnsSend.Multicast)
        val a = out[0].decoded().answers.single()
        assertEquals(own, a.ipv4); assertTrue(a.cacheFlush); assertEquals(120L, a.ttl)
    }

    @Test fun `ANY is answered like A`() {
        assertEquals(1, respond(q(type = MdnsCodec.TYPE_ANY)).size)
    }

    @Test fun `names are matched case-insensitively`() {
        assertEquals(1, respond(q(qName = "FLIKKY37.LOCAL")).size)
    }

    @Test fun `other names, other types, other classes and responses are ignored`() {
        assertTrue(respond(q(qName = "flikky38.local")).isEmpty())
        assertTrue(respond(q(type = 28)).isEmpty()) // AAAA
        assertTrue(respond(q(klass = 3)).isEmpty())
        assertTrue(respond(q(isResponse = true)).isEmpty())
    }

    @Test fun `packets from our own address are ignored`() {
        assertTrue(respond(q(), src = own).isEmpty())
    }

    @Test fun `QU adds a unicast answer to the asker`() {
        val out = respond(q(qu = true))
        assertEquals(2, out.size)
        val uni = out.filterIsInstance<MdnsSend.Unicast>().single()
        assertEquals(peer, uni.ip); assertEquals(5353, uni.port)
    }

    @Test fun `legacy unicast keeps id, echoes the question, no cache flush, short ttl`() {
        val out = respond(q(id = 31165), port = 8621)
        val uni = out.single() as MdnsSend.Unicast
        assertEquals(peer, uni.ip); assertEquals(8621, uni.port)
        val m = uni.decoded()
        assertEquals(31165, m.id)
        assertEquals(name, m.questions.single().name)
        assertFalse(m.answers.single().cacheFlush)
        assertEquals(10L, m.answers.single().ttl)
    }

    @Test fun `unicast never goes off-link`() {
        assertTrue(respond(q(id = 1), src = offLink, port = 8621).isEmpty())
        val out = respond(q(qu = true), src = offLink)
        assertTrue(out.none { it is MdnsSend.Unicast })
    }

    @Test fun `multicast answers are rate limited to one per second`() {
        assertEquals(1, respond(q()).size)
        now += 999
        assertTrue(respond(q()).isEmpty())
        now += 1
        assertEquals(1, respond(q()).size)
    }

    @Test fun `announcement and goodbye carry the right ttl`() {
        val ann = MdnsCodec.decode(policy.announcement(name, own))!!.answers.single()
        assertEquals(120L, ann.ttl); assertTrue(ann.cacheFlush)
        assertEquals(0L, MdnsCodec.decode(policy.goodbye(name, own))!!.answers.single().ttl)
    }

    // ---- 冲突判定（spec §4.4） ----

    private fun answer(from: Ipv4, ip: Ipv4, ttl: Long = 120, n: String = name) =
        MdnsIncoming(MdnsMessage(0, true, emptyList(), listOf(MdnsARecord(n, ip, ttl, true)), emptyList()), from, 5353)

    @Test fun `same name with a different address is a conflict`() {
        assertTrue(isNameConflict(answer(from = peer, ip = peer), name, own))
    }

    @Test fun `same name with our own address is not a conflict`() {
        assertFalse(isNameConflict(answer(from = peer, ip = own), name, own))
    }

    @Test fun `anything from our own source address is not a conflict`() {
        // 旧实例的宣告/goodbye 回环进来 —— 这是「刚停就启、提示被占用」的来源之一。
        assertFalse(isNameConflict(answer(from = own, ip = peer), name, own))
    }

    @Test fun `a goodbye is not a conflict`() {
        assertFalse(isNameConflict(answer(from = peer, ip = peer, ttl = 0), name, own))
    }

    @Test fun `other names are not a conflict`() {
        assertFalse(isNameConflict(answer(from = peer, ip = peer, n = "flikky38.local"), name, own))
    }

    @Test fun `a simultaneous probe from a larger address wins`() {
        val bigger = Ipv4.parse("192.168.1.200")!!
        val probe = MdnsIncoming(MdnsCodec.decode(MdnsCodec.encodeProbe(name, bigger))!!, bigger, 5353)
        assertTrue(isNameConflict(probe, name, own))
    }

    @Test fun `a simultaneous probe from a smaller address loses`() {
        val smaller = Ipv4.parse("192.168.1.2")!!
        val probe = MdnsIncoming(MdnsCodec.decode(MdnsCodec.encodeProbe(name, smaller))!!, smaller, 5353)
        assertFalse(isNameConflict(probe, name, own))
    }
}
