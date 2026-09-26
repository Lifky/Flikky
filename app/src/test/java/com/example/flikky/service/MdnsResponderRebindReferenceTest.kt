package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MdnsResponder 跨 rebind 存活（TransferService field），所以它**不得**持有任何
 * KtorServer 成员（CLAUDE.md「跨 Wi-Fi rebind 的引用规范」）。IP 只能经 start/updateIp 传入。
 */
class MdnsResponderRebindReferenceTest {

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java").map { File(dir, "$it/$relative") }
                .firstOrNull { it.isFile }?.let { return it.readText() }
            dir = dir.parentFile
        }
        error("missing $relative")
    }

    private val service get() = source("com/example/flikky/service/TransferService.kt")
    private val responder get() = source("com/example/flikky/network/MdnsResponder.kt")

    @Test fun `the responder is built without any server reference`() {
        val at = service.indexOf("MdnsResponder(")
        assertTrue("TransferService must construct MdnsResponder", at >= 0)
        val call = service.substring(at, service.indexOf('}', at) + 1)
        assertFalse("must not capture ktor: $call", call.contains("ktor"))
        assertFalse("must not capture a server local: $call", Regex("\\bserver\\b").containsMatchIn(call))
    }

    @Test fun `the responder does not know the server package`() {
        assertFalse(responder.contains("com.example.flikky.server"))
    }

    @Test fun `rebind re-announces on the new ip`() {
        val at = service.indexOf("private fun rebindTo(")
        assertTrue(at >= 0)
        assertTrue(service.substring(at).take(3000).contains("updateIp(newIp)"))
    }

    @Test fun `stopping the server stops the responder first`() {
        val at = service.indexOf("private fun stopActiveServer(")
        val body = service.substring(at).take(1500)
        val mdnsAt = body.indexOf("mdns.stop()")
        val ktorAt = body.indexOf("ktor?.stop()")
        assertTrue("stopActiveServer must stop the responder", mdnsAt >= 0)
        assertTrue("the responder must stop before Ktor", mdnsAt < ktorAt)
    }
}
