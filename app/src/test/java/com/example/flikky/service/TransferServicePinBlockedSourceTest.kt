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

    @Test fun `stopping clears the blocked state`() {
        assertTrue(service.contains("pinBlockedJob?.cancel(); pinBlockedJob = null"))
        assertTrue(service.contains("ServiceLocator.session.setPinBlocked(false)"))
    }
}
