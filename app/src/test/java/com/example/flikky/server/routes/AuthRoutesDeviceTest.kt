package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRoutesDeviceTest {
    private val chrome =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private fun pin() = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })

    private fun ApplicationTestBuilder.setUp(gate: AuthGate, landing: String = "/app") {
        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(gate, readAsset = { path -> path.toByteArray() }, redirectAfterLogin = { landing })
                get("/export") {
                    if (gate.isAuthorized(call.request.cookies[AUTH_COOKIE])) call.respondText("export")
                    else call.respond(HttpStatusCode.Unauthorized)
                }
                get("/api/probe") {
                    if (gate.isAuthorized(call.request.cookies[AUTH_COOKIE])) call.respondText("ok")
                    else call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }

    @Test fun `pin off issues a cookie on the landing redirect and still protects the api`() = testApplication {
        val registry = DeviceRegistry()
        setUp(AuthGate(required = false, pinAuth = pin(), registry = registry))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/probe").status)

        val http = createClient { install(HttpCookies) }
        assertEquals("web/app.html", http.get("/").bodyAsText())
        assertEquals(HttpStatusCode.OK, http.get("/api/probe").status)
        assertTrue(registry.hasCredentials())
    }

    @Test fun `pin off export landing receives the issued cookie first`() = testApplication {
        setUp(AuthGate(required = false, pinAuth = pin(), registry = DeviceRegistry()), landing = "/export")
        val http = createClient { install(HttpCookies) }
        assertEquals("export", http.get("/").bodyAsText())
    }

    @Test fun `pin on a second visit to slash with a valid cookie goes straight to the app`() = testApplication {
        setUp(AuthGate(required = true, pinAuth = pin(), registry = DeviceRegistry()))
        val http = createClient { install(HttpCookies); followRedirects = false }
        http.post("/api/auth") { contentType(ContentType.Application.Json); setBody("""{"pin":"000000"}""") }
        val again = http.get("/")
        assertEquals(HttpStatusCode.Found, again.status)
        assertEquals("/app", again.headers[HttpHeaders.Location])
    }

    @Test fun `pin on login names the device from the user agent`() = testApplication {
        val registry = DeviceRegistry()
        setUp(AuthGate(required = true, pinAuth = pin(), registry = registry))
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth") {
            header(HttpHeaders.UserAgent, chrome)
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals("Chrome · Windows", registry.devices.value.single().defaultLabel)
    }

    @Test fun `a revoked device is sent back to the login page`() = testApplication {
        val registry = DeviceRegistry()
        setUp(AuthGate(required = true, pinAuth = pin(), registry = registry))
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth") { contentType(ContentType.Application.Json); setBody("""{"pin":"000000"}""") }
        registry.revoke(registry.devices.value.single().deviceId)
        assertEquals("web/login.html", http.get("/").bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, http.get("/api/probe").status)
    }
}
