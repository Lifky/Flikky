package com.example.flikky.server

import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D79：状态修改请求（POST/PUT/PATCH/DELETE 与 WebSocket 握手）必须同源（CLAUDE.md 安全红线）。
 * 此前只靠 `SameSite=Strict` Cookie；这是不依赖浏览器 Cookie 实现的第二道。
 */
class SameOriginGuardTest {

    // ── 判定本身：纯函数，逐条钉死

    private fun allowed(
        method: String = "POST",
        ws: Boolean = false,
        origin: String? = null,
        referer: String? = null,
        host: String? = "10.0.2.16:8080",
    ) = isSameOriginRequest(method, ws, origin, referer, scheme = "http", host = host)

    @Test fun `a same-origin post passes`() {
        assertTrue(allowed(origin = "http://10.0.2.16:8080"))
        assertTrue("the local name is its own origin", allowed(origin = "http://flikky3.local:8080", host = "flikky3.local:8080"))
    }

    @Test fun `host names compare without case`() {
        assertTrue(allowed(origin = "http://Flikky3.LOCAL:8080", host = "flikky3.local:8080"))
    }

    @Test fun `a cross-origin post is refused`() {
        assertFalse(allowed(origin = "http://evil.example"))
        assertFalse("another port is another origin", allowed(origin = "http://10.0.2.16:9999"))
        assertFalse("another scheme is another origin", allowed(origin = "https://10.0.2.16:8080"))
    }

    @Test fun `an opaque null origin is refused`() {
        // 沙箱 iframe、no-referrer 表单都会发 `Origin: null`：攻击页可以主动做到。
        assertFalse(allowed(origin = "null"))
    }

    @Test fun `every state-changing method is guarded`() {
        for (m in listOf("POST", "PUT", "PATCH", "DELETE", "delete")) {
            assertFalse(m, allowed(method = m, origin = "http://evil.example"))
        }
    }

    @Test fun `reads are not guarded`() {
        assertTrue(allowed(method = "GET", origin = "http://evil.example"))
        assertTrue(allowed(method = "HEAD", origin = "http://evil.example"))
    }

    @Test fun `a websocket handshake is guarded even though it is a GET`() {
        assertFalse(allowed(method = "GET", ws = true, origin = "http://evil.example"))
        assertTrue(allowed(method = "GET", ws = true, origin = "http://10.0.2.16:8080"))
    }

    @Test fun `without an origin the referer decides`() {
        assertTrue(allowed(referer = "http://10.0.2.16:8080/app?x=1"))
        assertFalse(allowed(referer = "http://evil.example/page"))
        assertFalse("an unreadable referer counts as foreign", allowed(referer = "::not a url::"))
    }

    @Test fun `with neither header it is not a browser and passes`() {
        // CSRF 需要受害者的浏览器替攻击者发请求；受支持的浏览器对这些请求都会带 Origin。
        assertTrue(allowed())
    }

    @Test fun `a request without a host cannot prove it is same-origin`() {
        assertFalse(allowed(origin = "http://10.0.2.16:8080", host = null))
    }

    // ── 装进 Ktor 之后：拒绝时路由根本不执行

    // Ktor 测试引擎的请求不带 Host（实测为 null）；真实浏览器一定带，这里显式补上。
    private val host = "10.0.2.16:8080"

    private fun ApplicationTestBuilder.guardedApp(hits: MutableList<String>) = application {
        install(WebSockets)
        installSameOriginGuard()
        routing {
            post("/api/thing") { hits += "post"; call.respondText("ok") }
            delete("/api/thing") { hits += "delete"; call.respondText("ok") }
            webSocket("/ws") { hits += "ws" }
        }
    }

    @Test fun `a cross-origin post never reaches the route`() = testApplication {
        val hits = mutableListOf<String>()
        guardedApp(hits)
        val resp = client.post("/api/thing") {
            header(HttpHeaders.Host, host)
            header(HttpHeaders.Origin, "http://evil.example")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertTrue(resp.bodyAsText().contains("cross_origin"))
        val del = client.delete("/api/thing") {
            header(HttpHeaders.Host, host)
            header(HttpHeaders.Origin, "null")
        }
        assertEquals(HttpStatusCode.Forbidden, del.status)
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun `a same-origin post goes through`() = testApplication {
        val hits = mutableListOf<String>()
        guardedApp(hits)
        val resp = client.post("/api/thing") {
            header(HttpHeaders.Host, host)
            header(HttpHeaders.Origin, "http://$host")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(listOf("post"), hits)
    }

    @Test fun `a cross-origin websocket handshake is refused`() = testApplication {
        val hits = mutableListOf<String>()
        guardedApp(hits)
        val ws = createClient { install(ClientWebSockets) }
        val refused = runCatching {
            ws.webSocket("/ws", request = {
                header(HttpHeaders.Host, host)
                header(HttpHeaders.Origin, "http://evil.example")
            }) {}
        }
        assertTrue("the handshake must fail", refused.isFailure)

        ws.webSocket("/ws", request = {
            header(HttpHeaders.Host, host)
            header(HttpHeaders.Origin, "http://$host")
        }) {}
        assertEquals(listOf("ws"), hits)
    }

    // ── 真服务器确实装了它，且 Referrer-Policy 不会让同源请求的 Origin 变成 null

    private val server: String by lazy {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/com/example/flikky/server/KtorServer.kt") }
                .firstOrNull { it.isFile }?.let { return@lazy it.readText() }
            dir = dir.parentFile
        }
        error("missing KtorServer.kt")
    }

    @Test fun `the embedded server installs the guard`() {
        val module = server.substringAfter("embeddedServer(CIO").substringBefore("routing {")
        assertTrue(module.contains("installSameOriginGuard()"))
    }

    @Test fun `the referrer policy keeps same-origin origins readable`() {
        // no-referrer 下，非 cors 模式的同源 POST 按规范发 `Origin: null`，WebKit 对 fetch 也可能如此。
        // same-origin：同源照常带 Origin/Referer，跨源什么都不发 —— 对外链同样不泄露地址。
        assertTrue(server.contains("\"Referrer-Policy\", \"same-origin\""))
        assertFalse(server.contains("\"Referrer-Policy\", \"no-referrer\""))
    }
}
