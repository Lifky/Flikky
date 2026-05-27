package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
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
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

interface FileStore {
    /** filesDir/sessions/$sessionId/files/, auto-created. */
    fun fileDir(sessionId: Long): File
}

fun Route.fileRoutes(
    session: SessionState,
    pinAuth: PinAuth,
    store: FileStore,
    stats: TransferStats,
    currentSessionId: () -> Long,
    onPersist: suspend (Message) -> Unit,
    broadcastEvent: suspend (type: String, payload: String) -> Unit,
    nowMs: () -> Long,
) {
    fun authed(call: ApplicationCall): Boolean {
        val token = call.request.cookies[AUTH_COOKIE]
        return token != null && pinAuth.validateToken(token)
    }

    post("/api/files") {
        if (!authed(call)) { call.respond(HttpStatusCode.Unauthorized); return@post }
        val sid = currentSessionId()
        val senderId = call.request.headers["X-Client-Id"]
        val declaredSize = call.request.headers["X-File-Size"]?.toLongOrNull() ?: 0L
        val multipart = call.receiveMultipart(formFieldLimit = Long.MAX_VALUE)
        var savedName: String? = null
        var savedSize: Long = 0L
        var savedMime: String = "application/octet-stream"
        val fileId = UUID.randomUUID().toString()
        val target = File(store.fileDir(sid), fileId)
        var msgId: Long = 0L
        var inProgressBroadcasted = false

        try {
            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    savedName = part.originalFileName ?: "unnamed"
                    savedMime = part.contentType?.toString() ?: savedMime
                    val totalSize = if (declaredSize > 0) declaredSize else 0L

                    msgId = IdGen.newMessageId()
                    val inProgressMsg = Message.File(
                        id = msgId,
                        origin = Origin.BROWSER,
                        timestamp = nowMs(),
                        fileId = fileId,
                        name = savedName ?: "unnamed",
                        sizeBytes = totalSize,
                        mime = savedMime,
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
                    inProgressBroadcasted = true

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
                    savedSize = totalCopied
                }
                part.dispose()
            }
        } catch (e: Exception) {
            // Upload interrupted (browser refresh, WiFi drop, etc.)
            if (inProgressBroadcasted && msgId > 0) {
                session.removeMessage(msgId)
                session.clearProgress(msgId)
                stats.decrementFileCount()
                target.delete()
                val removedDto = FileRemovedDto(msgId)
                broadcastEvent("file_removed",
                    Json.encodeToString(FileRemovedDto.serializer(), removedDto))
            }
            return@post
        }

        val completedMsg = Message.File(
            id = msgId,
            origin = Origin.BROWSER,
            timestamp = nowMs(),
            fileId = fileId,
            name = savedName ?: "unnamed",
            sizeBytes = savedSize,
            mime = savedMime,
            status = Message.File.Status.COMPLETED,
            senderId = senderId,
        )
        session.updateMessage(msgId) { completedMsg }
        session.clearProgress(msgId)
        runCatching { onPersist(completedMsg) }

        val readyDto = FileReadyDto(msgId, fileId, savedName ?: "unnamed", savedSize)
        broadcastEvent("file_ready",
            Json.encodeToString(FileReadyDto.serializer(), readyDto))

        val responseDto = FileMessageDto(
            completedMsg.id, completedMsg.origin.name, completedMsg.timestamp,
            completedMsg.fileId, completedMsg.name, completedMsg.sizeBytes,
            completedMsg.mime, completedMsg.status.name,
        )
        call.respond(responseDto)
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
        val originalName = session.snapshot.value.messages
            .filterIsInstance<com.example.flikky.session.Message.File>()
            .firstOrNull { it.fileId == id }?.name ?: id
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter("filename", originalName).toString(),
        )
        call.response.header(HttpHeaders.ContentLength, file.length().toString())
        call.respondBytesWriter(contentType = ContentType.Application.OctetStream, status = HttpStatusCode.OK) {
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
