package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** v1.17 section 1.3: thumbnail routing and the inline media whitelist. */
class FileRoutesThumbTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Harness(root: File) {
        val session = SessionState(nowMs = { 0L })
        val stats = TransferStats(nowMs = { 0L })
        val filesDir: File = File(root, "files").apply { mkdirs() }
        val thumbsDir: File = File(root, "thumbs").apply { mkdirs() }
        var generateCalls = 0
        var generatorResult: ByteArray? = "thumb-bytes".toByteArray()
        val store = object : FileStore {
            override fun fileDir(sessionId: Long): File = filesDir
            override fun thumbFile(sessionId: Long, fileId: String): File =
                File(thumbsDir, "$fileId.jpg")
        }
        val thumbnailer = ThumbnailGenerator { _, _, target ->
            generateCalls++
            generatorResult?.let { target.writeBytes(it); true } ?: false
        }

        fun addFile(fileId: String, mime: String, status: Message.File.Status) {
            File(filesDir, fileId).writeBytes("blob".toByteArray())
            session.addMessage(
                Message.File(
                    id = fileId.hashCode().toLong(), origin = Origin.BROWSER, timestamp = 0L,
                    fileId = fileId, name = "$fileId.bin", sizeBytes = 4L, mime = mime, status = status,
                )
            )
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
            onPersist = { },
            broadcastEvent = { _, _ -> },
            nowMs = { 0L },
            thumbnailer = h.thumbnailer,
        )
    }

    @Test fun `thumb requires the current browser session`() = testApplication {
        val h = Harness(tmp.root)
        h.addFile("private", "image/jpeg", Message.File.Status.COMPLETED)
        application {
            install(ContentNegotiation) { json() }
            routing { installFileRoutes(h, authRequired = true) }
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/files/private/thumb").status)
        assertEquals(0, h.generateCalls)
    }

    @Test fun `thumb generates once then serves from cache`() = testApplication {
        val h = Harness(tmp.root)
        h.addFile("img1", "image/jpeg", Message.File.Status.COMPLETED)
        application { install(ContentNegotiation) { json() }; routing { installFileRoutes(h) } }

        val first: HttpResponse = client.get("/api/files/img1/thumb")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("image/jpeg", first.headers[HttpHeaders.ContentType])
        assertArrayEquals("thumb-bytes".toByteArray(), first.readRawBytes())

        client.get("/api/files/img1/thumb")
        assertEquals(1, h.generateCalls)
    }

    @Test fun `thumb of non media mime is 404`() = testApplication {
        val h = Harness(tmp.root)
        h.addFile("doc1", "application/pdf", Message.File.Status.COMPLETED)
        application { install(ContentNegotiation) { json() }; routing { installFileRoutes(h) } }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/files/doc1/thumb").status)
        assertEquals(0, h.generateCalls)
    }

    @Test fun `thumb of in-progress file is 409`() = testApplication {
        val h = Harness(tmp.root)
        h.addFile("img2", "image/png", Message.File.Status.IN_PROGRESS)
        application { install(ContentNegotiation) { json() }; routing { installFileRoutes(h) } }
        assertEquals(HttpStatusCode(409, "Conflict"), client.get("/api/files/img2/thumb").status)
    }

    @Test fun `generator failure is 404 and leaves no cache file`() = testApplication {
        val h = Harness(tmp.root)
        h.generatorResult = null
        h.addFile("img3", "image/webp", Message.File.Status.COMPLETED)
        application { install(ContentNegotiation) { json() }; routing { installFileRoutes(h) } }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/files/img3/thumb").status)
        assertTrue(!File(h.thumbsDir, "img3.jpg").exists())
    }

    @Test fun `inline whitelisted mime serves inline with real content type`() = testApplication {
        val h = Harness(tmp.root)
        h.addFile("vid1", "video/mp4", Message.File.Status.COMPLETED)
        application { install(ContentNegotiation) { json() }; routing { installFileRoutes(h) } }
        val resp = client.get("/api/files/vid1?inline=1")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.headers[HttpHeaders.ContentDisposition]!!.startsWith("inline"))
        assertTrue(resp.headers[HttpHeaders.ContentType]!!.startsWith("video/mp4"))
    }

    @Test fun `inline svg is refused and stays attachment octet-stream`() = testApplication {
        val h = Harness(tmp.root)
        h.addFile("svg1", "image/svg+xml", Message.File.Status.COMPLETED)
        application { install(ContentNegotiation) { json() }; routing { installFileRoutes(h) } }
        val resp = client.get("/api/files/svg1?inline=1")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"))
        assertTrue(resp.headers[HttpHeaders.ContentType]!!.startsWith("application/octet-stream"))
    }

    @Test fun `plain download without inline keeps attachment behavior`() = testApplication {
        val h = Harness(tmp.root)
        h.addFile("img4", "image/jpeg", Message.File.Status.COMPLETED)
        application { install(ContentNegotiation) { json() }; routing { installFileRoutes(h) } }
        val resp = client.get("/api/files/img4")
        assertTrue(resp.headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"))
        assertTrue(resp.headers[HttpHeaders.ContentType]!!.startsWith("application/octet-stream"))
    }
}
