package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3 T8：浏览器主动发 {"type":"ping","id":N} 时，服务端必须立刻回
 * {"type":"pong","id":N}，让浏览器据此判断 WS 是否真的活着（解半开 TCP）。
 * 不依赖 Ktor 自带的 protocol-level ping/pong（那个被动靠 timeoutMillis 撕，
 * 旧版被实测在 Wi-Fi 切换场景下要 30s 才生效）。
 */
class WsRoutesPingPongTest {

    @Test
    fun `server echoes pong with same id when client sends ping`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val hub = WsHub()

        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                wsRoutes(pin, session, hub)
            }
        }

        val http = createClient {
            install(HttpCookies)
            install(WebSockets)
        }

        // Authenticate first so the WS handshake sees a valid cookie.
        val authResp: HttpResponse = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, authResp.status)

        http.webSocket("/ws") {
            send(Frame.Text("""{"type":"ping","id":42}"""))
            val reply = withTimeout(2_000) {
                var found: String? = null
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        found = frame.readText()
                        break
                    }
                }
                found
            }
            assertEquals("""{"type":"pong","id":42}""", reply)
        }
    }

    @Test
    fun `server ignores non-ping text frames silently`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val hub = WsHub()

        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                wsRoutes(pin, session, hub)
            }
        }

        val http = createClient {
            install(HttpCookies)
            install(WebSockets)
        }
        val authResp: HttpResponse = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, authResp.status)

        http.webSocket("/ws") {
            send(Frame.Text("""{"type":"hello","id":1}"""))
            // Now send a real ping; the pong proves the server is still
            // processing frames (i.e. did not crash on the unknown frame).
            send(Frame.Text("""{"type":"ping","id":7}"""))
            val reply = withTimeout(2_000) {
                var found: String? = null
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        found = frame.readText()
                        break
                    }
                }
                found
            }
            // The only frame the server should ever emit in response is the
            // pong matching our ping; the prior "hello" must have been dropped
            // silently with no reply frame.
            assertTrue(
                "expected pong with id=7 but got $reply",
                reply == """{"type":"pong","id":7}""",
            )
        }
    }
}
