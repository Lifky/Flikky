package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.JoinRequests
import com.example.flikky.server.PinAuth
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「请求加入」（v1.23 多设备，用户裁决）：会话里已有设备时，新设备要手机允许才能进。
 * 第一台照旧：开 PIN 输 PIN，关 PIN 直接进。
 */
class JoinRoutesTest {
    private class Rig(val registry: DeviceRegistry, val joins: JoinRequests)

    private fun ApplicationTestBuilder.rig(requirePin: Boolean): Rig {
        val registry = DeviceRegistry()
        val joins = JoinRequests()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK-PIN" })
        application {
            install(ContentNegotiation) { json() }
            routing {
                val gate = AuthGate(required = requirePin, pinAuth = pin, registry = registry, joinRequests = joins)
                authRoutes(gate, readAsset = { path -> path.toByteArray() })
                joinRoutes(gate, joins, redirectAfterLogin = { "/app" })
            }
        }
        return Rig(registry, joins)
    }

    private fun ApplicationTestBuilder.browser(): HttpClient =
        createClient { install(HttpCookies); followRedirects = false }

    private suspend fun HttpClient.joinState(): String = get("/api/join/state").bodyAsText()

    @Test fun `the first browser still walks straight in with pin off`() = testApplication {
        val r = rig(requirePin = false)
        val first = browser()
        assertEquals(HttpStatusCode.Found, first.get("/").status)
        assertEquals("/app", first.get("/").headers[HttpHeaders.Location])
        assertTrue(first.joinState().contains("\"joinRequired\":false"))
        assertTrue(r.joins.pending.value.isEmpty())
    }

    @Test fun `once someone is in, a new browser lands on the join page instead`() = testApplication {
        val r = rig(requirePin = false)
        r.registry.issue("UA", viaPin = true)
        val second = browser()
        val root = second.get("/")
        assertEquals(HttpStatusCode.OK, root.status)
        assertEquals("web/login.html", root.bodyAsText())
        assertNull("no credential may be issued without the phone", root.headers[HttpHeaders.SetCookie])
        assertTrue(second.joinState().contains("\"joinRequired\":true"))
        assertEquals(1, r.registry.devices.value.size)
    }

    @Test fun `an approved request turns into a credential on the next check`() = testApplication {
        val r = rig(requirePin = false)
        r.registry.issue("UA", viaPin = true)
        val second = browser()
        assertEquals(HttpStatusCode.Accepted, second.post("/api/join").status)
        assertEquals(HttpStatusCode.Accepted, second.post("/api/join/status").status)
        val request = r.joins.pending.value.single()
        r.joins.approve(request.id)
        val approved = second.post("/api/join/status")
        assertEquals(HttpStatusCode.OK, approved.status)
        assertTrue(approved.bodyAsText().contains("\"redirectTo\":\"/app\""))
        assertEquals(2, r.registry.devices.value.size)
        // 拿到的是真凭据：之后的请求按设备鉴权通过。
        assertTrue(second.joinState().contains("\"joinRequired\":true"))
        assertEquals(HttpStatusCode.Found, second.get("/").status)
    }

    @Test fun `asking twice from the same page does not queue a second dialog`() = testApplication {
        val r = rig(requirePin = false)
        r.registry.issue("UA", viaPin = true)
        val second = browser()
        second.post("/api/join")
        second.post("/api/join")
        assertEquals(1, r.joins.pending.value.size)
    }

    @Test fun `a refused request says so and issues nothing`() = testApplication {
        val r = rig(requirePin = false)
        r.registry.issue("UA", viaPin = true)
        val second = browser()
        second.post("/api/join")
        r.joins.deny(r.joins.pending.value.single().id)
        val denied = second.post("/api/join/status")
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertTrue(denied.bodyAsText().contains("\"status\":\"denied\""))
        assertEquals(1, r.registry.devices.value.size)
    }

    @Test fun `a page with no request gets told so`() = testApplication {
        rig(requirePin = false).registry.issue("UA", viaPin = true)
        val stray = browser()
        assertEquals(HttpStatusCode.Gone, stray.post("/api/join/status").status)
    }

    @Test fun `joining is refused while the normal way in is open`() = testApplication {
        rig(requirePin = false)
        assertEquals(HttpStatusCode.Conflict, browser().post("/api/join").status)
    }

    @Test fun `too many waiting requests are turned away`() = testApplication {
        val r = rig(requirePin = false)
        r.registry.issue("UA", viaPin = true)
        repeat(JoinRequests.MAX_PENDING) { assertEquals(HttpStatusCode.Accepted, browser().post("/api/join").status) }
        assertEquals(HttpStatusCode.TooManyRequests, browser().post("/api/join").status)
    }

    @Test fun `with pin on, the second computer asks to join once the pin is used`() = testApplication {
        val r = rig(requirePin = true)
        val first = browser()
        assertTrue(first.joinState().contains("\"joinRequired\":false"))
        first.post("/api/auth") { contentType(ContentType.Application.Json); setBody("""{"pin":"000000"}""") }
        val second = browser()
        assertTrue(second.joinState().contains("\"joinRequired\":true"))
        assertEquals(HttpStatusCode.Accepted, second.post("/api/join").status)
        r.joins.approve(r.joins.pending.value.single().id)
        assertEquals(HttpStatusCode.OK, second.post("/api/join/status").status)
        assertEquals(2, r.registry.devices.value.size)
    }
}
