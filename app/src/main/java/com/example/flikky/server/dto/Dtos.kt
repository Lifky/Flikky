package com.example.flikky.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val pin: String)

@Serializable
data class AuthResponse(
    val ok: Boolean,
    val error: String? = null,
    val retryAfterSec: Int? = null,
    /** Where the browser should navigate after a successful login. v1.2 differs by mode. */
    val redirectTo: String? = null,
)

@Serializable
data class SendTextRequest(val text: String)

@Serializable
data class TextMessageDto(
    val id: Long,
    val origin: String,
    val timestamp: Long,
    val content: String,
    /**
     * Echo of the X-Client-Id header from the upload request, so the same
     * browser session can skip its own broadcast in onWsEvent (avoids double
     * bubble). Null for historical messages and phone-originated events.
     */
    val senderId: String? = null,
)

@Serializable
data class FileMessageDto(
    val id: Long,
    val origin: String,
    val timestamp: Long,
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
    val mime: String,
    val status: String,
    /** See [TextMessageDto.senderId]. */
    val senderId: String? = null,
)

@Serializable
data class MessagesResponse(
    val texts: List<TextMessageDto>,
    val files: List<FileMessageDto>,
    /**
     * Unified, timestamp-sorted view of all messages so the client can render
     * them in the right order without inferring kind interleaving from two
     * separate lists. Each entry has either `content` (text) or `fileId`+`name`
     * (file); discriminated by which fields are present.
     */
    val ordered: List<MessageDto> = emptyList(),
)

@Serializable
data class MessageDto(
    val kind: String,   // "text" or "file"
    val id: Long,
    val origin: String,
    val timestamp: Long,
    val content: String? = null,
    val fileId: String? = null,
    val name: String? = null,
    val sizeBytes: Long? = null,
    val mime: String? = null,
    val status: String? = null,
)

@Serializable
data class WsEvent(
    val type: String,
    val payload: kotlinx.serialization.json.JsonElement,
)

/**
 * v1.3 D26：DELETE /api/messages/{id} 的成功响应。失败分支用通用 error JSON。
 * 单独类型让 KtorServer 注入的 recall handler 与 server 包解耦——
 * server 包不需要看到 data 层的 RecallOutcome。
 */
@Serializable
data class RecallResponse(
    val messageId: Long,
    val sessionId: Long,
    val recalledAt: Long,
)

/**
 * Server 包内部的撤回结果枚举。data/SessionRepository.RecallOutcome 在调用
 * 边界（KtorServer 注入 lambda 处）转成本枚举，避免 server 反向依赖 data。
 *
 * 注意：刻意不加 @Serializable——这只是用作 lambda 返回值，不需要序列化。
 * 加了反而要求子类必须可序列化，但 Success/AlreadyRecalled 已经走 RecallResponse 输出。
 */
sealed class ServerRecallOutcome {
    data class Success(val messageId: Long, val sessionId: Long, val recalledAt: Long) : ServerRecallOutcome()
    object NotFound : ServerRecallOutcome()
    object Denied : ServerRecallOutcome()
    data class AlreadyRecalled(val messageId: Long, val sessionId: Long, val recalledAt: Long) : ServerRecallOutcome()
}

@Serializable
data class StatusDto(
    val startedAt: Long,
    val uptime: Long,
    val fileCount: Int,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val clientConnected: Boolean,
)
