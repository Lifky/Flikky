package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「请求加入」的生命周期接线（v1.23 多设备）。读源码断言形状，同 [TransferServiceRebindSourceTest] 的思路：
 * 这些是「漏一行、行为测试照样全绿」的地方。
 */
class TransferServiceJoinWiringSourceTest {
    // 本机 core.autocrlf=true：检出后的工作区文件是 CRLF。下面按 "\n" 切块，先统一换行再比。
    private val service = File("src/main/java/com/example/flikky/service/TransferService.kt").readText().replace("\r\n", "\n")
    private val ktor = File("src/main/java/com/example/flikky/server/KtorServer.kt").readText().replace("\r\n", "\n")

    private fun block(src: String, start: String, end: String): String {
        val i = src.indexOf(start)
        assertTrue("missing: $start", i >= 0)
        val j = src.indexOf(end, i + start.length)
        assertTrue("missing end after: $start", j > i)
        return src.substring(i, j)
    }

    // 一个服务一个队列：每次重建的 KtorServer 都拿到同一个；手机界面与通知按钮经 ServiceLocator 拿到同一个
    // （导出模式没有 TransferController，所以不走 controller）。
    @Test fun `a service owns one join queue, handed to every server and to the phone`() {
        assertTrue(service.contains("private var joinRequests: JoinRequests? = null"))
        val helper = block(service, "private fun startJoinRequests()", "\n    }\n")
        assertTrue(helper.contains("joinRequests = joins"))
        assertTrue(helper.contains("ServiceLocator.currentJoinRequests = joins"))
        for (builder in listOf("private fun buildTransferKtor(", "private fun buildExportKtor(")) {
            val build = block(service, builder, "\n    )\n")
            assertTrue("every rebuilt server must see the same queue: $builder", build.contains("joinRequests = joinRequests"))
        }
        val stop = block(service, "deviceRegistry?.revokeAll()\n        deviceRegistry = null", "_running.value = null")
        assertTrue(stop.contains("ServiceLocator.currentJoinRequests = null"))
    }

    @Test fun `a new address or a stop drops every pending request`() {
        // 请求凭证的 Cookie 绑在旧 host 上；换了 IP 那些页面再也领不到，留着只会让手机弹一个没人等的框。
        val newHost = block(service, "if (currentHostIp != newIp) {", "}")
        assertTrue(newHost.contains("joinRequests?.clear()"))
        val stop = block(service, "deviceRegistry?.revokeAll()\n        deviceRegistry = null", "_running.value = null")
        assertTrue(stop.contains("joinRequests?.clear()"))
        assertTrue(stop.contains("joinRequests = null"))
    }

    // 导出模式也要（装机反馈：开 PIN 时「请求加入」打到不存在的路由 → 网络错误；关 PIN 时直接放人）。
    @Test fun `both transfer and export accept join requests`() {
        assertTrue(ktor.contains("joinRequests = joinRequests,"))
        assertTrue(ktor.contains("joinRoutes(authGate, it, redirectAfterLogin = ::landingPath)"))
        val export = block(service, "private fun handleExport()", "currentMode = ServiceMode.Export")
        assertTrue("export must own a join queue too", export.contains("startJoinRequests()"))
        val start = block(service, "private fun startTransfer(", "currentMode = ServiceMode.Transfer")
        assertTrue(start.contains("startJoinRequests()"))
    }
}
