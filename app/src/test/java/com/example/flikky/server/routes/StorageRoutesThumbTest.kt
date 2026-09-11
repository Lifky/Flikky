package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.WebThemeDto
import io.ktor.client.HttpClient
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageRoutesThumbTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class Browser(var result: StorageResult<StorageFileHandle>) : StorageBrowser {
        var openCalls = 0

        override fun list(relative: String, sort: com.example.flikky.util.SortSpec) =
            StorageResult.NotFound

        override fun listStream(relative: String, sort: com.example.flikky.util.SortSpec) =
            StorageResult.Ok(StorageStream("", emptyFlow()))

        override fun open(relative: String): StorageResult<StorageFileHandle> {
            openCalls++
            return result
        }
    }

    private fun app(
        browser: Browser,
        enabled: Boolean = true,
        hasPermission: Boolean = true,
        maxBytes: Long = 1024 * 1024,
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
            storageRoutes(
                authGate = authGate,
                enabled = { enabled },
                hasPermission = { hasPermission },
                browser = { browser },
                storageThumbFile = { key -> File(cacheRoot, "$key.jpg") },
                thumbnailer = generator,
                thumbnailCacheMaxBytes = { maxBytes },
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

    private fun mediaFile(): File = tmp.newFile("photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun `storage thumbnail checks auth before the switch`() = testApplication {
        val source = mediaFile()
        val browser = Browser(StorageResult.Ok(StorageFileHandle(source, source.name, "image/jpeg")))
        var generated = 0
        application(
            app(
                browser = browser,
                enabled = false,
                generator = ThumbnailGenerator { _, _, _ -> generated++; true },
                cacheRoot = tmp.newFolder("cache"),
            ),
        )

        val response = client.get("/api/storage/thumb?path=photo.jpg")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, browser.openCalls)
        assertEquals(0, generated)
    }

    @Test
    fun `storage thumbnail gate returns switch and permission statuses`() = testApplication {
        val source = mediaFile()
        val browser = Browser(StorageResult.Ok(StorageFileHandle(source, source.name, "image/jpeg")))
        application(
            app(
                browser = browser,
                enabled = false,
                generator = ThumbnailGenerator { _, _, _ -> true },
                cacheRoot = tmp.newFolder("cache-off"),
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        assertEquals(HttpStatusCode.NotFound, http.get("/api/storage/thumb?path=photo.jpg").status)
        assertEquals(0, browser.openCalls)
    }

    @Test
    fun `storage thumbnail gate reports missing system permission`() = testApplication {
        val source = mediaFile()
        val browser = Browser(StorageResult.Ok(StorageFileHandle(source, source.name, "image/jpeg")))
        application(
            app(
                browser = browser,
                hasPermission = false,
                generator = ThumbnailGenerator { _, _, _ -> true },
                cacheRoot = tmp.newFolder("cache-permission"),
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val response = http.get("/api/storage/thumb?path=photo.jpg")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().contains("storage_permission_required"))
        assertEquals(0, browser.openCalls)
    }

    @Test
    fun `storage thumbnail reports invalid and non media paths`() = testApplication {
        val source = mediaFile()
        val invalid = Browser(StorageResult.InvalidPath)
        application(
            app(
                browser = invalid,
                generator = ThumbnailGenerator { _, _, _ -> true },
                cacheRoot = tmp.newFolder("cache-invalid"),
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        assertEquals(HttpStatusCode.BadRequest, http.get("/api/storage/thumb?path=../x").status)
        invalid.result = StorageResult.Ok(StorageFileHandle(source, source.name, "application/pdf"))
        assertEquals(HttpStatusCode.NotFound, http.get("/api/storage/thumb?path=photo.jpg").status)
    }

    @Test
    fun `storage thumbnail generates once then serves jpeg from cache`() = testApplication {
        val source = mediaFile()
        val browser = Browser(StorageResult.Ok(StorageFileHandle(source, source.name, "image/jpeg")))
        var calls = 0
        application(
            app(
                browser = browser,
                generator = ThumbnailGenerator { _, _, target ->
                    calls++
                    target.writeBytes(byteArrayOf(9, 8, 7))
                    true
                },
                cacheRoot = tmp.newFolder("cache-hit"),
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val first: HttpResponse = http.get("/api/storage/thumb?path=photo.jpg")
        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals("image/jpeg", first.headers[HttpHeaders.ContentType])
        assertArrayEquals(byteArrayOf(9, 8, 7), first.readRawBytes())
        val second: HttpResponse = http.get("/api/storage/thumb?path=photo.jpg")
        assertEquals(HttpStatusCode.OK, second.status)
        assertArrayEquals(byteArrayOf(9, 8, 7), second.readRawBytes())
        assertEquals(1, calls)
    }

    @Test
    fun `storage thumbnail generation failure returns 404 without a partial cache`() = testApplication {
        val source = mediaFile()
        val browser = Browser(StorageResult.Ok(StorageFileHandle(source, source.name, "image/webp")))
        val cache = tmp.newFolder("cache-failure")
        application(
            app(
                browser = browser,
                generator = ThumbnailGenerator { _, _, target ->
                    target.writeBytes(byteArrayOf(1))
                    false
                },
                cacheRoot = cache,
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        assertEquals(HttpStatusCode.NotFound, http.get("/api/storage/thumb?path=photo.jpg").status)
        assertTrue(cache.listFiles().orEmpty().none { it.isFile })
    }

    @Test
    fun `zero cache limit still returns a generated thumbnail without persisting`() = testApplication {
        val source = mediaFile()
        val browser = Browser(StorageResult.Ok(StorageFileHandle(source, source.name, "image/png")))
        val cache = tmp.newFolder("cache-zero")
        var calls = 0
        application(
            app(
                browser = browser,
                maxBytes = 0L,
                generator = ThumbnailGenerator { _, _, target ->
                    calls++
                    target.writeBytes(byteArrayOf(4, 5))
                    true
                },
                cacheRoot = cache,
            ),
        )
        val http = createClient { install(HttpCookies) }
        authenticate(http)
        val response = http.get("/api/storage/thumb?path=photo.jpg")
        assertEquals(HttpStatusCode.OK, response.status)
        assertArrayEquals(byteArrayOf(4, 5), response.readRawBytes())
        assertEquals(1, calls)
        assertTrue(cache.listFiles().orEmpty().none { it.isFile })
    }
}
