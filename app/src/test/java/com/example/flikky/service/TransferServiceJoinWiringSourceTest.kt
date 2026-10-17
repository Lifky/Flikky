package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「请求加入」的生命周期接线（v1.23 多设备）。读源码断言形状，同 [TransferServiceRebindSourceTest] 的思路：
 * 这些是「漏一行、行为测试照样全绿」的地方。
 */
class TransferServiceJoinWiringSourceTest {
    private val service = File("src/main/java/com/example/flikky/service/TransferService.kt").readText()
    private val ktor = File("src/main/java/com/example/flikky/server/KtorServer.kt").readText()

    private fun block(src: String, start: String, end: String): String {
        val i = src.indexOf(start)
        assertTrue("missing: $start", i >= 0)
        val j = src.indexOf(end, i + start.length)
        assertTrue("missing end after: $start", j > i)
        return src.substring(i, j)
    }

    @Test fun `a transfer session owns one join queue, handed to every server and the controller`() {
        assertTrue(service.contains("private var joinRequests: JoinRequests? = null"))
        val start = block(service, "private fun startTransfer(", "currentMode = ServiceMode.Transfer")
        assertTrue(start.contains("joinRequests = joins"))
        val build = block(service, "private fun buildTransferKtor(", "\n    )\n")
        assertTrue("every rebuilt server must see the same queue", build.contains("joinRequests = joinRequests"))
        assertTrue(service.contains("joins = joins"))
    }

    @Test fun `a new address or a stop drops every pending request`() {
        // 请求凭证的 Cookie 绑在旧 host 上；换了 IP 那些页面再也领不到，留着只会让手机弹一个没人等的框。
        val newHost = block(service, "if (currentHostIp != newIp) {", "}")
        assertTrue(newHost.contains("joinRequests?.clear()"))
        val stop = block(service, "deviceRegistry?.revokeAll()\n        deviceRegistry = null", "_running.value = null")
        assertTrue(stop.contains("joinRequests?.clear()"))
        assertTrue(stop.contains("joinRequests = null"))
    }

    @Test fun `only transfer mode accepts join requests`() {
        assertTrue(ktor.contains("joinRequests = joinRequests.takeIf { mode == ServiceMode.Transfer }"))
        assertTrue(ktor.contains("joinRoutes("))
    }
}
