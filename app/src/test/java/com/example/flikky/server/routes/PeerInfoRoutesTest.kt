package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.PeerInfoDto
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
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
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M9 Task 9.2 — GET /api/peer-info
 *
 * Cases:
 *  (a) No auth cookie → 401
 *  (b) Valid cookie → 200 + JSON with deviceName/phoneAvatarId/backgroundMode
 */
class PeerInfoRoutesTest {

    private val testPeerInfo = PeerInfoDto(
        deviceName = "测试手机",
        phoneAvatarId = 3,
        backgroundMode = "GRADIENT",
        backgroundValue = "sunset",
    )

    private fun setupApp(provider: () -> PeerInfoDto): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        routing {
            val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
            authRoutes(pin, readAsset = { byteArrayOf() })
            peerInfoRoutes(pinAuth = pin, provider = provider)
        }
    }

    private suspend fun authenticate(http: io.ktor.client.HttpClient) {
        val resp: HttpResponse = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET peer-info without cookie returns 401`() = testApplication {
        application(setupApp { testPeerInfo })
        val resp: HttpResponse = client.get("/api/peer-info")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET peer-info with valid cookie returns 200 and JSON fields`() = testApplication {
        application(setupApp { testPeerInfo })
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/peer-info")
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals("测试手机", body["deviceName"]!!.jsonPrimitive.content)
        assertEquals(3, body["phoneAvatarId"]!!.jsonPrimitive.int)
        assertEquals("GRADIENT", body["backgroundMode"]!!.jsonPrimitive.content)
        assertEquals("sunset", body["backgroundValue"]!!.jsonPrimitive.content)
        // bubbleCornerRadius defaults to 18 and is serialized so the browser can mirror it.
        assertEquals(18, body["bubbleCornerRadius"]!!.jsonPrimitive.int)
        assertEquals("FIRST", body["avatarGrouping"]!!.jsonPrimitive.content)
    }

    @Test
    fun `peer-info carries an explicit bubbleCornerRadius`() = testApplication {
        application(setupApp { testPeerInfo.copy(bubbleCornerRadius = 24) })
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/peer-info")
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(24, body["bubbleCornerRadius"]!!.jsonPrimitive.int)
    }
}
