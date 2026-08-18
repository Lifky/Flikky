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
import io.ktor.http.ContentType
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
import org.junit.Test

class FavoriteRoutesTest {

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
        fileResolver: suspend (Long) -> java.io.File? = { null },
    ): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        routing {
            val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
            val authGate = AuthGate(required = true, pinAuth = pin)
            authRoutes(
                authGate,
                readAsset = { byteArrayOf() },
                publicThemeProvider = { WebThemeDto(themeSeed = null, themeDark = false) },
            )
            favoriteRoutes(authGate = authGate, listProvider = listProvider, fileResolver = fileResolver)
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
}
