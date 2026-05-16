package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.ServerRecallOutcome
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3 D26：DELETE /api/messages/{id}。覆盖鉴权 / 头部校验 / 各分支语义 /
 * 成功时广播。recallHandler 直接 stub 各种 ServerRecallOutcome 子类型，
 * 无需触发真实 SessionRepository。
 */
class MessageRoutesRecallTest {

    private suspend fun authenticate(http: io.ktor.client.HttpClient) {
        val resp: HttpResponse = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    private fun setupApp(
        recallHandler: suspend (Long, String) -> ServerRecallOutcome,
        captureBroadcast: ((String, String) -> Unit)? = null,
    ): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        routing {
            val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
            val session = SessionState(nowMs = { 0L })
            authRoutes(pin, readAsset = { byteArrayOf() })
            messageRoutes(
                session = session,
                pinAuth = pin,
                onPersist = { _ -> },
                broadcastEvent = { type, payload -> captureBroadcast?.invoke(type, payload) },
                nowMs = { 0L },
                recallHandler = recallHandler,
            )
        }
    }

    @Test
    fun `DELETE without cookie returns 401`() = testApplication {
        application(setupApp(recallHandler = { _, _ -> error("recall must not run when unauthenticated") }))
        val resp: HttpResponse = client.delete("/api/messages/123") {
            header("X-Client-Id", "phone-abc")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `DELETE without X-Client-Id returns 400`() = testApplication {
        application(setupApp(recallHandler = { _, _ -> error("recall must not run without client id") }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.delete("/api/messages/123")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val err = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("missing_client_id", err["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE with non-numeric id returns 400`() = testApplication {
        application(setupApp(recallHandler = { _, _ -> error("recall must not run with bad id") }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.delete("/api/messages/abc") {
            header("X-Client-Id", "phone-abc")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val err = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("invalid_id", err["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE when handler returns NotFound responds 404`() = testApplication {
        application(setupApp(recallHandler = { _, _ -> ServerRecallOutcome.NotFound }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.delete("/api/messages/999") {
            header("X-Client-Id", "phone-abc")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
        val err = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("not_found", err["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE when handler returns Denied responds 403`() = testApplication {
        application(setupApp(recallHandler = { _, _ -> ServerRecallOutcome.Denied }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.delete("/api/messages/123") {
            header("X-Client-Id", "phone-attacker")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        val err = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("denied", err["error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `DELETE Success returns 200 with RecallResponse and broadcasts message_recalled`() = testApplication {
        var broadcastType: String? = null
        var broadcastPayload: String? = null
        application(
            setupApp(
                recallHandler = { messageId, _ ->
                    ServerRecallOutcome.Success(messageId = messageId, sessionId = 7L)
                },
                captureBroadcast = { type, payload ->
                    broadcastType = type
                    broadcastPayload = payload
                },
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.delete("/api/messages/123") {
            header("X-Client-Id", "phone-abc")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val obj = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(123L, obj["messageId"]!!.jsonPrimitive.long)
        assertEquals(7L, obj["sessionId"]!!.jsonPrimitive.long)

        assertEquals("message_recalled", broadcastType)
        assertNotNull(broadcastPayload)
        val payload = Json.parseToJsonElement(broadcastPayload!!).jsonObject
        assertEquals(123L, payload["messageId"]!!.jsonPrimitive.long)
        assertEquals(7L, payload["sessionId"]!!.jsonPrimitive.long)
    }
}
