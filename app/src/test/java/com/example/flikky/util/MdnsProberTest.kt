package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MdnsProberTest {
    private val own = Ipv4.parse("192.168.1.37")!!
    private val other = Ipv4.parse("192.168.1.10")!!
    private val sent = mutableListOf<ByteArray>()

    private fun claim(number: Int, from: Ipv4 = other, ip: Ipv4 = other, ttl: Long = 120) = MdnsIncoming(
        MdnsMessage(0, true, emptyList(), listOf(MdnsARecord(LocalHostName.fqdn(number), ip, ttl, true)), emptyList()),
        from, 5353,
    )

    /** 按窗口序号给出该窗口收到的包；超出脚本的窗口什么也收不到。 */
    private fun prober(script: Map<Int, List<MdnsIncoming>>, cancelAfterWindows: Int = Int.MAX_VALUE): MdnsProber {
        var window = 0
        return MdnsProber(
            send = { sent += it },
            collect = { ms -> assertEquals(250L, ms); script[window++].orEmpty() },
            isCancelled = { window >= cancelAfterWindows },
        )
    }

    @Test fun `a quiet network owns the first name after three probes`() {
        assertEquals(ProbeResult.Owned(37), prober(emptyMap()).probe(37, own))
        assertEquals(3, sent.size)
        assertEquals("flikky37.local", MdnsCodec.decode(sent[0])!!.questions.single().name)
    }

    @Test fun `a conflict moves to the next number`() {
        assertEquals(ProbeResult.Owned(38), prober(mapOf(0 to listOf(claim(37)))).probe(37, own))
    }

    @Test fun `a conflict on the last probe still counts`() {
        assertEquals(ProbeResult.Owned(38), prober(mapOf(2 to listOf(claim(37)))).probe(37, own))
    }

    @Test fun `ten conflicts in a row exhaust the candidates`() {
        // 每个候选在它的第一个窗口就撞：窗口序号 = 候选序号（撞了就不再发后两次探测）。
        val script = (0 until 10).associateWith { i -> listOf(claim(37 + i)) }
        assertEquals(ProbeResult.Exhausted, prober(script).probe(37, own))
    }

    @Test fun `candidates wrap past 999`() {
        assertEquals(ProbeResult.Owned(0), prober(mapOf(0 to listOf(claim(999)))).probe(999, own))
    }

    @Test fun `our previous instance echoing back does not cost us the name`() {
        // 停止后立即重启：旧实例的宣告和 goodbye 经组播回环进来。
        val echoes = listOf(claim(37, from = own, ip = own), claim(37, from = own, ip = own, ttl = 0))
        assertEquals(ProbeResult.Owned(37), prober(mapOf(0 to echoes, 1 to echoes)).probe(37, own))
    }

    @Test fun `someone else caching our address is not a conflict`() {
        assertEquals(ProbeResult.Owned(37), prober(mapOf(0 to listOf(claim(37, from = other, ip = own)))).probe(37, own))
    }

    @Test fun `cancellation stops probing promptly`() {
        assertEquals(ProbeResult.Cancelled, prober(emptyMap(), cancelAfterWindows = 1).probe(37, own))
        assertEquals(1, sent.size)
    }
}
