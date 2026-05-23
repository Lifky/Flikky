package com.example.flikky.server

import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.SessionExport
import com.example.flikky.server.routes.FileStore
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Boots a real [KtorServer] bound to 127.0.0.1 in each mode and asserts the
 * route set matches the mode: Transfer keeps message/file/WS routes, Export
 * replaces them with /export + /api/export/zip.
 *
 * We pick a high port range (18080-18099) to avoid colliding with the
 * production 8080-8099 band during local test runs.
 */
class KtorServerExportModeTest {

    private class FakeStore : FileStore {
        private val dir: File = Files.createTempDirectory("flikky-mode").toFile()
        override fun fileDir(sessionId: Long): File {
            val sessDir = File(File(dir, "sessions/$sessionId"), "files")
            sessDir.mkdirs()
            return sessDir
        }
    }

    private var server: KtorServer? = null

    @After
    fun tearDown() {
        server?.stop()
        server = null
    }

    private fun buildServer(
        mode: ServiceMode,
        session: SessionState,
        pin: PinAuth,
        onZipSent: suspend () -> Unit = {},
    ): KtorServer {
        val stats = TransferStats(nowMs = { 0L })
        val store = FakeStore()
        return KtorServer(
            host = "127.0.0.1",
            startPort = 18080,
            endPort = 18099,
            pinAuth = pin,
            session = session,
            stats = stats,
            fileStore = store,
            assetLoader = { byteArrayOf() },
            currentSessionId = { 1L },
            onPersistMessage = { _ -> },
            nowMs = { 1_700_000_000_000L },
            mode = mode,
            onZipSent = onZipSent,
        )
    }

    private fun makeSnapshot(): ExportSnapshot = ExportSnapshot(
        exportedAt = 1_700_000_000_000L,
        sessions = listOf(
            SessionExport(
                id = 42L,
                name = "Test",
                startedAt = 1_700_000_000_000L,
                endedAt = 1_700_000_300_000L,
                pinned = false,
                messages = listOf(
                    MessageExport.Text(
                        ts = 1_700_000_000_000L,
                        origin = Origin.PHONE,
                        content = "hello",
                    ),
                ),
            ),
        ),
    )

    private suspend fun authenticate(client: HttpClient, port: Int) {
        val resp: HttpResponse = client.post("http://127.0.0.1:$port/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `export mode does not mount messages route`() = runBlocking {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val s = buildServer(ServiceMode.Export, session, pin)
        server = s
        val port = s.start()

        HttpClient(CIO) { install(HttpCookies) }.use { http ->
            authenticate(http, port)
            val resp: HttpResponse = http.get("http://127.0.0.1:$port/api/messages")
            // authRoutes is still mounted; unauth path would be 401. With auth, the
            // messages route itself is missing so Ktor should respond 404.
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
    }

    @Test
    fun `export mode does not mount files route`() = runBlocking {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val s = buildServer(ServiceMode.Export, session, pin)
        server = s
        val port = s.start()

        HttpClient(CIO) { install(HttpCookies) }.use { http ->
            authenticate(http, port)
            val resp: HttpResponse = http.post("http://127.0.0.1:$port/api/files") {
                contentType(ContentType.Application.OctetStream)
                setBody(ByteArray(4))
            }
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
    }

    @Test
    fun `export mode mounts websocket route for health detection`() = runBlocking {
        // v1.3 test2 修订：export mode 现在也挂 /ws，让浏览器通过 WS onclose
        // 立即感知断网。Plain GET 到 /ws（无 Upgrade header）应返回 400/426
        // 而非 404——证明路由已挂载。
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val s = buildServer(ServiceMode.Export, session, pin)
        server = s
        val port = s.start()

        HttpClient(CIO) { install(HttpCookies) }.use { http ->
            authenticate(http, port)
            val resp: HttpResponse = http.get("http://127.0.0.1:$port/ws")
            // Ktor 对 WS 路由返回 400 (missing Upgrade) 或 426 (Upgrade Required)；
            // 不是 404，说明路由确实存在。
            assertTrue(
                "Expected 400/426 but got ${resp.status}",
                resp.status == HttpStatusCode.BadRequest || resp.status.value == 426
            )
        }
    }

    @Test
    fun `export mode mounts export zip route`() = runBlocking {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        session.armExport(
            ExportSession(sessionIds = listOf(42L), pin = "000000", createdAt = 0L),
            makeSnapshot(),
        )
        var zipCalls = 0
        val s = buildServer(ServiceMode.Export, session, pin, onZipSent = { zipCalls += 1 })
        server = s
        val port = s.start()

        HttpClient(CIO) { install(HttpCookies) }.use { http ->
            authenticate(http, port)
            val resp: HttpResponse = http.get("http://127.0.0.1:$port/api/export/zip")
            assertEquals(HttpStatusCode.OK, resp.status)
            // Drain so the server completes writing and fires onZipSent.
            resp.bodyAsBytes()
        }
        assertEquals(1, zipCalls)
    }

    @Test
    fun `transfer mode does not mount export zip route`() = runBlocking {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val s = buildServer(ServiceMode.Transfer, session, pin)
        server = s
        val port = s.start()

        HttpClient(CIO) { install(HttpCookies) }.use { http ->
            authenticate(http, port)
            val resp: HttpResponse = http.get("http://127.0.0.1:$port/api/export/zip")
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
    }

    @Test
    fun `transfer mode still serves messages route`() = runBlocking {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val s = buildServer(ServiceMode.Transfer, session, pin)
        server = s
        val port = s.start()

        HttpClient(CIO) { install(HttpCookies) }.use { http ->
            authenticate(http, port)
            val resp: HttpResponse = http.get("http://127.0.0.1:$port/api/messages")
            // Route exists: responds 200 (empty history) rather than 404.
            assertEquals(HttpStatusCode.OK, resp.status)
            assertNotEquals(HttpStatusCode.NotFound, resp.status)
            assertTrue(resp.bodyAsBytes().isNotEmpty())
        }
    }
}
