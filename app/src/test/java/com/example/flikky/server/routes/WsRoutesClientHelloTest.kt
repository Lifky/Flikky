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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * client_hello adoption is delegated through onClientHello so server/ stays independent from
 * Android DataStore. A non-null callback result is broadcast back as the authoritative avatar.
 */
class WsRoutesClientHelloTest {

    private suspend fun io.ktor.client.HttpClient.authOk() {
        val response: HttpResponse = post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `explicit hello reaches callback and does not push back`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val hub = WsHub()
        var seenKey: String? = null
        var seenExplicit: Boolean? = null

        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                val authGate = AuthGate(required = true, pinAuth = pin)
                authRoutes(authGate, readAsset = { byteArrayOf() })
                wsRoutes(authGate, session, hub, onClientHello = { key, explicit ->
                    seenKey = key
                    seenExplicit = explicit
                    null
                })
            }
        }

        val http = createClient {
            install(HttpCookies)
            install(WebSockets)
        }
        http.authOk()

        http.webSocket("/ws") {
            send(Frame.Text("""{"type":"client_hello","avatarKey":"icon:star","explicit":true}"""))
            send(Frame.Text("""{"type":"ping","id":1}"""))
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
            assertEquals("""{"type":"pong","id":1}""", reply)
        }
        assertEquals("icon:star", seenKey)
        assertEquals(true, seenExplicit)
    }

    @Test
    fun `announce hello with pushback broadcasts the authoritative avatar`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val hub = WsHub()

        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                val authGate = AuthGate(required = true, pinAuth = pin)
                authRoutes(authGate, readAsset = { byteArrayOf() })
                wsRoutes(authGate, session, hub, onClientHello = { _, _ -> "icon:palette" })
            }
        }

        val http = createClient {
            install(HttpCookies)
            install(WebSockets)
        }
        http.authOk()

        http.webSocket("/ws") {
            send(Frame.Text("""{"type":"client_hello","avatarKey":"icon:desktop_windows","explicit":false}"""))
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
            assertEquals(
                """{"type":"peer_avatar_changed","payload":{"avatarKey":"icon:palette"}}""",
                reply,
            )
        }
    }

    @Test
    fun `hello without explicit field defaults to announce`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val hub = WsHub()
        var seenExplicit: Boolean? = null

        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                val authGate = AuthGate(required = true, pinAuth = pin)
                authRoutes(authGate, readAsset = { byteArrayOf() })
                wsRoutes(authGate, session, hub, onClientHello = { _, explicit ->
                    seenExplicit = explicit
                    null
                })
            }
        }

        val http = createClient {
            install(HttpCookies)
            install(WebSockets)
        }
        http.authOk()

        http.webSocket("/ws") {
            send(Frame.Text("""{"type":"client_hello","avatarKey":"icon:star"}"""))
            send(Frame.Text("""{"type":"ping","id":2}"""))
            withTimeout(2_000) {
                for (frame in incoming) {
                    if (frame is Frame.Text) break
                }
            }
        }
        assertEquals(false, seenExplicit)
        assertNull(null)
    }
}
