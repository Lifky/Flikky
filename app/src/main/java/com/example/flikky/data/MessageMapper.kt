package com.example.flikky.data

import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.session.Message
import com.example.flikky.session.Origin

internal fun Message.toEntity(sessionId: Long): MessageEntity = when (this) {
    is Message.Text -> MessageEntity(
        id = id, sessionId = sessionId, origin = origin.name, timestamp = timestamp,
        kind = "TEXT", content = content,
        senderId = senderId,
        recalledAt = recalledAt,
    )
    is Message.File -> MessageEntity(
        id = id, sessionId = sessionId, origin = origin.name, timestamp = timestamp,
        kind = "FILE",
        fileId = fileId, fileName = name, fileSize = sizeBytes,
        fileMime = mime, fileStatus = status.name,
        senderId = senderId,
        recalledAt = recalledAt,
    )
}

internal fun MessageEntity.toMessage(): Message = when (kind) {
    "TEXT" -> Message.Text(
        id = id, origin = Origin.valueOf(origin), timestamp = timestamp,
        content = content ?: "",
        senderId = senderId,
        recalledAt = recalledAt,
    )
    "FILE" -> Message.File(
        id = id, origin = Origin.valueOf(origin), timestamp = timestamp,
        fileId = fileId ?: error("FILE message missing fileId"),
        name = fileName ?: "",
        sizeBytes = fileSize ?: 0L,
        mime = fileMime ?: "application/octet-stream",
        status = fileStatus?.let { Message.File.Status.valueOf(it) }
            ?: Message.File.Status.COMPLETED,
        senderId = senderId,
        recalledAt = recalledAt,
    )
    else -> error("Unknown message kind: $kind")
}
