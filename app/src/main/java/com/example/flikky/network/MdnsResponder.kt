package com.example.flikky.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.flikky.session.LocalNameStatus
import com.example.flikky.util.Ipv4
import com.example.flikky.util.LocalHostName
import com.example.flikky.util.MdnsCodec
import com.example.flikky.util.MdnsIncoming
import com.example.flikky.util.MdnsProber
import com.example.flikky.util.MdnsResponderPolicy
import com.example.flikky.util.MdnsSend
import com.example.flikky.util.ProbeResult
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * 最小 mDNS 响应器：把 `flikky{X}.local` 解析到当前绑定 IP（spec §4，CLAUDE.md 安全红线）。
 *
 * 两个 socket（前置实验结论）：收包绑 `224.0.0.251:5353`；发包绑 `当前IP:5353`
 * —— 绑在组播地址上的 socket 发不出包（EINVAL）。**任何 socket 都不绑 0.0.0.0**。
 *
 * 由 TransferService 持有、跨 rebind 存活；不认识 KtorServer，IP 只经参数传入。
 * 所有方法由服务线程串行调用（D75）。
 */
class MdnsResponder(
    context: Context,
    private val onStatus: (LocalNameStatus) -> Unit,
) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lock: WifiManager.MulticastLock? = null
    private var worker: Worker? = null
    private var requestedNumber = 0

    fun start(ip: String, number: Int) {
        stop()
        requestedNumber = number
        acquireLock()
        worker = Worker(ip, number, probe = true).also { it.start() }
    }

    /**
     * IP 变了：名字已确认过就不再探测、直接在新网卡上宣告；
     * 还没确认（探测途中 rebind）就在新 IP 上重新探测。
     */
    fun updateIp(ip: String) {
        val previous = worker ?: return
        val owned = previous.ownedNumber
        previous.shutdown()
        worker = if (owned != null) {
            Worker(ip, owned, probe = false, wanted = requestedNumber)
        } else {
            Worker(ip, requestedNumber, probe = true)
        }.also { it.start() }
    }

    /** 同步且有界：返回时两个 socket 已关、线程已退出（或已超过 1s 上限）。 */
    fun stop() {
        worker?.shutdown()
        worker = null
        runCatching { lock?.release() }
        lock = null
        onStatus(LocalNameStatus.Disabled)
    }

    private fun acquireLock() {
        if (lock != null) return
        lock = runCatching {
            wifi.createMulticastLock("flikky-mdns").apply { setReferenceCounted(false); acquire() }
        }.onFailure { Log.w(TAG, "multicast lock failed", it) }.getOrNull()
    }

    private inner class Worker(
        private val ipText: String,
        private val number: Int,
        private val probe: Boolean,
        private val wanted: Int = number,
    ) : Thread("flikky-mdns") {
        @Volatile private var running = true
        @Volatile var ownedNumber: Int? = null
            private set
        @Volatile private var receiver: MulticastSocket? = null
        @Volatile private var sender: MulticastSocket? = null
        private val policy = MdnsResponderPolicy(nowMs = System::currentTimeMillis)

        init { isDaemon = true }

        fun shutdown() {
            // 先发 goodbye、再置 running=false：反过来的话，工作线程可能先退出并在 finally 里关掉 sender。
            val owned = ownedNumber
            val ip = Ipv4.parse(ipText)
            if (owned != null && ip != null) {
                runCatching { send(MdnsSend.Multicast(policy.goodbye(LocalHostName.fqdn(owned), ip))) }
            }
            running = false
            runCatching { receiver?.close() }
            runCatching { sender?.close() }
            join(STOP_JOIN_MS)
            if (isAlive) Log.w(TAG, "responder thread did not exit within ${STOP_JOIN_MS}ms")
        }

        /** 只在仍是当前实例时上报：被 shutdown 之后的迟到状态不能覆盖新实例的状态。 */
        private fun report(status: LocalNameStatus) {
            if (running) onStatus(status)
        }

        override fun run() {
            val ip = Ipv4.parse(ipText)
            val addr = runCatching { InetAddress.getByName(ipText) }.getOrNull()
            val iface = addr?.let { runCatching { NetworkInterface.getByInetAddress(it) }.getOrNull() }
            if (ip == null || addr == null || iface == null) {
                Log.w(TAG, "no interface for $ipText")
                report(LocalNameStatus.Unavailable)
                return
            }
            val prefix = iface.interfaceAddresses.firstOrNull { it.address == addr }
                ?.networkPrefixLength?.toInt() ?: 24
            try {
                receiver = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(GROUP, MdnsCodec.MDNS_PORT))
                    networkInterface = iface
                    joinGroup(InetSocketAddress(GROUP, MdnsCodec.MDNS_PORT), iface)
                    soTimeout = RECEIVE_TIMEOUT_MS
                }
                sender = MulticastSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(addr, MdnsCodec.MDNS_PORT))
                    networkInterface = iface
                    timeToLive = 255
                }
            } catch (e: Exception) {
                Log.w(TAG, "socket setup failed on ${iface.name}", e)
                runCatching { receiver?.close() }
                runCatching { sender?.close() }
                report(LocalNameStatus.Unavailable)
                return
            }
            // 任何退出路径（探测用尽、取消、收包出错）都关掉 socket，别留一个不应答的监听点。
            try {
                serve(ip, prefix)
            } finally {
                runCatching { receiver?.close() }
                runCatching { sender?.close() }
            }
        }

        private fun serve(ip: Ipv4, prefix: Int) {
            if (!running) return
            val finalNumber = if (probe) {
                report(LocalNameStatus.Probing(number))
                val prober = MdnsProber(
                    send = { bytes -> runCatching { send(MdnsSend.Multicast(bytes)) } },
                    collect = { ms -> collect(ms) },
                    isCancelled = { !running },
                )
                when (val r = prober.probe(number, ip)) {
                    is ProbeResult.Owned -> r.number
                    ProbeResult.Exhausted -> { report(LocalNameStatus.Unavailable); return }
                    ProbeResult.Cancelled -> return
                }
            } else {
                number
            }
            if (!running) return
            ownedNumber = finalNumber
            report(
                if (finalNumber == wanted) LocalNameStatus.Owned(finalNumber)
                else LocalNameStatus.Renamed(wanted, finalNumber),
            )

            val name = LocalHostName.fqdn(finalNumber)
            repeat(ANNOUNCEMENTS) { i ->
                if (!running) return
                runCatching { send(MdnsSend.Multicast(policy.announcement(name, ip))) }
                if (i < ANNOUNCEMENTS - 1) collect(ANNOUNCE_INTERVAL_MS).forEach { answer(it, name, ip, prefix) }
            }
            while (running) {
                receiveOne()?.let { answer(it, name, ip, prefix) }
            }
        }

        private fun answer(incoming: MdnsIncoming, name: String, ip: Ipv4, prefix: Int) {
            policy.respond(incoming, name, ip, prefix).forEach { runCatching { send(it) } }
        }

        private fun collect(windowMs: Long): List<MdnsIncoming> {
            val out = mutableListOf<MdnsIncoming>()
            val deadline = System.currentTimeMillis() + windowMs
            while (running && System.currentTimeMillis() < deadline) {
                receiveOne()?.let { out += it }
            }
            return out
        }

        private fun receiveOne(): MdnsIncoming? {
            val socket = receiver ?: return null
            val buf = ByteArray(MdnsCodec.MAX_PACKET)
            val pkt = DatagramPacket(buf, buf.size)
            return try {
                socket.receive(pkt)
                val src = Ipv4.parse(pkt.address.hostAddress ?: return null) ?: return null
                MdnsCodec.decode(pkt.data, pkt.length)?.let { MdnsIncoming(it, src, pkt.port) }
            } catch (_: SocketTimeoutException) {
                null
            } catch (e: Exception) {
                if (running) Log.w(TAG, "receive failed", e)
                running = false
                null
            }
        }

        private fun send(action: MdnsSend) {
            val s = sender ?: return
            when (action) {
                is MdnsSend.Multicast -> s.send(
                    DatagramPacket(action.bytes, action.bytes.size, InetSocketAddress(GROUP, MdnsCodec.MDNS_PORT)),
                )
                is MdnsSend.Unicast -> s.send(
                    DatagramPacket(
                        action.bytes, action.bytes.size,
                        InetSocketAddress(InetAddress.getByAddress(action.ip.toBytes()), action.port),
                    ),
                )
            }
        }
    }

    companion object {
        private const val TAG = "MdnsResponder"
        private val GROUP: InetAddress = InetAddress.getByName("224.0.0.251")
        private const val RECEIVE_TIMEOUT_MS = 200
        private const val ANNOUNCEMENTS = 2
        private const val ANNOUNCE_INTERVAL_MS = 1000L
        private const val STOP_JOIN_MS = 1000L
    }
}
