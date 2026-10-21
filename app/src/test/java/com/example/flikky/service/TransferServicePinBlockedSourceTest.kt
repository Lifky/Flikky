package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.24（B59#5）：PIN 停用状态从 PinAuth 同步到手机界面（传输与导出两条启动路径都要），随服务停止复位。
 * 源码断言：这些是「漏一行、行为测试照样全绿」的地方。
 */
class TransferServicePinBlockedSourceTest {
    private val service: String by lazy {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/com/example/flikky/service/TransferService.kt") }
                .firstOrNull { it.isFile }?.let { return@lazy it.readText().replace("\r\n", "\n") }
            dir = dir.parentFile
        }
        error("missing TransferService.kt")
    }

    @Test fun `both start paths mirror the blocked pin to the phone`() {
        assertTrue(service.contains("auth.blocked.collect { ServiceLocator.session.setPinBlocked(it) }"))
        assertEquals("transfer and export both watch their PinAuth", 2, Regex("""watchPinBlocked\(auth\)""").findAll(service).count())
    }

    @Test fun `stopping clears the blocked state only after the card is gone`() {
        assertTrue(service.contains("pinBlockedJob?.cancel(); pinBlockedJob = null"))
        // 装机：先复位、后清 _running，中间那一刻卡片按「未停用」画出旧 PIN（闪一下）。
        val stop = service.substringAfter("pinBlockedJob?.cancel(); pinBlockedJob = null")
        val cleared = stop.indexOf("_running.value = null")
        val reset = stop.indexOf("ServiceLocator.session.setPinBlocked(false)")
        assertTrue("reset must exist", reset >= 0)
        assertTrue("reset must come after the card's inputs are cleared", cleared in 0 until reset)
    }

    @Test fun `a new service starts unblocked before its card appears`() {
        // 收集在 IO 协程里异步发生；卡片可能先于它出现。启动时同步写一次当前值。
        assertTrue(service.contains("ServiceLocator.session.setPinBlocked(auth.blocked.value)"))
    }
}
