package com.example.flikky.server.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val pin: String)

@Serializable
data class AuthResponse(val ok: Boolean, val error: String? = null, val retryAfterSec: Int? = null)

@Serializable
data class SendTextRequest(val text: String)

@Serializable
data class TextMessageDto(
    val id: Long,
    val origin: String,
    val timestamp: Long,
    val content: String,
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
)

@Serializable
data class MessagesResponse(
    val texts: List<TextMessageDto>,
    val files: List<FileMessageDto>,
)

@Serializable
data class WsEvent(
    val type: String,
    val payload: kotlinx.serialization.json.JsonElement,
)

@Serializable
data class StatusDto(
    val startedAt: Long,
    val fileCount: Int,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val clientConnected: Boolean,
)
