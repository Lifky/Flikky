package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.session.PeerDevice
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.ktor.server.websocket.WebSockets as ServerWebSockets

class WsRoutesDevicesTest {
    /** 关 PIN 签发的设备要等第一条 WebSocket 才可见；轮询到可见台数达到 [count]。 */
    private suspend fun awaitVisible(registry: DeviceRegistry, count: Int): List<PeerDevice> =
        withTimeout(5_000) {
            while (registry.devices.value.size < count) delay(10)
            registry.devices.value
        }

    /** 下一条不是「本场名册」的文本帧（名册连上即推，也会随设备变化重推）。 */
    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.nextNonRoster(): String {
        while (true) {
            val frame = incoming.receive()
            if (frame !is Frame.Text) continue
            val text = frame.readText()
            if (!text.startsWith("""{"type":"peers"""")) return text
        }
    }

    @Test fun `a socket without a credential is closed as unauthorized`() = testApplication {
        val registry = DeviceRegistry()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                val gate = AuthGate(required = true, pinAuth = pin, registry = registry)
                wsRoutes(gate, SessionState(nowMs = { 0L }), WsHub(), registry)
            }
        }
        createClient { install(WebSockets) }.webSocket("/ws") {
            assertEquals(WS_CLOSE_UNAUTHORIZED, withTimeout(2_000) { closeReason.await() }!!.code)
        }
    }

    @Test fun `disconnecting one device tells it why and leaves the other connected`() = testApplication {
        val registry = DeviceRegistry()
        val hub = WsHub()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                val gate = AuthGate(required = false, pinAuth = pin, registry = registry)
                authRoutes(gate, readAsset = { byteArrayOf() })
                wsRoutes(gate, SessionState(nowMs = { 0L }), hub, registry)
            }
        }
        val a = createClient { install(HttpCookies); install(WebSockets) }.also { it.get("/") }
        val b = createClient { install(HttpCookies); install(WebSockets) }.also { it.get("/") }
        coroutineScope {
            val bId = CompletableDeferred<String>()
            val aRevoked = CompletableDeferred<Unit>()
            val bReply = CompletableDeferred<String>()
            launch {
                b.webSocket("/ws") {
                    bId.complete(awaitVisible(registry, count = 1).single().deviceId)
                    // 等 A 被断开之后再 ping：证明断开一台不会波及另一台。
                    aRevoked.await()
                    send(Frame.Text("""{"type":"ping","id":9}"""))
                    bReply.complete(nextNonRoster())
                }
            }
            val knownB = withTimeout(5_000) { bId.await() }
            a.webSocket("/ws") {
                val aId = awaitVisible(registry, count = 2).single { it.deviceId != knownB }.deviceId
                registry.revoke(aId)
                hub.closeDevice(aId)
                aRevoked.complete(Unit)
                val first = withTimeout(2_000) { nextNonRoster() }
                assertTrue(first.contains("device_revoked"))
                assertEquals(WS_CLOSE_REVOKED, withTimeout(2_000) { closeReason.await() }!!.code)
            }
            assertEquals("""{"type":"pong","id":9}""", withTimeout(5_000) { bReply.await() })
        }
    }

    @Test fun `a new connection is told who else is in the session`() = testApplication {
        val registry = DeviceRegistry()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            install(ServerWebSockets) {}
            routing {
                val gate = AuthGate(required = false, pinAuth = pin, registry = registry)
                authRoutes(gate, readAsset = { byteArrayOf() })
                wsRoutes(gate, SessionState(nowMs = { 0L }), WsHub(), registry)
            }
        }
        val client = createClient { install(HttpCookies); install(WebSockets) }.also { it.get("/") }
        client.webSocket("/ws") {
            val first = withTimeout(2_000) { (incoming.receive() as Frame.Text).readText() }
            assertTrue(first, first.startsWith("""{"type":"peers","payload":{"devices":["""))
            val me = registry.devices.value.single()
            assertTrue(first, first.contains(""""deviceId":"${me.deviceId}""""))
            assertTrue(first, first.contains(""""selfDeviceId":"${me.deviceId}""""))
        }
    }
}
