package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.session.SessionState
import com.example.flikky.util.PeerAvatarPolicy
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/** client_hello 是正规 JSON；每台设备只采纳第一次上报，之后以服务端为准（spec §5.2–§5.4）。 */
class WsRoutesClientHelloTest {
    private val registry = DeviceRegistry(idSupplier = { "dev-1" })
    private val decisions = mutableListOf<Pair<String?, Boolean>>()

    private fun ApplicationTestBuilder.setUp(
        decide: suspend (String?, Boolean) -> PeerAvatarPolicy.Decision = { key, explicit ->
            decisions += key to explicit
            PeerAvatarPolicy.onHello(key, explicit, storedDefault = "icon:face")
        },
    ) {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                val gate = AuthGate(required = true, pinAuth = pin, registry = registry)
                authRoutes(gate, readAsset = { byteArrayOf() })
                wsRoutes(gate, SessionState(nowMs = { 0L }), WsHub(), registry, decide)
            }
        }
    }

    private suspend fun ApplicationTestBuilder.loggedIn(): HttpClient {
        val http = createClient { install(HttpCookies); install(WebSockets) }
        http.post("/api/auth") { contentType(ContentType.Application.Json); setBody("""{"pin":"000000"}""") }
        return http
    }

    private suspend fun DefaultClientWebSocketSession.nextPayload(type: String): JsonObject =
        withTimeout(2_000) {
            var found: JsonObject? = null
            while (found == null) {
                val frame = incoming.receive()
                if (frame !is Frame.Text) continue
                val obj = Json.parseToJsonElement(frame.readText()).jsonObject
                if (obj["type"]?.jsonPrimitive?.content == type) found = obj["payload"]!!.jsonObject
            }
            found
        }

    @Test fun `the first hello names the device and the phone gets the decided avatar`() = testApplication {
        setUp()
        loggedIn().webSocket("/ws") {
            send(Frame.Text("""{"type":"client_hello","avatarKey":"icon:star","avatarExplicit":true,"deviceName":"  我的 \"笔记本\" 😀 "}"""))
            val payload = nextPayload("device_updated")
            assertEquals("我的 \"笔记本\" 😀", payload["name"]!!.jsonPrimitive.content)
            assertEquals("icon:star", payload["avatarKey"]!!.jsonPrimitive.content)
        }
        assertEquals("我的 \"笔记本\" 😀", registry.device("dev-1")!!.name)
        assertEquals(listOf<Pair<String?, Boolean>>("icon:star" to true), decisions)
    }

    @Test fun `a later hello cannot overwrite a name the phone set meanwhile`() = testApplication {
        setUp()
        val http = loggedIn()
        http.webSocket("/ws") {
            send(Frame.Text("""{"type":"client_hello","avatarKey":"icon:star","avatarExplicit":false,"deviceName":"旧名字"}"""))
            nextPayload("device_updated")
        }
        registry.update("dev-1", name = "手机改的名字")
        http.webSocket("/ws") {
            send(Frame.Text("""{"type":"client_hello","avatarKey":"icon:star","avatarExplicit":false,"deviceName":"旧名字"}"""))
            assertEquals("手机改的名字", nextPayload("device_updated")["name"]!!.jsonPrimitive.content)
        }
        assertEquals(1, decisions.size)
    }

    @Test fun `frames that are not a hello are ignored`() {
        assertNull(parseClientHello("""{"type":"foo"}"""))
        assertNull(parseClientHello("""{"type":"client_hello""""))
        assertEquals("x", parseClientHello("""{"type":"client_hello","deviceName":"x","extra":1}""")?.deviceName)
    }
}
