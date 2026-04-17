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
    broadcastEvent: suspend (type: String, jsonPayload: String) -> Unit,
    nowMs: () -> Long,
) {
    fun requireAuth(call: ApplicationCall): Boolean {
        val token = call.request.cookies[AUTH_COOKIE]
        return token != null && pinAuth.validateToken(token)
    }

    post("/api/messages") {
        if (!requireAuth(call)) { call.respond(HttpStatusCode.Unauthorized); return@post }
        val req = call.receive<SendTextRequest>()
        val msg = Message.Text(
            id = IdGen.newMessageId(),
            origin = Origin.BROWSER,
            timestamp = nowMs(),
            content = req.text,
        )
        session.addMessage(msg)
        val dto = TextMessageDto(msg.id, msg.origin.name, msg.timestamp, msg.content)
        broadcastEvent("text_added", kotlinx.serialization.json.Json.encodeToString(TextMessageDto.serializer(), dto))
        call.respond(dto)
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
        call.respond(MessagesResponse(texts, files))
    }
}
