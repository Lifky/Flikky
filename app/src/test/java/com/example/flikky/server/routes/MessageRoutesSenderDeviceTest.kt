package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.ServerRecallOutcome
import com.example.flikky.session.Message
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRoutesSenderDeviceTest {
    @Test fun `a text message records the device that sent it`() = testApplication {
        val registry = DeviceRegistry(idSupplier = { "dev-1" })
        val persisted = mutableListOf<Message>()
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        application {
            install(ContentNegotiation) { json() }
            routing {
                val gate = AuthGate(required = true, pinAuth = pin, registry = registry)
                authRoutes(gate, readAsset = { byteArrayOf() })
                messageRoutes(
                    session = SessionState(nowMs = { 0L }),
                    authGate = gate,
                    onPersist = { persisted += it },
                    broadcastEvent = { _, _ -> },
                    nowMs = { 0L },
                    recallHandler = { _, _ -> ServerRecallOutcome.NotFound },
                )
            }
        }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth") { contentType(ContentType.Application.Json); setBody("""{"pin":"000000"}""") }
        http.post("/api/messages") { contentType(ContentType.Application.Json); setBody("""{"text":"hi"}""") }
        assertEquals("dev-1", persisted.single().senderDeviceId)
    }
}
