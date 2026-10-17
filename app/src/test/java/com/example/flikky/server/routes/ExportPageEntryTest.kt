package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 直接打开 /export（旧标签页、书签、换 IP 后）而没有有效凭据时，应送回入口页，
 * 由入口页签发（关 PIN）或要求输入 PIN —— 而不是一个走不下去的裸 401（v1.23 审查 Important #2）。
 */
class ExportPageEntryTest {
    private fun ApplicationTestBuilder.setUp(requirePin: Boolean) {
        val gate = AuthGate(
            required = requirePin,
            pinAuth = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" }),
            registry = DeviceRegistry(),
        )
        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(gate, readAsset = { path -> path.toByteArray() }, redirectAfterLogin = { "/export" })
                exportRoutes(
                    sessionState = SessionState(nowMs = { 0L }),
                    authGate = gate,
                    readAsset = { path -> path.toByteArray() },
                    exportedBy = { _ -> error("not expected") },
                    fileResolver = { _, _ -> null },
                    onZipSent = { },
                    now = { 0L },
                )
            }
        }
    }

    @Test fun `without a credential the export page sends the browser to the entry page`() = testApplication {
        setUp(requirePin = true)
        val http = createClient { followRedirects = false }
        val resp = http.get("/export")
        assertEquals(HttpStatusCode.Found, resp.status)
        assertEquals("/", resp.headers[HttpHeaders.Location])
    }

    @Test fun `with pin off a stale export tab lands on the export page again`() = testApplication {
        setUp(requirePin = false)
        val http = createClient { install(HttpCookies) }
        assertEquals("web/export.html", http.get("/export").bodyAsText())
    }
}
