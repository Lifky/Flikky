package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.ServerFavoriteOutcome
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D78：POST /api/messages/{id}/favorite —— 浏览器把当前会话里的一条消息收藏到手机。
 * 只能加不能删；门控是「收藏功能 + 允许对端收藏消息」两轴都开（由调用方算好传入）。
 */
class MessageRoutesFavoriteTest {

    private suspend fun authenticate(http: io.ktor.client.HttpClient) {
        val resp: HttpResponse = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    private fun setupApp(
        favoriteHandler: suspend (Message) -> ServerFavoriteOutcome,
        peerFavoriteEnabled: () -> Boolean = { true },
    ): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        routing {
            val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
            val session = SessionState(nowMs = { 0L }).apply {
                startNew(sessionId = 7L)
                addMessage(Message.Text(123L, Origin.PHONE, 0L, "from the phone"))
                addMessage(
                    Message.File(
                        id = 124L, origin = Origin.BROWSER, timestamp = 0L, fileId = "f", name = "a.bin",
                        sizeBytes = 1L, mime = "application/octet-stream", status = Message.File.Status.IN_PROGRESS,
                    ),
                )
            }
            val authGate = AuthGate(required = true, pinAuth = pin)
            authRoutes(authGate, readAsset = { byteArrayOf() })
            messageRoutes(
                session = session,
                authGate = authGate,
                onPersist = { _ -> },
                broadcastEvent = { _, _ -> },
                nowMs = { 0L },
                recallHandler = { _, _ -> error("recall is not under test") },
                favoriteHandler = favoriteHandler,
                peerFavoriteEnabled = peerFavoriteEnabled,
            )
        }
    }

    private fun errorOf(body: String) = Json.parseToJsonElement(body).jsonObject["error"]!!.jsonPrimitive.content
    private fun resultOf(body: String) = Json.parseToJsonElement(body).jsonObject["result"]!!.jsonPrimitive.content

    @Test
    fun `without a cookie it is 401 and nothing is favorited`() = testApplication {
        application(setupApp(favoriteHandler = { error("must not run unauthenticated") }))
        val resp = client.post("/api/messages/123/favorite") { header("X-Client-Id", "c") }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `with peer favoriting off it is 403 and nothing is favorited`() = testApplication {
        application(
            setupApp(favoriteHandler = { error("must not run while disabled") }, peerFavoriteEnabled = { false }),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val resp = http.post("/api/messages/123/favorite") { header("X-Client-Id", "c") }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertEquals("favorite_disabled", errorOf(resp.bodyAsText()))
    }

    @Test
    fun `without X-Client-Id it is 400`() = testApplication {
        application(setupApp(favoriteHandler = { error("must not run without client id") }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val resp = http.post("/api/messages/123/favorite")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertEquals("missing_client_id", errorOf(resp.bodyAsText()))
    }

    @Test
    fun `a message outside the current session is 404`() = testApplication {
        application(setupApp(favoriteHandler = { error("must not run for unknown ids") }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val resp = http.post("/api/messages/999/favorite") { header("X-Client-Id", "c") }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `a file that is still transferring is 409`() = testApplication {
        application(setupApp(favoriteHandler = { error("must not run for an incomplete file") }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val resp = http.post("/api/messages/124/favorite") { header("X-Client-Id", "c") }
        assertEquals(HttpStatusCode.Conflict, resp.status)
        assertEquals("not_ready", errorOf(resp.bodyAsText()))
    }

    @Test
    fun `a new favorite reports added and hands the session message to the handler`() = testApplication {
        var seen: Message? = null
        application(setupApp(favoriteHandler = { seen = it; ServerFavoriteOutcome.Added }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val resp = http.post("/api/messages/123/favorite") { header("X-Client-Id", "c") }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("added", resultOf(resp.bodyAsText()))
        assertTrue("the handler gets the phone's own message too", (seen as Message.Text).content == "from the phone")
    }

    @Test
    fun `a repeat reports exists`() = testApplication {
        application(setupApp(favoriteHandler = { ServerFavoriteOutcome.AlreadyFavorited }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val resp = http.post("/api/messages/123/favorite") { header("X-Client-Id", "c") }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("exists", resultOf(resp.bodyAsText()))
    }

    @Test
    fun `a storage failure is 500`() = testApplication {
        application(setupApp(favoriteHandler = { ServerFavoriteOutcome.Failed }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val resp = http.post("/api/messages/123/favorite") { header("X-Client-Id", "c") }
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
        assertEquals("favorite_failed", errorOf(resp.bodyAsText()))
    }
}
