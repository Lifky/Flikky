package com.example.flikky.util

import java.io.ByteArrayOutputStream

data class MdnsQuestion(val name: String, val type: Int, val klass: Int, val unicastResponse: Boolean)

/** 只保留 A/IN 记录；其余类型在解码时按 RDLENGTH 跳过。 */
data class MdnsARecord(val name: String, val ipv4: Ipv4, val ttl: Long, val cacheFlush: Boolean)

data class MdnsMessage(
    val id: Int,
    val isResponse: Boolean,
    val questions: List<MdnsQuestion>,
    val answers: List<MdnsARecord>,
    /** 探测报文在 authority 段携带提议的记录；同时探测时靠它比大小（spec §4.3）。 */
    val authorities: List<MdnsARecord>,
)

/**
 * 我们需要的那一小部分 mDNS 报文（spec §4.7）。
 *
 * 解码面对的是局域网里任何人都能发来的字节：所有长度、计数、指针都先校验再用，
 * 任何越界都丢弃整包（返回 null），**绝不抛**。附加段（additional）不解析。
 */
object MdnsCodec {
    const val MDNS_PORT = 5353
    const val MAX_PACKET = 9000
    const val TYPE_A = 1
    const val TYPE_ANY = 255
    const val CLASS_IN = 1
    private const val MAX_QUESTIONS = 32
    private const val MAX_RECORDS = 64
    private const val MAX_NAME_LENGTH = 255

    private class Malformed : Exception() {
        override fun fillInStackTrace(): Throwable = this // 丢包是常态，不要栈
    }

    private class Reader(private val d: ByteArray, private val len: Int) {
        var off = 0
        fun need(n: Int) { if (n < 0 || off + n > len) throw Malformed() }
        fun u16(): Int {
            need(2)
            val v = (d[off].toInt() and 0xFF shl 8) or (d[off + 1].toInt() and 0xFF)
            off += 2
            return v
        }
        fun u32(): Long { val hi = u16().toLong(); return (hi shl 16) or u16().toLong() }
        fun skip(n: Int) { need(n); off += n }
        fun ipv4(): Ipv4 { need(4); val ip = Ipv4.fromBytes(d, off); off += 4; return ip }

        /**
         * 读名字，跟随压缩指针。指针只许**指向前文**（严格更早的位置）——
         * 这一条同时排除了自指与环，不再需要单独的跳转计数。
         */
        fun name(): String {
            val sb = StringBuilder()
            var pos = off
            var resumeAt = -1
            var limit = pos // 下一个指针必须指向 < limit 的位置
            while (true) {
                if (pos >= len) throw Malformed()
                val l = d[pos].toInt() and 0xFF
                when {
                    l == 0 -> { pos += 1; break }
                    l and 0xC0 == 0xC0 -> {
                        if (pos + 1 >= len) throw Malformed()
                        val target = (l and 0x3F shl 8) or (d[pos + 1].toInt() and 0xFF)
                        if (target >= limit) throw Malformed()
                        if (resumeAt < 0) resumeAt = pos + 2
                        limit = target
                        pos = target
                    }
                    l and 0xC0 != 0 -> throw Malformed() // 0x40/0x80 保留标签类型
                    else -> {
                        if (pos + 1 + l > len) throw Malformed()
                        if (sb.isNotEmpty()) sb.append('.')
                        sb.append(String(d, pos + 1, l, Charsets.UTF_8))
                        if (sb.length > MAX_NAME_LENGTH) throw Malformed()
                        pos += 1 + l
                    }
                }
            }
            off = if (resumeAt >= 0) resumeAt else pos
            return sb.toString()
        }
    }

    fun decode(data: ByteArray, length: Int = data.size): MdnsMessage? {
        if (length > MAX_PACKET || length > data.size || length < 12) return null
        return try {
            val r = Reader(data, length)
            val id = r.u16()
            val flags = r.u16()
            val qd = r.u16()
            val an = r.u16()
            val ns = r.u16()
            r.u16() // ARCOUNT 不用
            if (qd > MAX_QUESTIONS || an + ns > MAX_RECORDS) return null
            val questions = List(qd) {
                val name = r.name()
                val type = r.u16()
                val cls = r.u16()
                MdnsQuestion(name, type, cls and 0x7FFF, cls and 0x8000 != 0)
            }
            fun records(n: Int): List<MdnsARecord> {
                val out = ArrayList<MdnsARecord>(n)
                repeat(n) {
                    val name = r.name()
                    val type = r.u16()
                    val cls = r.u16()
                    val ttl = r.u32()
                    val rdLen = r.u16()
                    if (type == TYPE_A && cls and 0x7FFF == CLASS_IN && rdLen == 4) {
                        out += MdnsARecord(name, r.ipv4(), ttl, cls and 0x8000 != 0)
                    } else {
                        r.skip(rdLen)
                    }
                }
                return out
            }
            val answers = records(an)
            val authorities = records(ns)
            MdnsMessage(id, flags and 0x8000 != 0, questions, answers, authorities)
        } catch (_: Malformed) {
            null
        }
    }

    fun encodeAResponse(
        id: Int,
        name: String,
        ip: Ipv4,
        ttl: Long,
        cacheFlush: Boolean,
        echoQuestion: Boolean,
    ): ByteArray = Writer().apply {
        u16(id); u16(0x8400) // QR=1, AA=1
        u16(if (echoQuestion) 1 else 0); u16(1); u16(0); u16(0)
        if (echoQuestion) { name(name); u16(TYPE_A); u16(CLASS_IN) }
        aRecord(name, ip, ttl, cacheFlush)
    }.bytes()

    fun encodeProbe(name: String, ip: Ipv4): ByteArray = Writer().apply {
        u16(0); u16(0)
        u16(1); u16(0); u16(1); u16(0)
        name(name); u16(TYPE_ANY); u16(0x8000 or CLASS_IN) // QU
        aRecord(name, ip, ttl = 120, cacheFlush = false)   // authority：提议的记录
    }.bytes()

    private class Writer {
        private val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8 and 0xFF); out.write(v and 0xFF) }
        fun u32(v: Long) { u16((v ushr 16).toInt() and 0xFFFF); u16(v.toInt() and 0xFFFF) }
        fun name(n: String) {
            n.split('.').forEach { label ->
                val b = label.toByteArray(Charsets.UTF_8)
                require(b.size in 1..63) { "bad label: $label" }
                out.write(b.size); out.write(b)
            }
            out.write(0)
        }
        fun aRecord(n: String, ip: Ipv4, ttl: Long, cacheFlush: Boolean) {
            name(n); u16(TYPE_A); u16(if (cacheFlush) 0x8000 or CLASS_IN else CLASS_IN)
            u32(ttl); u16(4); out.write(ip.toBytes())
        }
        fun bytes(): ByteArray = out.toByteArray()
    }
}
