package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.util.IdGen
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
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
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

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
            override fun thumbFile(sessionId: Long, fileId: String): File =
                File(filesDir, "$fileId.thumb.jpg")
        }
    }

    private fun io.ktor.server.routing.Route.installFileRoutes(
        h: Harness,
        authRequired: Boolean = false,
    ) {
        fileRoutes(
            session = h.session,
            authGate = AuthGate(
                required = authRequired,
                pinAuth = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" }),
            ),
            store = h.store,
            stats = h.stats,
            currentSessionId = { 5L },
            onPersist = { h.persisted += it },
            broadcastEvent = { type, payload -> h.events += type to payload },
            nowMs = { 0L },
            thumbnailer = ThumbnailGenerator { _, _, _ -> false },
        )
    }

    private fun filePart(name: String, payload: ByteArray) = Headers.build {
        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$name\"")
        append(HttpHeaders.ContentType, "application/octet-stream")
    }.let { headers -> Triple(name, payload, headers) }

    private suspend fun HttpClient.uploadFile(name: String, payload: ByteArray): HttpResponse =
        post("/api/files") {
            setBody(MultiPartFormDataContent(formData {
                val (_, bytes, headers) = filePart(name, payload)
                append("file", bytes, headers)
            }))
        }

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

    /**
     * 往会话里塞一条「手机发过来的」已完成文件消息，并真的落一个文件。
     * 上传接口 `/api/files` 记的是 BROWSER origin（浏览器自己发出去的），
     * 而 ZIP 只该打包接收方向 —— 所以 archive 的用例必须用这个而不是 uploadFile。
     */
    private fun Harness.addPhoneFile(name: String, body: String) {
        // id 必须走真正的分配器。IdGen.messageCounter 是进程级 AtomicLong，同一次
        // JVM 运行里跨测试持续递增，所以手写 1L/2L 会撞上上传接口分配到的 id ——
        // 而 fileRoutes 的 `updateMessage(msgId) { completedMsg }` 的 transform
        // 忽略入参、无条件返回浏览器那条消息，撞上就把这条手机消息整条覆盖掉。
        val id = IdGen.newMessageId()
        val fileId = "phone-$id"
        File(filesDir, fileId).writeBytes(body.toByteArray())
        session.addMessage(
            Message.File(
                id = id,
                origin = Origin.PHONE,
                timestamp = 0L,
                fileId = fileId,
                name = name,
                sizeBytes = body.length.toLong(),
                mime = "text/plain",
                status = Message.File.Status.COMPLETED,
            ),
        )
    }

    @Test
    fun `archive streams all completed received files as zip with deduped names`() = testApplication {
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h) }
        }
        h.addPhoneFile("a.txt", "one")
        h.addPhoneFile("a.txt", "two")

        val response = client.get("/api/files/archive")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.contains("flikky-files-"))
        val names = mutableListOf<String>()
        val contents = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(response.bodyAsBytes())).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                names += entry.name
                contents += zin.readBytes().decodeToString()
            }
        }
        assertEquals(listOf("a.txt", "a (1).txt"), names)
        assertEquals(listOf("one", "two"), contents)
    }

    @Test
    fun `archive excludes files the browser itself uploaded`() = testApplication {
        // 用户报的缺陷：己方（浏览器）发出去的文件会被装进「打包为 ZIP」，
        // 等于把自己刚发出去的东西又下载回来一份。逐个下载没有这个问题 ——
        // 它在浏览器端只收集 .file-bubble.them（接收方向）。
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h) }
        }
        h.addPhoneFile("from-phone.txt", "received")
        client.uploadFile("from-browser.txt", "sent".toByteArray())

        val response = client.get("/api/files/archive")

        assertEquals(HttpStatusCode.OK, response.status)
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(response.bodyAsBytes())).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                names += entry.name
                zin.readBytes()
            }
        }
        assertEquals(listOf("from-phone.txt"), names)
    }

    @Test
    fun `archive returns 404 when the only completed files are browser uploads`() = testApplication {
        // 过滤掉发送方向后一个都不剩，必须是 404，而不是一个空 ZIP。
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h) }
        }
        client.uploadFile("only-mine.txt", "sent".toByteArray())

        assertEquals(HttpStatusCode.NotFound, client.get("/api/files/archive").status)
    }

    @Test
    fun `archive returns 404 when no completed files`() = testApplication {
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h) }
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/api/files/archive").status)
    }

    @Test
    fun `archive rejects unauthenticated requests when auth is required`() = testApplication {
        val h = Harness(tmp.root)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h, authRequired = true) }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/files/archive").status)
    }
}
