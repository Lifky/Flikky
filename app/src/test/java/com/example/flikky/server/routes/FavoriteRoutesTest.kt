package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.FavoriteGroupDto
import com.example.flikky.server.dto.FavoriteItemDto
import com.example.flikky.server.dto.FavoritesResponseDto
import com.example.flikky.server.dto.WebThemeDto
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FavoriteRoutesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sample = FavoritesResponseDto(
        groups = listOf(FavoriteGroupDto(id = 3, name = "常用片段", sortOrder = 0)),
        items = listOf(
            FavoriteItemDto(id = 11, kind = "TEXT", text = "ssh -p 2222", groupId = 3, createdAt = 900L),
            FavoriteItemDto(
                id = 12, kind = "FILE", fileName = "a.pdf", fileSize = 42L,
                mime = "application/pdf", createdAt = 800L,
            ),
        ),
    )

    private fun setupApp(
        listProvider: suspend () -> FavoritesResponseDto = { sample },
        fileResolver: suspend (Long) -> FavoriteFileHandle? = { null },
        enabled: suspend () -> Boolean = { true },
    ): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; encodeDefaults = true }) }
        routing {
            val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
            val authGate = AuthGate(required = true, pinAuth = pin)
            authRoutes(
                authGate,
                readAsset = { byteArrayOf() },
                publicThemeProvider = { WebThemeDto(themeSeed = null, themeDark = false) },
            )
            favoriteRoutes(
                authGate = authGate,
                listProvider = listProvider,
                fileResolver = fileResolver,
                enabled = enabled,
            )
        }
    }

    private suspend fun authenticate(http: io.ktor.client.HttpClient) {
        val resp: HttpResponse = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"000000"}""")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET favorites without cookie returns 401`() = testApplication {
        application(setupApp())
        val resp: HttpResponse = client.get("/api/favorites")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET favorites with valid cookie returns groups and items`() = testApplication {
        application(setupApp())
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/favorites")
        assertEquals(HttpStatusCode.OK, resp.status)

        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val groups = body["groups"]!!.jsonArray
        assertEquals(1, groups.size)
        assertEquals("常用片段", groups[0].jsonObject["name"]!!.jsonPrimitive.content)

        val items = body["items"]!!.jsonArray
        assertEquals(2, items.size)
        assertEquals("ssh -p 2222", items[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("a.pdf", items[1].jsonObject["fileName"]!!.jsonPrimitive.content)
    }

    @Test
    fun `favorites response never carries a depot fileId`() = testApplication {
        application(setupApp())
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val raw = http.get("/api/favorites").bodyAsText()
        assertEquals(false, raw.contains("fileId"))
    }

    @Test
    fun `empty favorites returns empty arrays not 404`() = testApplication {
        application(setupApp(listProvider = { FavoritesResponseDto(emptyList(), emptyList()) }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/favorites")
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        assertEquals(0, body["groups"]!!.jsonArray.size)
        assertEquals(0, body["items"]!!.jsonArray.size)
    }

    @Test
    fun `GET favorite file without cookie returns 401`() = testApplication {
        application(setupApp())
        val resp: HttpResponse = client.get("/api/favorites/12/file")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET favorite file streams bytes as an attachment using the recorded fileName`() = testApplication {
        // The on-disk depot file is named like a bare UUID (FavoriteFileStore.resolve) --
        // deliberately not "report.pdf" -- so this test actually exercises Fix 1: the
        // Content-Disposition filename must come from the handle, not from file.name.
        val f = tmp.newFile("3f2504e0-4f89-11d3-9a0c-0305e82c3301").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        application(
            setupApp(fileResolver = { id -> if (id == 12L) FavoriteFileHandle(f, "report.pdf") else null }),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/favorites/12/file")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(listOf<Byte>(1, 2, 3, 4, 5), resp.readRawBytes().toList())
        val cd = resp.headers[HttpHeaders.ContentDisposition] ?: ""
        assertEquals(true, cd.startsWith("attachment"))
        assertEquals(true, cd.contains("filename") && cd.contains("report.pdf"))
        assertEquals(false, cd.contains("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
        assertEquals("5", resp.headers[HttpHeaders.ContentLength])
    }

    @Test
    fun `GET favorite file ignores inline and mime query params and always downloads as octet-stream`() =
        testApplication {
            // R7: the favorites panel only offers download, never preview/lightbox (spec §4.2),
            // so this endpoint must not let the caller pick the rendered Content-Type -- even
            // when a client tries to force inline SVG rendering (a script-execution vector).
            val f = tmp.newFile("payload.bin").apply { writeBytes(byteArrayOf(9)) }
            application(
                setupApp(fileResolver = { id -> if (id == 12L) FavoriteFileHandle(f, "payload.bin") else null }),
            )
            val http = createClient { install(HttpCookies) }
            authenticate(http)

            val resp: HttpResponse = http.get("/api/favorites/12/file?inline=1&mime=image/svg+xml")
            assertEquals(HttpStatusCode.OK, resp.status)
            val cd = resp.headers[HttpHeaders.ContentDisposition] ?: ""
            assertEquals(true, cd.startsWith("attachment"))
            assertEquals("application/octet-stream", resp.headers[HttpHeaders.ContentType])
        }

    @Test
    fun `GET favorite file returns 404 when the row or file is gone`() = testApplication {
        application(setupApp(fileResolver = { null }))
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        val resp: HttpResponse = http.get("/api/favorites/999/file")
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `GET favorite file rejects a non-numeric id with 400`() = testApplication {
        application(setupApp())
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        // "abc" is a single path segment, so the route matches and toLongOrNull() reliably
        // returns null -- this is what actually exercises the numeric guard.
        val resp: HttpResponse = http.get("/api/favorites/abc/file")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `GET favorite file refuses a traversal-shaped id`() = testApplication {
        application(setupApp())
        val http = createClient { install(HttpCookies) }
        authenticate(http)

        // "..%2F..%2Fetc" decodes to "../../etc", which is three path segments once Ktor
        // decodes it -- whether that fails routing (404) or reaches the handler and fails
        // toLongOrNull() (400) depends on this Ktor version's decode-before-route behavior.
        // Only assert it is refused, not which status refuses it.
        val resp: HttpResponse = http.get("/api/favorites/..%2F..%2Fetc/file")
        assertNotEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `GET favorites returns 404 not 403 when the beta flag is off, even when authenticated`() =
        testApplication {
            application(setupApp(enabled = { false }))
            val http = createClient { install(HttpCookies) }
            authenticate(http)

            val resp: HttpResponse = http.get("/api/favorites")
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }

    @Test
    fun `GET favorite file returns 404 when the beta flag is off, even when authenticated`() =
        testApplication {
            val f = tmp.newFile("payload.bin").apply { writeBytes(byteArrayOf(1)) }
            application(
                setupApp(
                    fileResolver = { id -> if (id == 12L) FavoriteFileHandle(f, "report.pdf") else null },
                    enabled = { false },
                ),
            )
            val http = createClient { install(HttpCookies) }
            authenticate(http)

            val resp: HttpResponse = http.get("/api/favorites/12/file")
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }

    @Test
    fun `GET favorites without cookie still returns 401 even when the beta flag is off`() = testApplication {
        // Auth must be checked before the feature gate -- an unauthenticated caller must never
        // learn whether the beta flag is on or off.
        application(setupApp(enabled = { false }))
        val resp: HttpResponse = client.get("/api/favorites")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
}
