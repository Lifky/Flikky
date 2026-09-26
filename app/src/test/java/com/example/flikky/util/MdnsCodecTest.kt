package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MdnsCodecTest {
    private val ip = Ipv4.parse("192.168.1.37")!!

    /** 手工拼一个查询，模拟 Windows / Android 发来的报文。 */
    private fun query(
        name: String,
        type: Int = MdnsCodec.TYPE_A,
        qu: Boolean = false,
        id: Int = 0,
        compressSecond: Boolean = false,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        fun name(n: String) { n.split('.').forEach { out.write(it.length); out.write(it.toByteArray()) }; out.write(0) }
        u16(id); u16(0); u16(if (compressSecond) 2 else 1); u16(0); u16(0); u16(0)
        name(name); u16(type); u16(if (qu) 0x8001 else 0x0001)
        if (compressSecond) { out.write(0xC0); out.write(12); u16(MdnsCodec.TYPE_ANY); u16(1) }
        return out.toByteArray()
    }

    @Test fun `decodes a plain A query`() {
        val m = MdnsCodec.decode(query("flikky37.local"))!!
        assertFalse(m.isResponse)
        assertEquals(listOf(MdnsQuestion("flikky37.local", 1, 1, false)), m.questions)
    }

    @Test fun `decodes the QU bit and keeps the class separate`() {
        val q = MdnsCodec.decode(query("flikky37.local", qu = true))!!.questions.single()
        assertTrue(q.unicastResponse)
        assertEquals(MdnsCodec.CLASS_IN, q.klass)
    }

    @Test fun `follows a compression pointer`() {
        val m = MdnsCodec.decode(query("flikky37.local", compressSecond = true))!!
        assertEquals(listOf("flikky37.local", "flikky37.local"), m.questions.map { it.name })
        assertEquals(MdnsCodec.TYPE_ANY, m.questions[1].type)
    }

    @Test fun `a multicast answer round trips`() {
        val bytes = MdnsCodec.encodeAResponse(0, "flikky37.local", ip, 120, cacheFlush = true, echoQuestion = false)
        val m = MdnsCodec.decode(bytes)!!
        assertTrue(m.isResponse)
        assertEquals(0, m.id)
        assertEquals(emptyList<MdnsQuestion>(), m.questions)
        assertEquals(listOf(MdnsARecord("flikky37.local", ip, 120, cacheFlush = true)), m.answers)
    }

    @Test fun `a legacy unicast answer keeps the id, echoes the question and drops cache flush`() {
        val bytes = MdnsCodec.encodeAResponse(31165, "flikky37.local", ip, 10, cacheFlush = false, echoQuestion = true)
        val m = MdnsCodec.decode(bytes)!!
        assertEquals(31165, m.id)
        assertEquals(listOf(MdnsQuestion("flikky37.local", 1, 1, false)), m.questions)
        assertFalse(m.answers.single().cacheFlush)
        assertEquals(10L, m.answers.single().ttl)
    }

    @Test fun `a probe asks QM ANY and proposes our record in authority`() {
        // QM 而非 QU：我们只有组播收包 socket，发给 IP:5353 的单播应答收不到（spec §4.3 修订）。
        val m = MdnsCodec.decode(MdnsCodec.encodeProbe("flikky37.local", ip))!!
        assertFalse(m.isResponse)
        assertEquals(listOf(MdnsQuestion("flikky37.local", MdnsCodec.TYPE_ANY, 1, false)), m.questions)
        assertEquals(ip, m.authorities.single().ipv4)
    }

    @Test fun `non A records are skipped, not failed`() {
        // 应答段里一条 TXT（type 16）后跟一条 A：TXT 跳过，A 保留。
        val out = java.io.ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        fun name(n: String) { n.split('.').forEach { out.write(it.length); out.write(it.toByteArray()) }; out.write(0) }
        u16(0); u16(0x8400); u16(0); u16(2); u16(0); u16(0)
        name("x.local"); u16(16); u16(1); u16(0); u16(120); u16(3); out.write(byteArrayOf(2, 'h'.code.toByte(), 'i'.code.toByte()))
        name("flikky37.local"); u16(1); u16(0x8001); u16(0); u16(120); u16(4); out.write(ip.toBytes())
        val m = MdnsCodec.decode(out.toByteArray())!!
        assertEquals(listOf("flikky37.local"), m.answers.map { it.name })
    }

    @Test fun `truncated packets are dropped`() {
        val full = query("flikky37.local")
        for (len in 0 until full.size) assertNull("len=$len", MdnsCodec.decode(full, len))
    }

    @Test fun `a self pointing compression pointer is dropped`() {
        val bytes = query("flikky37.local")
        bytes[12] = 0xC0.toByte(); bytes[13] = 12 // 指向自己
        assertNull(MdnsCodec.decode(bytes))
    }

    @Test fun `a forward pointer is dropped`() {
        val bytes = query("flikky37.local")
        bytes[12] = 0xC0.toByte(); bytes[13] = 20
        assertNull(MdnsCodec.decode(bytes))
    }

    @Test fun `oversize packets are dropped`() {
        assertNull(MdnsCodec.decode(ByteArray(MdnsCodec.MAX_PACKET + 1)))
    }

    @Test fun `absurd section counts are dropped without allocating`() {
        val bytes = query("flikky37.local")
        bytes[4] = 0xFF.toByte(); bytes[5] = 0xFF.toByte() // QDCOUNT = 65535
        assertNull(MdnsCodec.decode(bytes))
    }

    @Test fun `random bytes never throw`() {
        val random = Random(42)
        repeat(10_000) {
            val bytes = random.nextBytes(random.nextInt(0, 600))
            MdnsCodec.decode(bytes) // 只要不抛
        }
    }

    @Test fun `mutated valid packets never throw`() {
        val random = Random(1234)
        val seed = MdnsCodec.encodeProbe("flikky37.local", ip)
        repeat(10_000) {
            val b = seed.copyOf()
            repeat(random.nextInt(1, 6)) { b[random.nextInt(b.size)] = random.nextInt(256).toByte() }
            MdnsCodec.decode(b, random.nextInt(0, b.size + 1))
        }
        assertNotNull(MdnsCodec.decode(seed))
    }

    /** [n] 个问题：第 0 个是真名字，第 k 个的名字是指向第 k-1 个名字的指针 —— 读第 k 个要跳 k 次。 */
    private fun pointerChain(n: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        u16(0); u16(0); u16(n); u16(0); u16(0); u16(0)
        var prev = out.size()
        out.write(1); out.write('a'.code); out.write(5); out.write("local".toByteArray()); out.write(0)
        u16(MdnsCodec.TYPE_A); u16(MdnsCodec.CLASS_IN)
        repeat(n - 1) {
            val here = out.size()
            u16(0xC000 or prev); u16(MdnsCodec.TYPE_A); u16(MdnsCodec.CLASS_IN)
            prev = here
        }
        return out.toByteArray()
    }

    @Test fun `a name reached in 16 pointer jumps still decodes`() {
        val m = MdnsCodec.decode(pointerChain(17))
        assertEquals(17, m!!.questions.size)
        assertEquals("a.local", m.questions.last().name)
    }

    @Test fun `a name that needs more than 16 pointer jumps drops the packet`() {
        // spec §4.7：跳转上限 16。只靠「指针只许往前」挡得住环，挡不住一条几千跳的长链。
        assertNull(MdnsCodec.decode(pointerChain(18)))
    }
}
