package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.ServerRecallOutcome
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 多台浏览器同在一个会话时，撤回只认「自己设备发的」（v1.23 多设备）。
 * 原规则只比来源：所有浏览器的来源都是 BROWSER，于是电脑 B 能撤回电脑 A 的消息。
 * 别的电脑发的消息与手机发的一样算「对端消息」，要手机打开「允许撤回对端消息」。
 */
class MessageRoutesRecallDeviceTest {
    private fun status(target: Message, allowPeerRecall: Boolean): HttpStatusCode {
        var result: HttpStatusCode? = null
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing {
                    val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
                    val gate = AuthGate(required = true, pinAuth = pin, registry = DeviceRegistry(idSupplier = { "dev-me" }))
                    authRoutes(gate, readAsset = { byteArrayOf() })
                    messageRoutes(
                        session = SessionState(nowMs = { 0L }).apply { startNew(7L); addMessage(target) },
                        authGate = gate,
                        onPersist = { },
                        broadcastEvent = { _, _ -> },
                        nowMs = { 0L },
                        recallHandler = { id, _ -> ServerRecallOutcome.Success(id, 7L) },
                        recallEnabled = { true },
                        allowPeerRecall = { allowPeerRecall },
                    )
                }
            }
            val http = createClient { install(HttpCookies) }
            http.post("/api/auth") { contentType(ContentType.Application.Json); setBody("""{"pin":"000000"}""") }
            result = http.delete("/api/messages/${target.id}") { header("X-Client-Id", "tab") }.status
        }
        return result!!
    }

    private fun text(senderDeviceId: String?) = Message.Text(5L, Origin.BROWSER, 0L, "x", senderDeviceId = senderDeviceId)

    @Test fun `a browser can recall what it sent itself`() {
        assertEquals(HttpStatusCode.OK, status(text("dev-me"), allowPeerRecall = false))
    }

    @Test fun `a browser cannot recall another computer's message by default`() {
        assertEquals(HttpStatusCode.Forbidden, status(text("dev-other"), allowPeerRecall = false))
    }

    @Test fun `another computer's message follows the peer recall switch`() {
        assertEquals(HttpStatusCode.OK, status(text("dev-other"), allowPeerRecall = true))
    }

    @Test fun `messages from before devices existed keep the old rule`() {
        assertEquals(HttpStatusCode.OK, status(text(null), allowPeerRecall = false))
    }
}
