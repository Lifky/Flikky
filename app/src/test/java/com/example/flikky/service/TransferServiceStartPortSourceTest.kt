package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 端口起点的**源码**守卫（spec §4.6、D75）：新会话（传输、导出）从设置里的端口起扫，
 * 只有 rebind 沿用本会话已绑的端口。
 *
 * 导出启动不走 `session.startNew()`，snapshot 里可能还留着上一次会话的 `boundPort`。
 * 拿它起扫就会绕过新设的端口，还会给出「9000 被占用，本次使用 8080」这种假提示 ——
 * 正是 §4.6 要杜绝的误报（2026-09-26 整分支审查）。
 */
class TransferServiceStartPortSourceTest {

    private val service: String by lazy {
        val file = File("src/main/java/com/example/flikky/service/TransferService.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/example/flikky/service/TransferService.kt")
        assertTrue("source file not found", file.isFile)
        file.readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\r\n]*"""), "")
    }

    @Test fun `fresh transfer and export starts scan from the custom port`() {
        assertTrue(service.contains("buildTransferKtor(ip, auth, startPort = latestSettings.customPort)"))
        assertTrue(service.contains("buildExportKtor(ip, auth, startPort = latestSettings.customPort)"))
    }

    @Test fun `only the rebind path reuses the bound port`() {
        val uses = Regex("""snapshot\.value\.boundPort""").findAll(service).map { it.range.first }.toList()
        assertEquals("expected exactly one read of the bound port", 1, uses.size)
        val rebindStart = service.indexOf("private fun rebindTo(")
        val rebindEnd = service.indexOf("\n    private fun ", rebindStart + 1).takeIf { it > 0 } ?: service.length
        assertTrue("the bound port may only be read inside rebindTo", uses.single() in rebindStart until rebindEnd)
    }
}
