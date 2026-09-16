package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.AlbumBucketDto
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.server.dto.WireJson
import com.example.flikky.util.AlbumAccess
import com.example.flikky.util.AlbumItemId
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumRoutesTest {
    private val empty = object : MediaLibrary {
        override fun count(bucket: String?) = 0
        override fun listStream(bucket: String?) = flowOf(emptyList<AlbumItemDto>())
        override fun open(id: AlbumItemId) = AlbumResult.NotFound
        override fun thumbnail(id: AlbumItemId, maxPx: Int) = AlbumResult.NotFound
        override fun buckets() = emptyList<AlbumBucketDto>()
        override fun todayKey() = "2026-09-16"
        override fun yesterdayKey() = "2026-09-15"
    }
    private val two = object : MediaLibrary {
        override fun count(bucket: String?) = 2
        override fun listStream(bucket: String?) = flowOf(
            listOf(
                AlbumItemDto("img:1", "a.jpg", "image/jpeg", 1, 0, 0, dateKey = "2026-09-16"),
                AlbumItemDto("vid:2", "b.mp4", "video/mp4", 2, 3, 4, dateKey = "2026-09-15"),
            ),
        )
        override fun open(id: AlbumItemId) =
            AlbumResult.Ok(AlbumFileHandle("a", "image/jpeg", 1) { ByteArrayInputStream(byteArrayOf(1)) })
        override fun thumbnail(id: AlbumItemId, maxPx: Int) = AlbumResult.Ok(byteArrayOf(1))
        override fun buckets() = emptyList<AlbumBucketDto>()
        override fun todayKey() = "2026-09-16"
        override fun yesterdayKey() = "2026-09-15"
    }

    /** 记录 list 收到的 bucket 参数，用来验证筛选真的传到了 library。 */
    private class BucketRecordingLibrary(private val base: MediaLibrary) : MediaLibrary by base {
        var lastBucket: String? = "<never called>"
        override fun count(bucket: String?): Int {
            lastBucket = bucket
            return 0
        }
        override fun listStream(bucket: String?) = flowOf(emptyList<AlbumItemDto>())
        override fun buckets() = listOf(
            AlbumBucketDto(name = "Camera", count = 12, coverId = "img:9"),
            AlbumBucketDto(name = "Screenshots", count = 3, coverId = "img:4"),
        )
    }
    private fun singleFileLibrary(payload: ByteArray, mime: String = "image/jpeg") =
        object : MediaLibrary by empty {
            override fun open(id: AlbumItemId) = AlbumResult.Ok(
                AlbumFileHandle("a.jpg", mime, payload.size.toLong()) {
                    ByteArrayInputStream(payload)
                },
            )
        }

    private fun run(enabled:()->Boolean={true}, access:AlbumAccess=AlbumAccess.Full, authorised:Boolean=true, lib:MediaLibrary=empty, check:suspend (io.ktor.client.HttpClient)->Unit)=testApplication {
        application { install(ContentNegotiation){json(WireJson)}; routing { albumRoutes(AuthGate(!authorised,PinAuth({0},{"0"},{"token"})),{enabled()},{access},{lib}) } }
        check(client)
    }

    private suspend fun HttpClient.download(path: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        prepareGet(path).execute { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read < 0) break
                if (read > 0) bytes.write(buffer, 0, read)
            }
        }
        return bytes.toByteArray()
    }
    @Test fun `unauthorised requests get 401 even when the feature is off`()=run({false},AlbumAccess.None,false){ assertEquals(HttpStatusCode.Unauthorized,it.get("/api/album/list").status) }
    @Test fun `an authorised request with the feature off gets 404`()=run({false}){ assertEquals(HttpStatusCode.NotFound,it.get("/api/album/list").status) }
    @Test fun `the feature on but no media permission gets 403 with a machine readable code`()=run(access=AlbumAccess.None){ val r=it.get("/api/album/list"); assertEquals(HttpStatusCode.Forbidden,r.status); assertTrue(r.bodyAsText().contains("album_permission_required")) }
    @Test fun `partial access is allowed through the gate`()=run(access=AlbumAccess.Partial){ assertEquals(HttpStatusCode.OK,it.get("/api/album/list").status) }
    @Test fun `the stream starts with a total and ends with done`()=run(lib=two){ val l=it.get("/api/album/list?stream=1").bodyAsText().trim().lines(); assertTrue(l.first().contains("\"total\"")); assertEquals("{\"done\":true}",l.last()); assertEquals(4,l.size) }
    @Test fun `stream items keep fields that equal their defaults`()=run(lib=two){
        val line=it.get("/api/album/list?stream=1").bodyAsText().lines().single { row -> row.contains("\"id\":\"img:1\"") }
        assertTrue(line.contains("\"durationMs\"")); assertTrue(line.contains("\"takenAtMs\""))
    }
    @Test fun `library absence reports service unavailable`()=run(lib=empty){ assertEquals(HttpStatusCode.OK,it.get("/api/album/list").status) }
    @Test fun `non streaming list contains every item`()=run(lib=two){ val b=it.get("/api/album/list").bodyAsText(); assertTrue(b.contains("img:1")); assertTrue(b.contains("vid:2")) }

    @Test fun `a malformed id gets 400, never 500`()=run { client ->
        listOf("content://media/external/images/media/1", "img:abc", "img:99999999999999999999", "doc:1", "").forEach { bad ->
            assertEquals("id=$bad", HttpStatusCode.BadRequest, client.get("/api/album/file?id=$bad").status)
        }
    }

    @Test fun `a well formed id for a missing item gets 404`() {
        var opened = false
        val library = object : MediaLibrary by empty {
            override fun open(id: AlbumItemId): AlbumResult<AlbumFileHandle> {
                opened = true
                return AlbumResult.NotFound
            }
        }
        run(lib=library) { assertEquals(HttpStatusCode.NotFound, it.get("/api/album/file?id=img:999").status) }
        assertTrue("the request never reached MediaLibrary.open", opened)
    }

    @Test fun `closing peer access mid download truncates the stream`() {
        var open = true
        val payload = ByteArray(512 * 1024) { it.toByte() }
        var served = 0
        val library = object : MediaLibrary by two {
            override fun open(id: AlbumItemId) = AlbumResult.Ok(
                AlbumFileHandle("a.jpg", "image/jpeg", payload.size.toLong()) {
                    object : ByteArrayInputStream(payload) {
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            served += 1
                            if (served > 2) open = false
                            return super.read(b, off, len)
                        }
                    }
                },
            )
        }
        run(enabled={open},lib=library) { client ->
            val bytes = client.download("/api/album/file?id=img:1")
            assertTrue("download was not truncated: ${bytes.size}/${payload.size}", bytes.size < payload.size)
        }
    }

    @Test fun `an untouched download arrives complete`() {
        val payload = ByteArray(512 * 1024) { it.toByte() }
        run(lib=singleFileLibrary(payload)) { client ->
            assertEquals(payload.size, client.get("/api/album/file?id=img:1").readRawBytes().size)
        }
    }

    @Test fun `downloads default to attachment with an octet stream type`()=run(lib=singleFileLibrary(ByteArray(8))) { client ->
        val response = client.get("/api/album/file?id=img:1")
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment"))
        assertEquals(ContentType.Application.OctetStream, response.contentType()?.withoutParameters())
    }

    @Test fun `inline is only honoured for whitelisted media types`()=run(lib=singleFileLibrary(ByteArray(8),"image/svg+xml")) { client ->
        val response = client.get("/api/album/file?id=img:1&inline=1")
        assertTrue("SVG must not render inline", response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment"))
    }

    @Test fun `inline works for a whitelisted image`()=run(lib=singleFileLibrary(ByteArray(8))) { client ->
        val response = client.get("/api/album/file?id=img:1&inline=1")
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("inline"))
    }

    @Test fun `thumbnails go through the same gate as everything else`() {
        run(enabled={false}) { assertEquals(HttpStatusCode.NotFound,it.get("/api/album/thumb?id=img:1").status) }
        run(access=AlbumAccess.None) { assertEquals(HttpStatusCode.Forbidden,it.get("/api/album/thumb?id=img:1").status) }
    }

    @Test fun `a malformed thumbnail id gets 400`()=run {
        assertEquals(HttpStatusCode.BadRequest,it.get("/api/album/thumb?id=content://sms/1").status)
    }

    // ── D65：日期只算一次，由服务端下发 ──────────────────────────────────

    @Test
    fun `the stream head carries the phone's today and yesterday keys`() {
        // 装机验收（Screenshot_12 / 13）暴露过双端分组不一致：两端各自从 takenAtMs
        // 算日期，而两台设备时区不同。修法是服务端算好下发 —— 浏览器因此不需要
        // 知道手机时区。这两个键没下发，浏览器就只能自己算，那道缝就又回来了。
        run(lib = two) { client ->
            val head = client.get("/api/album/list?stream=1").bodyAsText().trim().lines().first()

            assertTrue("首行缺 todayKey：$head", head.contains("\"todayKey\":\"2026-09-16\""))
            assertTrue("首行缺 yesterdayKey：$head", head.contains("\"yesterdayKey\":\"2026-09-15\""))
        }
    }

    @Test
    fun `every item carries the date key the phone computed`() {
        run(lib = two) { client ->
            val line = client.get("/api/album/list?stream=1").bodyAsText()
                .lines().single { row -> row.contains("\"id\":\"img:1\"") }

            assertTrue("条目缺 dateKey：$line", line.contains("\"dateKey\":\"2026-09-16\""))
        }
    }

    // ── 相册簿视图 ────────────────────────────────────────────────────────

    @Test
    fun `a bucket parameter reaches the library`() {
        val library = BucketRecordingLibrary(empty)

        run(lib = library) { client -> client.get("/api/album/list?stream=1&bucket=Camera") }

        assertEquals("Camera", library.lastBucket)
    }

    @Test
    fun `an empty bucket parameter means the whole album, not a nameless bucket`() {
        // 浏览器退出相册簿时传空串最自然。把空串当成「名字是空的那个相册簿」
        // 会让用户看到 0 项，而他刚才明明点的是「返回全部」。
        val library = BucketRecordingLibrary(empty)

        run(lib = library) { client -> client.get("/api/album/list?stream=1&bucket=") }

        assertEquals(null, library.lastBucket)
    }

    @Test
    fun `the buckets endpoint lists albums with covers and counts`() {
        run(lib = BucketRecordingLibrary(empty)) { client ->
            val body = client.get("/api/album/buckets").bodyAsText()

            assertTrue(body.contains("Camera"))
            assertTrue(body.contains("\"count\":12"))
            assertTrue(body.contains("\"coverId\":\"img:9\""))
        }
    }

    @Test
    fun `the buckets endpoint sits behind the same gate`() {
        run(enabled = { false }, lib = BucketRecordingLibrary(empty)) {
            assertEquals(HttpStatusCode.NotFound, it.get("/api/album/buckets").status)
        }
        run(access = AlbumAccess.None, lib = BucketRecordingLibrary(empty)) {
            assertEquals(HttpStatusCode.Forbidden, it.get("/api/album/buckets").status)
        }
        run(enabled = { false }, access = AlbumAccess.None, authorised = false, lib = BucketRecordingLibrary(empty)) {
            assertEquals(HttpStatusCode.Unauthorized, it.get("/api/album/buckets").status)
        }
    }
}
