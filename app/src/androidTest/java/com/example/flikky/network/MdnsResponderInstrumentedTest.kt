package com.example.flikky.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.session.LocalNameStatus
import com.example.flikky.util.MdnsCodec
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 真 socket 上的生命周期守卫（spec §4.4、plan Review Focus 1/2/5）。需要设备连着 Wi-Fi，
 * 没有 Wi-Fi 时跳过 —— **跳过不等于通过**，验收时必须在有 Wi-Fi 的设备上跑通一次。
 */
@RunWith(AndroidJUnit4::class)
class MdnsResponderInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val statuses = CopyOnWriteArrayList<LocalNameStatus>()
    private val responder = MdnsResponder(context) { statuses += it }

    @After fun tearDown() = responder.stop()

    private fun wifiIp(): String? = NetworkInfo(context).currentWifiIpv4()

    private fun awaitStatus(timeoutMs: Long, predicate: (LocalNameStatus) -> Boolean): LocalNameStatus? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            statuses.lastOrNull()?.takeIf(predicate)?.let { return it }
            Thread.sleep(20)
        }
        return null
    }

    @Test fun restartRightAfterStopKeepsTheSameName() {
        val ip = wifiIp()
        assumeTrue("needs Wi-Fi", ip != null)
        repeat(5) { round ->
            statuses.clear()
            responder.start(ip!!, 537)
            val owned = awaitStatus(3000) { it is LocalNameStatus.Owned || it is LocalNameStatus.Renamed }
            assertEquals("round $round renamed itself: $statuses", LocalNameStatus.Owned(537), owned)
            val t0 = System.currentTimeMillis()
            responder.stop()
            assertTrue("stop took too long", System.currentTimeMillis() - t0 < 1500)
        }
    }

    @Test fun stoppingWhileProbingReturnsPromptly() {
        val ip = wifiIp()
        assumeTrue("needs Wi-Fi", ip != null)
        responder.start(ip!!, 538)
        Thread.sleep(100) // 仍在探测
        val t0 = System.currentTimeMillis()
        responder.stop()
        assertTrue(System.currentTimeMillis() - t0 < 1500)
        Thread.sleep(1000)
        assertEquals(LocalNameStatus.Disabled, statuses.last())
    }

    @Test fun anIpWithNoInterfaceIsUnavailableNotACrash() {
        responder.start("192.0.2.1", 539) // TEST-NET-1，本机不会有这个地址
        assertEquals(LocalNameStatus.Unavailable, awaitStatus(2000) { it == LocalNameStatus.Unavailable })
    }

    @Test fun updateIpWhileProbingProbesAgain() {
        val ip = wifiIp()
        assumeTrue("needs Wi-Fi", ip != null)
        responder.start(ip!!, 540)
        Thread.sleep(100)
        statuses.clear()
        responder.updateIp(ip)
        assertTrue("expected a fresh probe: $statuses", awaitStatus(500) { it is LocalNameStatus.Probing } != null)
    }

    /**
     * 生产代码里 stop() 的调用方（handleStop / onDestroy）都在主线程；主线程发 UDP 会抛
     * NetworkOnMainThreadException。goodbye 被吞掉时这条测试会等不到 TTL=0（2026-09-26 整分支审查）。
     */
    @Test fun stoppingFromTheMainThreadStillSendsGoodbye() {
        val ip = wifiIp()
        assumeTrue("needs Wi-Fi", ip != null)
        val addr = InetAddress.getByName(ip)
        val iface = NetworkInterface.getByInetAddress(addr)
        assumeTrue("needs the Wi-Fi interface", iface != null)
        val group = InetSocketAddress(InetAddress.getByName("224.0.0.251"), MdnsCodec.MDNS_PORT)
        MulticastSocket(null).use { listener ->
            listener.reuseAddress = true
            listener.bind(group)
            listener.joinGroup(group, iface)
            listener.soTimeout = 200
            responder.start(ip!!, 541)
            assertEquals(LocalNameStatus.Owned(541), awaitStatus(3000) { it is LocalNameStatus.Owned })
            InstrumentationRegistry.getInstrumentation().runOnMainSync { responder.stop() }
            val deadline = System.currentTimeMillis() + 2000
            var goodbye = false
            val buf = ByteArray(MdnsCodec.MAX_PACKET)
            while (!goodbye && System.currentTimeMillis() < deadline) {
                val pkt = DatagramPacket(buf, buf.size)
                try { listener.receive(pkt) } catch (_: SocketTimeoutException) { continue }
                goodbye = MdnsCodec.decode(pkt.data, pkt.length)?.answers
                    ?.any { it.name == "flikky541.local" && it.ttl == 0L } == true
            }
            assertTrue("no TTL=0 goodbye for flikky541.local after a main-thread stop", goodbye)
        }
    }

    /**
     * rebind 用调用方给的「设置里当前的编号」判断 Owned/Renamed，而不是启动时记下的那个。
     * 生产里这保证用户点过「改用 N」（设置 = 本次实际编号）之后，换网不会把 Renamed 提示再报回来。
     * 这里反过来验：设置变成别的号，rebind 后就该是 Renamed(设置, 实际)。
     */
    @Test fun rebindJudgesOwnershipAgainstTheCurrentSettingsNumber() {
        val ip = wifiIp()
        assumeTrue("needs Wi-Fi", ip != null)
        responder.start(ip!!, 542)
        assertEquals(LocalNameStatus.Owned(542), awaitStatus(3000) { it is LocalNameStatus.Owned })
        statuses.clear()
        responder.updateIp(ip, wanted = 999)
        assertEquals(
            LocalNameStatus.Renamed(999, 542),
            awaitStatus(1000) { it is LocalNameStatus.Owned || it is LocalNameStatus.Renamed },
        )
    }
}
