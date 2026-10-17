package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.ServerRecallOutcome
import com.example.flikky.session.Message
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRoutesSenderDeviceTest {
    @Test fun `a text message records the device that sent it`() = testApplication {
        val registry = DeviceRegistry(idSupplier = { "dev-1" })
        val persisted = mutableListOf<Message>()
        val events = mutableListOf<Pair<String, String>>()
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
                    broadcastEvent = { type, json -> events += type to json },
                    nowMs = { 0L },
                    recallHandler = { _, _ -> ServerRecallOutcome.NotFound },
                )
            }
        }
        val http = createClient { install(HttpCookies) }
        http.post("/api/auth") { contentType(ContentType.Application.Json); setBody("""{"pin":"000000"}""") }
        http.post("/api/messages") { contentType(ContentType.Application.Json); setBody("""{"text":"hi"}""") }
        assertEquals("dev-1", persisted.single().senderDeviceId)
        // 别的浏览器要靠它判断「这条不是我发的」并画对头像（v1.23 多设备，装机反馈）。
        val added = events.single { it.first == "text_added" }.second
        assertEquals("dev-1", Json.parseToJsonElement(added).jsonObject["senderDeviceId"]?.jsonPrimitive?.content)
        val history = Json.parseToJsonElement(http.get("/api/messages").bodyAsText()).jsonObject
        val first = history["ordered"]!!.jsonArray.first().jsonObject
        assertEquals("dev-1", first["senderDeviceId"]?.jsonPrimitive?.content)
        // 浏览器渲染历史前就要知道「我是哪台」，否则别的电脑的消息会先被画到右边。
        assertEquals("dev-1", history["selfDeviceId"]?.jsonPrimitive?.content)
    }
}
