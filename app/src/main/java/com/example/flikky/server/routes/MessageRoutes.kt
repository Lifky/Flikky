package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.FileMessageDto
import com.example.flikky.server.dto.MessagesResponse
import com.example.flikky.server.dto.RecallResponse
import com.example.flikky.server.dto.SendTextRequest
import com.example.flikky.server.dto.ServerRecallOutcome
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
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.messageRoutes(
    session: SessionState,
    pinAuth: PinAuth,
    onPersist: suspend (Message) -> Unit,
    broadcastEvent: suspend (type: String, jsonPayload: String) -> Unit,
    nowMs: () -> Long,
    /**
     * v1.3 D26：撤回处理器。返回 [ServerRecallOutcome] 是 server-local 类型，
     * 调用方（KtorServer / TransferService）负责把 data 层的 RecallOutcome 转过来，
     * server 包因此不依赖 data 包。
     */
    recallHandler: suspend (messageId: Long, callerSenderId: String) -> ServerRecallOutcome,
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
            senderId = senderId,
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

    /**
     * v1.3 D26 修订：浏览器端撤回消息。X-Client-Id 必填——既用于鉴权（必须等于
     * 消息原始 senderId），也用于让本浏览器自己 dedup 后续 WS 广播。
     *
     * 撤回 = 真删（不再有"撤回标记"）。成功后广播 message_recalled，让所有客户端
     * 把对应消息节点完全移除 + snackbar 提醒。失败分支不广播。
     *
     * 不再有 409 / AlreadyRecalled —— 真删后重复请求得到 NotFound，浏览器把
     * NotFound 当 idempotent 成功处理（节点已经移除了）。
     */
    delete("/api/messages/{id}") {
        if (!requireAuth(call)) { call.respond(HttpStatusCode.Unauthorized); return@delete }
        val id = call.parameters["id"]?.toLongOrNull()
            ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_id")); return@delete }
        val senderId = call.request.headers["X-Client-Id"]
            ?: run { call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing_client_id")); return@delete }

        when (val outcome = recallHandler(id, senderId)) {
            is ServerRecallOutcome.Success -> {
                val resp = RecallResponse(outcome.messageId, outcome.sessionId)
                call.respond(HttpStatusCode.OK, resp)
                broadcastEvent(
                    "message_recalled",
                    kotlinx.serialization.json.Json.encodeToString(RecallResponse.serializer(), resp),
                )
            }
            is ServerRecallOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "not_found"))
            is ServerRecallOutcome.Denied -> call.respond(HttpStatusCode.Forbidden, mapOf("error" to "denied"))
        }
    }
}
