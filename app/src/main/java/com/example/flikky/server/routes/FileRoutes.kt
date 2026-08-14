package com.example.flikky.server.routes

import com.example.flikky.export.FilesZipWriter
import com.example.flikky.server.dto.FileMessageDto
import com.example.flikky.server.dto.FileProgressDto
import com.example.flikky.server.dto.FileReadyDto
import com.example.flikky.server.dto.FileRemovedDto
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import com.example.flikky.util.IdGen
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

interface FileStore {
    /** filesDir/sessions/$sessionId/files/, auto-created. */
    fun fileDir(sessionId: Long): File

    /** Thumbnail cache path for this file. The parent directory is auto-created. */
    fun thumbFile(sessionId: Long, fileId: String): File
}

/** Only these media types may be rendered inline. SVG stays excluded because it can execute script. */
internal val INLINE_MIME_WHITELIST = setOf(
    "image/jpeg", "image/png", "image/gif", "image/webp",
    "video/mp4", "video/webm", "video/3gpp", "video/quicktime", "video/x-matroska",
)

internal fun isMediaMime(mime: String): Boolean =
    mime.startsWith("image/") || mime.startsWith("video/")

fun Route.fileRoutes(
    session: SessionState,
    authGate: AuthGate,
    store: FileStore,
    stats: TransferStats,
    currentSessionId: () -> Long,
    onPersist: suspend (Message) -> Unit,
    broadcastEvent: suspend (type: String, payload: String) -> Unit,
    nowMs: () -> Long,
    thumbnailer: ThumbnailGenerator,
) {
    fun authed(call: ApplicationCall): Boolean {
        val token = call.request.cookies[AUTH_COOKIE]
        return authGate.isAuthorized(token)
    }

    post("/api/files") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@post }
        val sid = currentSessionId()
        val senderId = call.request.headers["X-Client-Id"]
        val declaredSize = call.request.headers["X-File-Size"]?.toLongOrNull() ?: 0L
        val multipart = call.receiveMultipart(formFieldLimit = Long.MAX_VALUE)

        // B16：每个 file part 独立走完整的消息创建 + 落盘 + 状态机 + 落库流程。
        // 异常时只回滚尚未完成的 in-flight part，已完成的 part 保持有效。
        val completed = mutableListOf<Message.File>()
        var inFlightMsgId = 0L
        var inFlightTarget: File? = null

        try {
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val fileId = UUID.randomUUID().toString()
                    val target = File(store.fileDir(sid), fileId)
                    val name = part.originalFileName ?: "unnamed"
                    val mime = part.contentType?.toString() ?: "application/octet-stream"
                    // X-File-Size 是请求级声明，只能作为首个 file part 的进度基准
                    val totalSize = if (completed.isEmpty() && declaredSize > 0) declaredSize else 0L

                    val msgId = IdGen.newMessageId()
                    inFlightMsgId = msgId
                    inFlightTarget = target
                    val inProgressMsg = Message.File(
                        id = msgId,
                        origin = Origin.BROWSER,
                        timestamp = nowMs(),
                        fileId = fileId,
                        name = name,
                        sizeBytes = totalSize,
                        mime = mime,
                        status = Message.File.Status.IN_PROGRESS,
                        senderId = senderId,
                    )
                    session.addMessage(inProgressMsg)
                    stats.incrementFileCount()
                    val inProgressDto = FileMessageDto(
                        inProgressMsg.id, inProgressMsg.origin.name, inProgressMsg.timestamp,
                        inProgressMsg.fileId, inProgressMsg.name, inProgressMsg.sizeBytes,
                        inProgressMsg.mime, inProgressMsg.status.name,
                        senderId = senderId,
                    )
                    broadcastEvent("file_added",
                        Json.encodeToString(FileMessageDto.serializer(), inProgressDto))

                    val input = part.provider()
                    var totalCopied = 0L
                    var lastReportedPct = -1
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (!input.isClosedForRead) {
                            val n = input.readAvailable(buf, 0, buf.size)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            totalCopied += n
                            stats.recordBytes(n.toLong())
                            if (totalSize > 0) {
                                val pct = ((totalCopied * 100) / totalSize).toInt()
                                if (pct >= lastReportedPct + 5) {
                                    lastReportedPct = pct
                                    session.updateProgress(msgId, totalCopied.toFloat() / totalSize)
                                    val progressDto = FileProgressDto(msgId, totalCopied, totalSize)
                                    broadcastEvent("file_progress",
                                        Json.encodeToString(FileProgressDto.serializer(), progressDto))
                                }
                            }
                        }
                    }

                    val completedMsg = inProgressMsg.copy(
                        timestamp = nowMs(),
                        sizeBytes = totalCopied,
                        status = Message.File.Status.COMPLETED,
                    )
                    session.updateMessage(msgId) { completedMsg }
                    session.clearProgress(msgId)
                    runCatching { onPersist(completedMsg) }

                    val readyDto = FileReadyDto(msgId, fileId, name, totalCopied)
                    broadcastEvent("file_ready",
                        Json.encodeToString(FileReadyDto.serializer(), readyDto))

                    completed += completedMsg
                    inFlightMsgId = 0L
                    inFlightTarget = null
                }
                part.dispose()
            }
        } catch (e: Exception) {
            if (inFlightMsgId > 0L) {
                session.updateMessage(inFlightMsgId) { msg ->
                    (msg as Message.File).copy(status = Message.File.Status.FAILED)
                }
                session.clearProgress(inFlightMsgId)
                stats.decrementFileCount()
                inFlightTarget?.delete()
                val removedDto = FileRemovedDto(inFlightMsgId)
                runCatching {
                    broadcastEvent("file_removed",
                        Json.encodeToString(FileRemovedDto.serializer(), removedDto))
                }
            }
            return@post
        }

        if (completed.isEmpty()) { call.respond(HttpStatusCode.BadRequest); return@post }

        // 响应保持单对象契约（当前 Web 端一请求一文件）；多 part 时返回最后一个
        val last = completed.last()
        val responseDto = FileMessageDto(
            last.id, last.origin.name, last.timestamp,
            last.fileId, last.name, last.sizeBytes,
            last.mime, last.status.name,
        )
        call.respond(responseDto)
    }

    get("/api/files/{id}/thumb") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }
        val id = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest); return@get }
        val sid = currentSessionId()

        val fileMsg = session.snapshot.value.messages
            .filterIsInstance<Message.File>()
            .firstOrNull { it.fileId == id }
            ?: run { call.respond(HttpStatusCode.NotFound); return@get }
        if (!isMediaMime(fileMsg.mime)) { call.respond(HttpStatusCode.NotFound); return@get }
        if (fileMsg.status == Message.File.Status.IN_PROGRESS) {
            call.respond(HttpStatusCode(409, "Conflict"), "File transfer in progress")
            return@get
        }
        val source = File(store.fileDir(sid), id)
        if (!source.exists()) { call.respond(HttpStatusCode.NotFound); return@get }

        val thumb = store.thumbFile(sid, id)
        if (!thumb.exists()) {
            val generated = runCatching { thumbnailer.generate(source, fileMsg.mime, thumb) }
                .getOrDefault(false)
            if (!generated || !thumb.exists() || thumb.length() == 0L) {
                thumb.delete()
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
        }
        call.respondBytes(thumb.readBytes(), ContentType.Image.JPEG)
    }

    get("/api/files/archive") {
        if (!authed(call)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@get
        }
        val sid = currentSessionId()
        val entries = session.snapshot.value.messages
            .filterIsInstance<Message.File>()
            .filter { it.status == Message.File.Status.COMPLETED }
            .mapNotNull { msg ->
                val file = File(store.fileDir(sid), msg.fileId)
                if (file.isFile) FilesZipWriter.Entry(msg.name, file) else null
            }
        if (entries.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMs()))
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, "flikky-files-$stamp.zip")
                .toString(),
        )
        call.respondOutputStream(
            contentType = ContentType.Application.Zip,
            status = HttpStatusCode.OK,
        ) {
            val downstream = this
            val counting = object : java.io.FilterOutputStream(downstream) {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    downstream.write(b, off, len)
                    stats.recordBytes(len.toLong())
                }
            }
            FilesZipWriter.write(counting, entries)
            counting.flush()
        }
    }

    get("/api/files/{id}") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }
        val id = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest); return@get }
        val sid = currentSessionId()

        val fileMsg = session.snapshot.value.messages
            .filterIsInstance<com.example.flikky.session.Message.File>()
            .firstOrNull { it.fileId == id }
        if (fileMsg != null && fileMsg.status == com.example.flikky.session.Message.File.Status.IN_PROGRESS) {
            call.respond(HttpStatusCode(409, "Conflict"), "File transfer in progress")
            return@get
        }

        val file = File(store.fileDir(sid), id)
        if (!file.exists()) { call.respond(HttpStatusCode.NotFound); return@get }
        val inline = call.request.queryParameters["inline"] == "1" &&
            fileMsg != null && fileMsg.mime in INLINE_MIME_WHITELIST
        val originalName = session.snapshot.value.messages
            .filterIsInstance<com.example.flikky.session.Message.File>()
            .firstOrNull { it.fileId == id }?.name ?: id
        call.response.header(
            HttpHeaders.ContentDisposition,
            (if (inline) ContentDisposition.Inline else ContentDisposition.Attachment)
                .withParameter("filename", originalName).toString(),
        )
        call.response.header(HttpHeaders.ContentLength, file.length().toString())
        call.respondBytesWriter(
            contentType = if (inline) ContentType.parse(fileMsg!!.mime) else ContentType.Application.OctetStream,
            status = HttpStatusCode.OK,
        ) {
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    writeFully(buf, 0, n)
                    stats.recordBytes(n.toLong())
                }
            }
        }
    }
}
