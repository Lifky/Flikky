package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.session.Message
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * B16 回归：一个上传请求带多个 multipart file part 时，每个 part 必须独立走完
 * 消息创建 + 落盘 + 状态机 + 落库流程，不允许互相覆盖。
 */
class FileRoutesTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Harness(root: File) {
        val session = SessionState(nowMs = { 0L })
        val stats = TransferStats(nowMs = { 0L })
        val persisted = mutableListOf<Message>()
        val events = mutableListOf<Pair<String, String>>()
        val filesDir: File = File(root, "files").apply { mkdirs() }
        val store = object : FileStore {
            override fun fileDir(sessionId: Long): File = filesDir
        }
    }

    private fun io.ktor.server.routing.Route.installFileRoutes(h: Harness) {
        fileRoutes(
            session = h.session,
            authGate = AuthGate(
                required = false,
                pinAuth = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" }),
            ),
            store = h.store,
            stats = h.stats,
            currentSessionId = { 5L },
            onPersist = { h.persisted += it },
            broadcastEvent = { type, payload -> h.events += type to payload },
            nowMs = { 0L },
        )
    }

    private fun filePart(name: String, payload: ByteArray) = Headers.build {
        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$name\"")
        append(HttpHeaders.ContentType, "application/octet-stream")
    }.let { headers -> Triple(name, payload, headers) }

    @Test
    fun `single file part is saved completed and persisted once`() = testApplication {
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h) }
        }

        val payload = "hello flikky".toByteArray()
        val resp: HttpResponse = client.post("/api/files") {
            setBody(MultiPartFormDataContent(formData {
                val (name, bytes, headers) = filePart("a.bin", payload)
                append("file", bytes, headers)
            }))
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(1, h.persisted.size)
        val msg = h.persisted.single() as Message.File
        assertEquals(Message.File.Status.COMPLETED, msg.status)
        assertEquals("a.bin", msg.name)
        assertEquals(payload.size.toLong(), msg.sizeBytes)
        assertArrayEquals(payload, File(h.filesDir, msg.fileId).readBytes())
        assertEquals(1, h.events.count { it.first == "file_added" })
        assertEquals(1, h.events.count { it.first == "file_ready" })
    }

    @Test
    fun `two file parts each get their own file message and persistence`() = testApplication {
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h) }
        }

        val payloadA = "first-part-content".toByteArray()
        val payloadB = "second-part-content-with-different-length".toByteArray()
        val resp: HttpResponse = client.post("/api/files") {
            setBody(MultiPartFormDataContent(formData {
                val (_, bytesA, headersA) = filePart("a.bin", payloadA)
                append("file", bytesA, headersA)
                val (_, bytesB, headersB) = filePart("b.bin", payloadB)
                append("file", bytesB, headersB)
            }))
        }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(2, h.persisted.size)
        val msgA = h.persisted[0] as Message.File
        val msgB = h.persisted[1] as Message.File
        assertNotEquals(msgA.fileId, msgB.fileId)
        assertNotEquals(msgA.id, msgB.id)
        assertEquals("a.bin", msgA.name)
        assertEquals("b.bin", msgB.name)
        assertEquals(Message.File.Status.COMPLETED, msgA.status)
        assertEquals(Message.File.Status.COMPLETED, msgB.status)
        assertEquals(payloadA.size.toLong(), msgA.sizeBytes)
        assertEquals(payloadB.size.toLong(), msgB.sizeBytes)
        assertArrayEquals(payloadA, File(h.filesDir, msgA.fileId).readBytes())
        assertArrayEquals(payloadB, File(h.filesDir, msgB.fileId).readBytes())
        assertEquals(2, h.events.count { it.first == "file_added" })
        assertEquals(2, h.events.count { it.first == "file_ready" })

        val inMemory = h.session.snapshot.value.messages.filterIsInstance<Message.File>()
        assertEquals(2, inMemory.size)
        assertTrue(inMemory.all { it.status == Message.File.Status.COMPLETED })
    }

    @Test
    fun `request without any file part returns 400 and persists nothing`() = testApplication {
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h) }
        }

        val resp: HttpResponse = client.post("/api/files") {
            setBody(MultiPartFormDataContent(formData {
                append("note", "not a file")
            }))
        }

        assertEquals(HttpStatusCode.BadRequest, resp.status)
        assertEquals(0, h.persisted.size)
        assertEquals(0, h.events.size)
    }
}
