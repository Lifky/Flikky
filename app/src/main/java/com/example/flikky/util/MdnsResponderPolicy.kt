package com.example.flikky.util

data class MdnsIncoming(val message: MdnsMessage, val srcIp: Ipv4, val srcPort: Int)

/** 发送动作。持有 ByteArray，所以不是 data class —— 测试请解码后比较。 */
sealed interface MdnsSend {
    class Multicast(val bytes: ByteArray) : MdnsSend
    class Unicast(val bytes: ByteArray, val ip: Ipv4, val port: Int) : MdnsSend
}

/**
 * 给定一个收到的报文，决定回什么、回给谁（spec §4.7）。纯逻辑，唯一的状态是组播速率限制。
 *
 * 安全约束都在这里：只认本机名字的 A/ANY + IN；源是本机的包一律不理；
 * 单播（旧式与 QU）只回同子网源地址，防止被当成反射器。
 */
class MdnsResponderPolicy(private val nowMs: () -> Long) {
    private var lastMulticastAt: Long? = null

    fun respond(incoming: MdnsIncoming, name: String, ownIp: Ipv4, prefixLength: Int): List<MdnsSend> {
        val msg = incoming.message
        if (msg.isResponse || incoming.srcIp == ownIp) return emptyList()
        val asked = msg.questions.filter {
            it.name.equals(name, ignoreCase = true) &&
                (it.type == MdnsCodec.TYPE_A || it.type == MdnsCodec.TYPE_ANY) &&
                it.klass == MdnsCodec.CLASS_IN
        }
        if (asked.isEmpty()) return emptyList()
        val onLink = incoming.srcIp.sameSubnet(ownIp, prefixLength)

        if (incoming.srcPort != MdnsCodec.MDNS_PORT) {
            // 旧式单播（RFC 6762 §6.7）：Android 本机解析走的就是这条。
            if (!onLink) return emptyList()
            val bytes = MdnsCodec.encodeAResponse(msg.id, name, ownIp, LEGACY_TTL, cacheFlush = false, echoQuestion = true)
            return listOf(MdnsSend.Unicast(bytes, incoming.srcIp, incoming.srcPort))
        }

        val out = mutableListOf<MdnsSend>()
        val now = nowMs()
        val last = lastMulticastAt
        if (last == null || now - last >= MULTICAST_INTERVAL_MS) {
            out += MdnsSend.Multicast(announcement(name, ownIp))
            lastMulticastAt = now
        }
        if (onLink && asked.any { it.unicastResponse }) {
            out += MdnsSend.Unicast(announcement(name, ownIp), incoming.srcIp, MdnsCodec.MDNS_PORT)
        }
        return out
    }

    fun announcement(name: String, ownIp: Ipv4): ByteArray =
        MdnsCodec.encodeAResponse(0, name, ownIp, TTL, cacheFlush = true, echoQuestion = false)

    fun goodbye(name: String, ownIp: Ipv4): ByteArray =
        MdnsCodec.encodeAResponse(0, name, ownIp, 0, cacheFlush = true, echoQuestion = false)

    companion object {
        const val TTL = 120L
        const val LEGACY_TTL = 10L
        const val MULTICAST_INTERVAL_MS = 1000L
    }
}

/**
 * 这个报文是否说明 [name] 已被别人占用（spec §4.4）。
 *
 * 只有「同名 + 不同 IPv4 + TTL>0」才算。源地址是本机的一律不算 —— 刚停止的旧实例
 * 发出的宣告/goodbye 会经组播回环进来，把它当冲突就是「刚停就启、提示被占用」。
 * 同时探测（RFC 6762 §8.2 的简化版）：对方提议的 IP 比我们大，我们让。
 */
fun isNameConflict(incoming: MdnsIncoming, name: String, ownIp: Ipv4): Boolean {
    if (incoming.srcIp == ownIp) return false
    val m = incoming.message
    if (m.isResponse) {
        return m.answers.any { it.name.equals(name, ignoreCase = true) && it.ttl > 0 && it.ipv4 != ownIp }
    }
    val probingSame = m.questions.any { it.name.equals(name, ignoreCase = true) }
    return probingSame && m.authorities.any {
        it.name.equals(name, ignoreCase = true) && it.ipv4 != ownIp && it.ipv4 > ownIp
    }
}
