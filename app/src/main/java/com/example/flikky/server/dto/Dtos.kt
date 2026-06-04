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
 * v1.3 D26 修订：DELETE /api/messages/{id} 的成功响应。撤回 = 真删，不再有
 * recalledAt 时间戳；客户端拿到 (sessionId, messageId) 即可移除节点。
 */
@Serializable
data class RecallResponse(
    val messageId: Long,
    val sessionId: Long,
)

/**
 * Server 包内部的撤回结果枚举。data/SessionRepository.RecallOutcome 在调用
 * 边界（KtorServer 注入 lambda 处）转成本枚举，避免 server 反向依赖 data。
 *
 * 真删后没有 AlreadyRecalled 分支——重复请求的消息行已不存在，等价 NotFound。
 * 上层把 NotFound 当 idempotent 成功处理（节点已经被移除了）。
 */
sealed class ServerRecallOutcome {
    data class Success(val messageId: Long, val sessionId: Long) : ServerRecallOutcome()
    object NotFound : ServerRecallOutcome()
    object Denied : ServerRecallOutcome()
}

@Serializable
data class FileProgressDto(
    val messageId: Long,
    val bytesTransferred: Long,
    val totalBytes: Long,
)

@Serializable
data class FileReadyDto(
    val messageId: Long,
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
)

@Serializable
data class FileRemovedDto(
    val messageId: Long,
)

@Serializable
data class StatusDto(
    val startedAt: Long,
    val uptime: Long,
    val fileCount: Int,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val clientConnected: Boolean,
)

/**
 * M9: GET /api/peer-info response. Phone's appearance settings sent to the browser.
 */
@Serializable
data class PeerInfoDto(
    val deviceName: String,
    val phoneAvatarId: Int,
    val backgroundMode: String,
    val backgroundValue: String? = null,
)

/**
 * M9: client_hello WS frame from browser, carrying the browser's chosen avatar ID.
 */
@Serializable
data class ClientHelloDto(val type: String, val avatarId: Int)
