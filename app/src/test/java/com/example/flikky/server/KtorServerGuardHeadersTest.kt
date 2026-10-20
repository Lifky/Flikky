package com.example.flikky.server

import com.example.flikky.server.routes.FileStore
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * v1.24（B55#5）：同源守卫拒绝的 403 也要带安全响应头。安全响应头曾注册在守卫之后，
 * 守卫 `finish()` 时它们还没加上。
 */
class KtorServerGuardHeadersTest {
    private val dir: File = Files.createTempDirectory("flikky-guard").toFile()
    private var server: KtorServer? = null

    @After fun tearDown() {
        server?.stop()
        server = null
        dir.deleteRecursively()
    }

    @Test fun `a refused cross-origin request still carries the security headers`() = runBlocking {
        val s = KtorServer(
            host = "127.0.0.1",
            startPort = 18140,
            endPort = 18159,
            pinAuth = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" }),
            session = SessionState(nowMs = { 0L }),
            stats = TransferStats(nowMs = { 0L }),
            fileStore = object : FileStore {
                override fun fileDir(sessionId: Long): File = File(dir, "files").apply { mkdirs() }
                override fun thumbFile(sessionId: Long, fileId: String): File = File(dir, "$fileId.jpg")
            },
            assetLoader = { byteArrayOf() },
            currentSessionId = { 1L },
            onPersistMessage = { _ -> },
        )
        server = s
        val port = s.start()
        HttpClient(CIO).use { client ->
            val resp = client.post("http://127.0.0.1:$port/api/messages") {
                header(HttpHeaders.Origin, "http://evil.example")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
            assertNotNull(resp.headers["Content-Security-Policy"])
            assertEquals("nosniff", resp.headers["X-Content-Type-Options"])
            assertEquals("DENY", resp.headers["X-Frame-Options"])
        }
    }
}
