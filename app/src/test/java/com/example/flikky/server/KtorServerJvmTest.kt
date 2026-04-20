package com.example.flikky.server

import com.example.flikky.server.routes.FileStore
import com.example.flikky.server.routes.authRoutes
import com.example.flikky.server.routes.messageRoutes
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class KtorServerJvmTest {

    private class FakeStore : FileStore {
        private val dir: File = Files.createTempDirectory("flikky").toFile()
        override fun fileDir(sessionId: Long): File {
            val sessDir = File(File(dir, "sessions/$sessionId"), "files")
            sessDir.mkdirs()
            return sessDir
        }
    }

    @Test
    fun `auth then send text then fetch history`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val stats = TransferStats(nowMs = { 0L })
        @Suppress("UNUSED_VARIABLE")
        val store = FakeStore()

        application {
            install(ContentNegotiation) { json() }
            install(WebSockets) {}
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                messageRoutes(
                    session = session,
                    pinAuth = pin,
                    onPersist = { _ -> },
                    broadcastEvent = { _, _ -> },
                    nowMs = { 0L },
                )
            }
        }

        val http = createClient {
            install(HttpCookies)
        }

        val unauth: HttpResponse = http.get("/api/messages")
        assertEquals(HttpStatusCode.Unauthorized, unauth.status)

        val authResp: HttpResponse = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, authResp.status)
        val authJson = Json.parseToJsonElement(authResp.bodyAsText()).jsonObject
        assertTrue(authJson["ok"]!!.jsonPrimitive.content == "true")

        val sendResp: HttpResponse = http.post("/api/messages") {
            contentType(ContentType.Application.Json)
            setBody("""{"text":"hello"}""")
        }
        assertEquals(HttpStatusCode.OK, sendResp.status)
        val msgJson = Json.parseToJsonElement(sendResp.bodyAsText()).jsonObject
        assertEquals("hello", msgJson["content"]!!.jsonPrimitive.content)
        assertEquals("BROWSER", msgJson["origin"]!!.jsonPrimitive.content)

        val historyResp: HttpResponse = http.get("/api/messages")
        assertEquals(HttpStatusCode.OK, historyResp.status)
        val historyJson = Json.parseToJsonElement(historyResp.bodyAsText()).jsonObject
        val texts = historyJson["texts"]!!
        assertTrue(texts.toString().contains("hello"))
    }
}
