package com.example.flikky.server.routes

import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.SessionExport
import com.example.flikky.server.PinAuth
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ExportRoutesTest {

    private fun makeSnapshot(): ExportSnapshot = ExportSnapshot(
        exportedAt = 1_700_000_000_000L,
        sessions = listOf(
            SessionExport(
                id = 42L,
                name = "Test Session",
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

    private suspend fun authenticate(client: io.ktor.client.HttpClient) {
        val resp: HttpResponse = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `unauthenticated zip request returns 401`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                exportRoutes(
                    sessionState = session,
                    pinAuth = pin,
                    readAsset = { byteArrayOf() },
                    exportedBy = { _ -> error("not expected") },
                    fileResolver = { _, _ -> null },
                    onZipSent = { },
                    now = { 1_700_000_000_000L },
                )
            }
        }

        val resp: HttpResponse = client.get("/api/export/zip")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `authenticated zip request while Idle returns 409 Conflict`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                exportRoutes(
                    sessionState = session,
                    pinAuth = pin,
                    readAsset = { byteArrayOf() },
                    exportedBy = { _ -> error("not expected") },
                    fileResolver = { _, _ -> null },
                    onZipSent = { },
                    now = { 1_700_000_000_000L },
                )
            }
        }

        val http = createClient { install(HttpCookies) }
        authenticate(http)

        // exportMode is still Idle (armExport never called)
        val resp: HttpResponse = http.get("/api/export/zip")
        assertEquals(HttpStatusCode.Conflict, resp.status)
    }

    @Test
    fun `armed zip request returns 200 zip with attachment filename`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val snapshot = makeSnapshot()
        session.armExport(
            ExportSession(sessionIds = listOf(42L), pin = "000000", createdAt = 0L),
            snapshot,
        )

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                exportRoutes(
                    sessionState = session,
                    pinAuth = pin,
                    readAsset = { byteArrayOf() },
                    exportedBy = { _ -> snapshot },
                    fileResolver = { _, _ -> null },
                    onZipSent = { },
                    now = { 1_700_000_000_000L },
                )
            }
        }

        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/export/zip")
        assertEquals(HttpStatusCode.OK, resp.status)
        val ct = resp.headers[HttpHeaders.ContentType]
        assertNotNull(ct)
        assertTrue("content-type should be application/zip but was $ct", ct!!.startsWith("application/zip"))
        val disposition = resp.headers[HttpHeaders.ContentDisposition]
        assertNotNull(disposition)
        assertTrue(
            "disposition should include filename=flikky-export-*.zip but was $disposition",
            disposition!!.contains("flikky-export-") && disposition.contains(".zip"),
        )
    }

    @Test
    fun `zip body is a valid archive containing README txt`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val snapshot = makeSnapshot()
        session.armExport(
            ExportSession(sessionIds = listOf(42L), pin = "000000", createdAt = 0L),
            snapshot,
        )

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                exportRoutes(
                    sessionState = session,
                    pinAuth = pin,
                    readAsset = { byteArrayOf() },
                    exportedBy = { _ -> snapshot },
                    fileResolver = { _, _ -> null },
                    onZipSent = { },
                    now = { 1_700_000_000_000L },
                )
            }
        }

        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/export/zip")
        assertEquals(HttpStatusCode.OK, resp.status)

        val bytes = resp.bodyAsBytes()
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries.add(entry.name)
                zis.closeEntry()
            }
        }
        assertTrue("README.txt missing from zip; entries=$entries", entries.any { it == "README.txt" })
    }

    @Test
    fun `onZipSent fires exactly once after successful download`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val snapshot = makeSnapshot()
        session.armExport(
            ExportSession(sessionIds = listOf(42L), pin = "000000", createdAt = 0L),
            snapshot,
        )
        var calls = 0

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                exportRoutes(
                    sessionState = session,
                    pinAuth = pin,
                    readAsset = { byteArrayOf() },
                    exportedBy = { _ -> snapshot },
                    fileResolver = { _, _ -> null },
                    onZipSent = { calls += 1 },
                    now = { 1_700_000_000_000L },
                )
            }
        }

        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/export/zip")
        assertEquals(HttpStatusCode.OK, resp.status)
        // Drain body so the server finishes writing the response before we observe callbacks.
        resp.bodyAsBytes()

        assertEquals(1, calls)
    }

    @Test
    fun `export mode transitions to Done after successful download`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val snapshot = makeSnapshot()
        session.armExport(
            ExportSession(sessionIds = listOf(42L), pin = "000000", createdAt = 0L),
            snapshot,
        )

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                exportRoutes(
                    sessionState = session,
                    pinAuth = pin,
                    readAsset = { byteArrayOf() },
                    exportedBy = { _ -> snapshot },
                    fileResolver = { _, _ -> null },
                    onZipSent = { },
                    now = { 1_700_000_000_000L },
                )
            }
        }

        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/export/zip")
        assertEquals(HttpStatusCode.OK, resp.status)
        resp.bodyAsBytes()

        val mode = runBlocking { session.exportMode.value }
        assertTrue("exportMode should be Done but was $mode", mode is ExportMode.Done)
    }

    @Test
    fun `GET export page returns html while authenticated`() = testApplication {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        val session = SessionState(nowMs = { 0L })
        val stubHtml = "<html><body>stub</body></html>".toByteArray(Charsets.UTF_8)

        application {
            install(ContentNegotiation) { json() }
            routing {
                authRoutes(pin, readAsset = { byteArrayOf() })
                exportRoutes(
                    sessionState = session,
                    pinAuth = pin,
                    readAsset = { path ->
                        if (path == "web/export.html") stubHtml else throw java.io.FileNotFoundException(path)
                    },
                    exportedBy = { _ -> error("not expected") },
                    fileResolver = { _, _ -> null },
                    onZipSent = { },
                    now = { 1_700_000_000_000L },
                )
            }
        }

        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/export")
        assertEquals(HttpStatusCode.OK, resp.status)
        val ct = resp.headers[HttpHeaders.ContentType]
        assertNotNull(ct)
        assertTrue("content-type should be text/html but was $ct", ct!!.startsWith("text/html"))
    }
}
