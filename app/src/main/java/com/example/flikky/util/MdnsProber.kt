package com.example.flikky.util

sealed interface ProbeResult {
    data class Owned(val number: Int) : ProbeResult
    data object Exhausted : ProbeResult
    data object Cancelled : ProbeResult
}

/**
 * 探测状态机（RFC 6762 §8.1 的最小子集，spec §4.3）。阻塞式：跑在响应器自己的线程上。
 *
 * 对每个候选名：发探测 → 收 250ms → 重复 3 次；任一窗口里出现冲突就换下一个。
 * [collect] 负责真正等待这么久（生产实现是带超时的 socket 接收；测试里直接返回脚本）。
 * [isCancelled] 在每个窗口之后检查：用户在探测中途停止服务时必须立刻退出。
 */
class MdnsProber(
    private val send: (ByteArray) -> Unit,
    private val collect: (windowMs: Long) -> List<MdnsIncoming>,
    private val isCancelled: () -> Boolean = { false },
) {
    fun probe(start: Int, ownIp: Ipv4): ProbeResult {
        for (number in LocalHostName.candidates(start)) {
            val name = LocalHostName.fqdn(number)
            var conflicted = false
            for (attempt in 0 until PROBES) {
                send(MdnsCodec.encodeProbe(name, ownIp))
                val packets = collect(PROBE_WINDOW_MS)
                if (isCancelled()) return ProbeResult.Cancelled
                if (packets.any { isNameConflict(it, name, ownIp) }) {
                    conflicted = true
                    break
                }
            }
            if (!conflicted) return ProbeResult.Owned(number)
        }
        return ProbeResult.Exhausted
    }

    companion object {
        const val PROBES = 3
        const val PROBE_WINDOW_MS = 250L
    }
}
