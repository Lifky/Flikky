package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.FavoritesResponseDto
import com.example.flikky.server.dto.WebThemeDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FavoriteRoutesThumbTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun app(
        resolver: suspend (Long) -> FavoriteFileHandle?,
        enabled: Boolean = true,
        generator: ThumbnailGenerator,
        cacheRoot: File,
    ): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
        routing {
            val authGate = AuthGate(
                required = true,
                pinAuth = PinAuth(
                    nowMs = { 0L },
                    pinSupplier = { "000000" },
                    tokenSupplier = { "TOK" },
                ),
            )
            authRoutes(
                authGate = authGate,
                readAsset = { byteArrayOf() },
                publicThemeProvider = { WebThemeDto() },
            )
            favoriteRoutes(
                authGate = authGate,
                listProvider = { FavoritesResponseDto(emptyList(), emptyList()) },
                fileResolver = resolver,
                enabled = { enabled },
                favoriteThumbFile = { id -> File(cacheRoot, "$id.jpg") },
                thumbnailer = generator,
            )
        }
    }

    private suspend fun authenticate(http: HttpClient) {
        val response = http.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("{\"pin\":\"000000\"}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `favorite thumbnail checks auth before the feature switch`() = testApplication {
        val source = tmp.newFile("photo.jpg")
        var generated = 0
        application(
            app(
                resolver = { FavoriteFileHandle(source, source.name, "image/jpeg") },
                enabled = false,
                generator = ThumbnailGenerator { _, _, _ -> generated++; true },
                cacheRoot = tmp.newFolder("cache-gate"),
            ),
        )
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/favorites/12/thumb").status)
        assertEquals(0, generated)
    }

    @Test
    fun `favorite thumbnail returns 404 when switch is off`() = testApplication {
        val source = tmp.newFile("photo.jpg")
        application(
            app(
                resolver = { FavoriteFileHandle(source, source.name, "image/jpeg") },
                enabled = false,
                generator = ThumbnailGenerator { _, _, _ -> true },
                cacheRoot = tmp.newFolder("cache-off"),
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        assertEquals(HttpStatusCode.NotFound, http.get("/api/favorites/12/thumb").status)
    }

    @Test
    fun `favorite thumbnail validates numeric id and media file`() = testApplication {
        val source = tmp.newFile("document.pdf")
        val result = arrayOf<FavoriteFileHandle?>(null)
        application(
            app(
                resolver = { result[0] },
                generator = ThumbnailGenerator { _, _, _ -> true },
                cacheRoot = tmp.newFolder("cache-invalid"),
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        assertEquals(HttpStatusCode.BadRequest, http.get("/api/favorites/abc/thumb").status)
        result[0] = FavoriteFileHandle(source, source.name, "application/pdf")
        assertEquals(HttpStatusCode.NotFound, http.get("/api/favorites/12/thumb").status)
    }

    @Test
    fun `favorite thumbnail generates once then serves jpeg from cache`() = testApplication {
        val source = tmp.newFile("photo.jpg")
        val cache = tmp.newFolder("cache-hit")
        var calls = 0
        application(
            app(
                resolver = { FavoriteFileHandle(source, source.name, "image/jpeg") },
                generator = ThumbnailGenerator { _, _, target ->
                    calls++
                    target.writeBytes(byteArrayOf(6, 7, 8))
                    true
                },
                cacheRoot = cache,
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val first: HttpResponse = http.get("/api/favorites/12/thumb")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("image/jpeg", first.headers[HttpHeaders.ContentType])
        assertArrayEquals(byteArrayOf(6, 7, 8), first.readRawBytes())
        val second: HttpResponse = http.get("/api/favorites/12/thumb")
        assertArrayEquals(byteArrayOf(6, 7, 8), second.readRawBytes())
        assertEquals(1, calls)
        assertTrue(cache.resolve("12.jpg").isFile)
    }

    @Test
    fun `favorite thumbnail generation failure returns 404 and deletes partial`() = testApplication {
        val source = tmp.newFile("photo.webp")
        val cache = tmp.newFolder("cache-failure")
        application(
            app(
                resolver = { FavoriteFileHandle(source, source.name, "image/webp") },
                generator = ThumbnailGenerator { _, _, target ->
                    target.writeBytes(byteArrayOf(1))
                    false
                },
                cacheRoot = cache,
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        assertEquals(HttpStatusCode.NotFound, http.get("/api/favorites/12/thumb").status)
        assertTrue(!cache.resolve("12.jpg").exists())
    }
}
