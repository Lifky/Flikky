package com.example.flikky.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.flikky.session.LocalNameStatus
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
}
