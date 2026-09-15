package com.example.flikky.server.routes

import com.example.flikky.server.dto.AlbumErrorDto
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.server.dto.AlbumStreamHeadDto
import com.example.flikky.server.dto.WireJson
import com.example.flikky.util.AlbumAccess
import com.example.flikky.util.AlbumItemId
import com.example.flikky.util.ThumbnailDiskCache
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

sealed interface AlbumResult<out T> {
    data class Ok<T>(val value: T) : AlbumResult<T>
    data object InvalidId : AlbumResult<Nothing>
    data object NotFound : AlbumResult<Nothing>
}

data class AlbumFileHandle(val fileName: String, val mime: String, val size: Long, val open: () -> InputStream)

interface MediaLibrary {
    fun count(): Int
    fun listStream(): Flow<List<AlbumItemDto>>
    fun open(id: AlbumItemId): AlbumResult<AlbumFileHandle>
    fun thumbnail(id: AlbumItemId, maxPx: Int): AlbumResult<ByteArray>
}

fun Route.albumRoutes(
    authGate: AuthGate,
    enabled: suspend () -> Boolean,
    access: () -> AlbumAccess,
    library: () -> MediaLibrary?,
    albumThumbFile: ((String) -> File)? = null,
    thumbnailCacheMaxBytes: () -> Long = { 100L * 1024L * 1024L },
) {
    val thumbnailCache = albumThumbFile?.let { provider ->
        ThumbnailDiskCache(provider("0".repeat(64)).parentFile ?: File("."), thumbnailCacheMaxBytes)
    }
    suspend fun ApplicationCall.passesGate(): Boolean {
        if (!authGate.isAuthorized(request.cookies[AUTH_COOKIE])) { respond(HttpStatusCode.Unauthorized); return false }
        if (!enabled()) { respond(HttpStatusCode.NotFound); return false }
        if (access() == AlbumAccess.None) { respond(HttpStatusCode.Forbidden, AlbumErrorDto("album_permission_required")); return false }
        return true
    }
    suspend fun ApplicationCall.parsedId(): AlbumItemId? {
        val parsed = AlbumItemId.parse(request.queryParameters["id"].orEmpty())
        if (parsed == null) respond(HttpStatusCode.BadRequest)
        return parsed
    }
    get("/api/album/list") {
        if (!call.passesGate()) return@get
        val lib = library() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        if (call.request.queryParameters["stream"] != "1") {
            call.respond(withContext(Dispatchers.IO) { buildList { lib.listStream().collect { addAll(it) } } })
            return@get
        }
        val total = withContext(Dispatchers.IO) { lib.count() }
        call.respondOutputStream(NDJSON_ALBUM, HttpStatusCode.OK) {
            fun line(value: String) { write(value.toByteArray()); write(LF_ALBUM); flush() }
            line(WireJson.encodeToString(AlbumStreamHeadDto.serializer(), AlbumStreamHeadDto(total)))
            lib.listStream().collect { batch -> batch.forEach { line(WireJson.encodeToString(AlbumItemDto.serializer(), it)) } }
            line(DONE_LINE_ALBUM)
        }
    }
    @Suppress("UNUSED_VARIABLE") val keepCacheForTask17 = thumbnailCache
}

private val NDJSON_ALBUM = ContentType("application", "x-ndjson")
private val LF_ALBUM = byteArrayOf(10)
private const val DONE_LINE_ALBUM = "{\"done\":true}"
