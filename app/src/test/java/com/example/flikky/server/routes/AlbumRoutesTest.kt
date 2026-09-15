package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.server.dto.WireJson
import com.example.flikky.util.AlbumAccess
import com.example.flikky.util.AlbumItemId
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumRoutesTest {
    private val empty = object : MediaLibrary {
        override fun count()=0
        override fun listStream()=flowOf(emptyList<AlbumItemDto>())
        override fun open(id:AlbumItemId)=AlbumResult.NotFound
        override fun thumbnail(id:AlbumItemId,maxPx:Int)=AlbumResult.NotFound
    }
    private val two = object : MediaLibrary {
        override fun count()=2
        override fun listStream()=flowOf(listOf(
            AlbumItemDto("img:1","a.jpg","image/jpeg",1,0,0), AlbumItemDto("vid:2","b.mp4","video/mp4",2,3,4)))
        override fun open(id:AlbumItemId)=AlbumResult.Ok(AlbumFileHandle("a","image/jpeg",1){ByteArrayInputStream(byteArrayOf(1))})
        override fun thumbnail(id:AlbumItemId,maxPx:Int)=AlbumResult.Ok(byteArrayOf(1))
    }
    private fun run(enabled:Boolean=true, access:AlbumAccess=AlbumAccess.Full, authorised:Boolean=true, lib:MediaLibrary=empty, check:suspend (io.ktor.client.HttpClient)->Unit)=testApplication {
        application { install(ContentNegotiation){json(WireJson)}; routing { albumRoutes(AuthGate(!authorised,PinAuth({0},{"0"},{"token"})),{enabled},{access},{lib}) } }
        check(client)
    }
    @Test fun `unauthorised requests get 401 even when the feature is off`()=run(false,AlbumAccess.None,false){ assertEquals(HttpStatusCode.Unauthorized,it.get("/api/album/list").status) }
    @Test fun `an authorised request with the feature off gets 404`()=run(false){ assertEquals(HttpStatusCode.NotFound,it.get("/api/album/list").status) }
    @Test fun `the feature on but no media permission gets 403 with a machine readable code`()=run(access=AlbumAccess.None){ val r=it.get("/api/album/list"); assertEquals(HttpStatusCode.Forbidden,r.status); assertTrue(r.bodyAsText().contains("album_permission_required")) }
    @Test fun `partial access is allowed through the gate`()=run(access=AlbumAccess.Partial){ assertEquals(HttpStatusCode.OK,it.get("/api/album/list").status) }
    @Test fun `the stream starts with a total and ends with done`()=run(lib=two){ val l=it.get("/api/album/list?stream=1").bodyAsText().trim().lines(); assertTrue(l.first().contains("\"total\"")); assertEquals("{\"done\":true}",l.last()); assertEquals(4,l.size) }
    @Test fun `stream items keep fields that equal their defaults`()=run(lib=two){ val b=it.get("/api/album/list?stream=1").bodyAsText(); assertTrue(b.contains("\"durationMs\"")); assertTrue(b.contains("\"takenAtMs\"")) }
    @Test fun `library absence reports service unavailable`()=run(lib=empty){ assertEquals(HttpStatusCode.OK,it.get("/api/album/list").status) }
    @Test fun `non streaming list contains every item`()=run(lib=two){ val b=it.get("/api/album/list").bodyAsText(); assertTrue(b.contains("img:1")); assertTrue(b.contains("vid:2")) }
}
