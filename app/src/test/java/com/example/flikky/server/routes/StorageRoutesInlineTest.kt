package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.StorageListDto
import com.example.flikky.util.SortSpec
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StorageRoutesInlineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun handle(name: String, mime: String): StorageFileHandle =
        StorageFileHandle(tmp.newFile(name).apply { writeBytes(byteArrayOf(1, 2)) }, name, mime)

    private fun app(current: Array<StorageFileHandle>): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        routing {
            storageRoutes(
                authGate = AuthGate(
                    false,
                    PinAuth(
                        nowMs = { 0L },
                        pinSupplier = { "000000" },
                        tokenSupplier = { "TOKEN" },
                    ),
                ),
                enabled = { true },
                hasPermission = { true },
                browser = {
                    object : StorageBrowser {
                        override fun list(relative: String, sort: SortSpec) =
                            StorageResult.Ok(StorageListDto("", emptyList()))
                        override fun listStream(relative: String, sort: SortSpec) =
                            StorageResult.Ok(StorageStream("", emptyFlow()))
                        override fun open(relative: String) = StorageResult.Ok(current[0])
                    }
                },
            )
        }
    }

    @Test
    fun `allowlisted storage media may render inline with server mime`() = testApplication {
        application(app(arrayOf(handle("photo.jpg", "image/jpeg"))))
        val response = client.get("/api/storage/file?path=photo.jpg&inline=1")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.startsWith("inline"))
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("image/jpeg"))
    }

    @Test
    fun `storage inline falls back for non allowlisted mime`() = testApplication {
        application(app(arrayOf(handle("document.pdf", "application/pdf"))))
        val response = client.get("/api/storage/file?path=document.pdf&inline=1")
        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"))
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("application/octet-stream"))
    }

    @Test
    fun `storage SVG never renders inline`() = testApplication {
        application(app(arrayOf(handle("vector.svg", "image/svg+xml"))))
        val response = client.get("/api/storage/file?path=vector.svg&inline=1")
        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"))
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("application/octet-stream"))
    }

    @Test
    fun `storage download remains attachment without inline`() = testApplication {
        application(app(arrayOf(handle("clip.mp4", "video/mp4"))))
        val response = client.get("/api/storage/file?path=clip.mp4")
        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"))
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("application/octet-stream"))
    }

    @Test
    fun `storage inline ignores a forged mime query`() = testApplication {
        application(app(arrayOf(handle("clip.mp4", "video/mp4"))))
        val response = client.get("/api/storage/file?path=clip.mp4&inline=1&mime=image/svg+xml")
        assertTrue(response.headers[HttpHeaders.ContentDisposition]!!.startsWith("inline"))
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("video/mp4"))
    }
}
