package com.example.flikky.server.routes

import com.example.flikky.server.dto.AlbumErrorDto
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.server.dto.AlbumStreamHeadDto
import com.example.flikky.server.dto.WireJson
import com.example.flikky.util.AlbumAccess
import com.example.flikky.util.AlbumItemId
import com.example.flikky.util.ThumbnailDiskCache
import com.example.flikky.util.thumbnailCacheKey
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
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

    get("/api/album/file") {
        if (!call.passesGate()) return@get
        val id = call.parsedId() ?: return@get
        val lib = library() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        when (val result = lib.open(id)) {
            is AlbumResult.Ok -> {
                val handle = result.value
                val inline = call.request.queryParameters["inline"] == "1" &&
                    handle.mime in ALBUM_INLINE_MIME_WHITELIST
                call.respondAlbumFile(handle, inline, stillAllowed = enabled)
            }
            AlbumResult.InvalidId -> call.respond(HttpStatusCode.BadRequest)
            AlbumResult.NotFound -> call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/api/album/thumb") {
        if (!call.passesGate()) return@get
        val id = call.parsedId() ?: return@get
        val lib = library() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        val cacheName = albumThumbFile?.let { provider ->
            provider(thumbnailCacheKey(id.format(), 0L, 0L)).name
        }
        if (thumbnailCache != null && cacheName != null) {
            val cached = thumbnailCache.get(cacheName)
            if (cached != null && cached.isFile && cached.length() > 0L) {
                call.respondBytes(cached.readBytes(), ContentType.Image.JPEG)
                return@get
            }
            cached?.delete()
        }

        when (val made = withContext(Dispatchers.IO) { lib.thumbnail(id, ALBUM_THUMB_MAX_PX) }) {
            is AlbumResult.Ok -> {
                val bytes = made.value
                if (bytes.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                if (thumbnailCache != null && cacheName != null && thumbnailCacheMaxBytes() > 0L) {
                    thumbnailCache.put(cacheName) { file ->
                        file.writeBytes(bytes)
                        true
                    }
                }
                call.respondBytes(bytes, ContentType.Image.JPEG)
            }
            AlbumResult.InvalidId -> call.respond(HttpStatusCode.BadRequest)
            AlbumResult.NotFound -> call.respond(HttpStatusCode.NotFound)
        }
    }
}

private val NDJSON_ALBUM = ContentType("application", "x-ndjson")
private val LF_ALBUM = byteArrayOf(10)
private const val DONE_LINE_ALBUM = "{\"done\":true}"

private const val ALBUM_THUMB_MAX_PX = 512

private val ALBUM_INLINE_MIME_WHITELIST = setOf(
    "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif",
    "video/mp4", "video/webm",
)

/**
 * Streams one album item while re-reading the live peer-access gate before every block.
 * Keeping [stillAllowed] as a lambda makes disabling album access stop an in-progress download.
 */
private suspend fun ApplicationCall.respondAlbumFile(
    handle: AlbumFileHandle,
    inline: Boolean,
    stillAllowed: suspend () -> Boolean,
) {
    response.header(
        HttpHeaders.ContentDisposition,
        (if (inline) ContentDisposition.Inline else ContentDisposition.Attachment)
            .withParameter(ContentDisposition.Parameters.FileName, handle.fileName)
            .toString(),
    )
    if (handle.size > 0L) {
        response.header(HttpHeaders.ContentLength, handle.size.toString())
    }
    respondOutputStream(
        contentType = if (inline) ContentType.parse(handle.mime) else ContentType.Application.OctetStream,
        status = HttpStatusCode.OK,
    ) {
        handle.open().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                if (!stillAllowed()) break
                write(buffer, 0, read)
            }
            flush()
        }
    }
}
