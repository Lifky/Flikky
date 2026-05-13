package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.FileMessageDto
import com.example.flikky.server.dto.MessagesResponse
import com.example.flikky.server.dto.SendTextRequest
import com.example.flikky.server.dto.TextMessageDto
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import com.example.flikky.util.IdGen
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.messageRoutes(
    session: SessionState,
    pinAuth: PinAuth,
    onPersist: suspend (Message) -> Unit,
    broadcastEvent: suspend (type: String, jsonPayload: String) -> Unit,
    nowMs: () -> Long,
) {
    fun requireAuth(call: ApplicationCall): Boolean {
        val token = call.request.cookies[AUTH_COOKIE]
        return token != null && pinAuth.validateToken(token)
    }

    post("/api/messages") {
        if (!requireAuth(call)) { call.respond(HttpStatusCode.Unauthorized); return@post }
        // 同 FileRoutes：senderId 透传到广播 payload 给前端 dedup 用。
        val senderId = call.request.headers["X-Client-Id"]
        val req = call.receive<SendTextRequest>()
        val msg = Message.Text(
            id = IdGen.newMessageId(),
            origin = Origin.BROWSER,
            timestamp = nowMs(),
            content = req.text,
        )
        session.addMessage(msg)
        runCatching { onPersist(msg) }
        val dto = TextMessageDto(msg.id, msg.origin.name, msg.timestamp, msg.content, senderId = senderId)
        broadcastEvent("text_added", kotlinx.serialization.json.Json.encodeToString(TextMessageDto.serializer(), dto))
        call.respond(dto.copy(senderId = null))
    }

    get("/api/messages") {
        if (!requireAuth(call)) { call.respond(HttpStatusCode.Unauthorized); return@get }
        val snap = session.snapshot.value
        val texts = snap.messages.filterIsInstance<Message.Text>().map {
            TextMessageDto(it.id, it.origin.name, it.timestamp, it.content)
        }
        val files = snap.messages.filterIsInstance<Message.File>().map {
            FileMessageDto(it.id, it.origin.name, it.timestamp, it.fileId, it.name, it.sizeBytes, it.mime, it.status.name)
        }
        // Unified, ts-sorted view so the frontend renders chronologically on
        // reload — the old (texts, files) shape forced clients to interleave
        // by kind, which surfaces files after texts regardless of when they
        // were actually sent.
        val ordered = snap.messages.sortedBy { it.timestamp }.map { m ->
            when (m) {
                is Message.Text -> com.example.flikky.server.dto.MessageDto(
                    kind = "text",
                    id = m.id,
                    origin = m.origin.name,
                    timestamp = m.timestamp,
                    content = m.content,
                )
                is Message.File -> com.example.flikky.server.dto.MessageDto(
                    kind = "file",
                    id = m.id,
                    origin = m.origin.name,
                    timestamp = m.timestamp,
                    fileId = m.fileId,
                    name = m.name,
                    sizeBytes = m.sizeBytes,
                    mime = m.mime,
                    status = m.status.name,
                )
            }
        }
        call.respond(MessagesResponse(texts, files, ordered))
    }
}
