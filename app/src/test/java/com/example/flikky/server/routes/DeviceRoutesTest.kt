package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.session.PeerDevice
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRoutesTest {
    @Test fun `a device renames itself and picks its own avatar`() = testApplication {
        val registry = DeviceRegistry(idSupplier = { "dev-1" })
        val pushed = mutableListOf<PeerDevice>()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            routing {
                val gate = AuthGate(required = false, pinAuth = pin, registry = registry)
                authRoutes(gate, readAsset = { byteArrayOf() })
                deviceRoutes(gate, registry, onSelfUpdated = { pushed += it })
            }
        }
        val http = createClient { install(HttpCookies) }
        http.get("/")
        val resp = http.put("/api/devices/self") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"  客厅电脑 ","avatarKey":"icon:star"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("\"name\":\"客厅电脑\""))
        val stored = registry.device("dev-1")!!
        assertEquals("客厅电脑", stored.name)
        assertEquals("icon:star", stored.avatarKey)
        assertTrue(stored.avatarExplicit)
        assertEquals(listOf("dev-1"), pushed.map { it.deviceId })
    }

    @Test fun `without a credential the request is rejected`() = testApplication {
        val registry = DeviceRegistry()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            routing { deviceRoutes(AuthGate(required = false, pinAuth = pin, registry = registry), registry) }
        }
        val resp = client.put("/api/devices/self") { contentType(ContentType.Application.Json); setBody("""{"name":"x"}""") }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test fun `a malformed avatar key is refused`() = testApplication {
        val registry = DeviceRegistry()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            routing {
                val gate = AuthGate(required = false, pinAuth = pin, registry = registry)
                authRoutes(gate, readAsset = { byteArrayOf() })
                deviceRoutes(gate, registry)
            }
        }
        val http = createClient { install(HttpCookies) }
        http.get("/")
        val resp = http.put("/api/devices/self") { contentType(ContentType.Application.Json); setBody("""{"avatarKey":"<b>"}""") }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }
}
