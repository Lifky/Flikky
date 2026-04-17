package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.FileMessageDto
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
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.writeFully
import java.io.File
import java.util.UUID

interface FileStore {
    fun fileDir(): File
    fun registerPushFromPhone(
        fileId: String,
        name: String,
        size: Long,
        mime: String,
        input: () -> java.io.InputStream,
    )
    fun takePushedFile(fileId: String): PushedFile?
}

data class PushedFile(
    val name: String,
    val size: Long,
    val mime: String,
    val input: java.io.InputStream,
)

fun Route.fileRoutes(
    session: SessionState,
    pinAuth: PinAuth,
    store: FileStore,
    stats: TransferStats,
    broadcastEvent: suspend (type: String, payload: String) -> Unit,
    nowMs: () -> Long,
) {
    fun authed(call: ApplicationCall): Boolean {
        val token = call.request.cookies[AUTH_COOKIE]
        return token != null && pinAuth.validateToken(token)
    }

    post("/api/files") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@post }
        val multipart = call.receiveMultipart()
        var savedName: String? = null
        var savedSize: Long = 0L
        var savedMime: String = "application/octet-stream"
        val fileId = UUID.randomUUID().toString()
        val target = File(store.fileDir(), fileId)

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                savedName = part.originalFileName ?: "unnamed"
                savedMime = part.contentType?.toString() ?: savedMime
                savedSize = part.provider().copyAndClose(target.writeChannel())
                stats.recordBytes(savedSize)
            }
            part.dispose()
        }

        val msg = Message.File(
            id = IdGen.newMessageId(),
            origin = Origin.BROWSER,
            timestamp = nowMs(),
            fileId = fileId,
            name = savedName ?: "unnamed",
            sizeBytes = savedSize,
            mime = savedMime,
            status = Message.File.Status.COMPLETED,
        )
        stats.incrementFileCount()
        session.addMessage(msg)

        val dto = FileMessageDto(
            msg.id, msg.origin.name, msg.timestamp, msg.fileId, msg.name, msg.sizeBytes, msg.mime, msg.status.name,
        )
        broadcastEvent("file_added", kotlinx.serialization.json.Json.encodeToString(FileMessageDto.serializer(), dto))
        call.respond(dto)
    }

    get("/api/files/{id}") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }
        val id = call.parameters["id"] ?: run { call.respond(HttpStatusCode.BadRequest); return@get }

        val phonePush = store.takePushedFile(id)
        if (phonePush != null) {
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter("filename", phonePush.name).toString(),
            )
            call.response.header(HttpHeaders.ContentLength, phonePush.size.toString())
            val mime = ContentType.parse(phonePush.mime)
            call.respondBytesWriter(contentType = mime, status = HttpStatusCode.OK) {
                phonePush.input.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        writeFully(buf, 0, n)
                        stats.recordBytes(n.toLong())
                    }
                }
            }
            stats.incrementFileCount()
            return@get
        }

        val upload = File(store.fileDir(), id)
        if (!upload.exists()) { call.respond(HttpStatusCode.NotFound); return@get }
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter("filename", id).toString(),
        )
        call.response.header(HttpHeaders.ContentLength, upload.length().toString())
        call.respondBytesWriter(contentType = ContentType.Application.OctetStream, status = HttpStatusCode.OK) {
            upload.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    writeFully(buf, 0, n)
                }
            }
        }
    }
}
