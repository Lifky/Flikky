package com.example.flikky.data

import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.session.Message
import com.example.flikky.session.Origin

// v1.3 D26 修订：撤回 = 真删（DELETE FROM messages），不再写 recalledAt。
// MessageEntity 仍有 recalledAt 列以保 schema 向前兼容（Migration 1→2 加的列），
// 但 Mapper 不读不写——Message 模型本身已去掉该字段。

internal fun Message.toEntity(sessionId: Long): MessageEntity = when (this) {
    is Message.Text -> MessageEntity(
        id = id, sessionId = sessionId, origin = origin.name, timestamp = timestamp,
        kind = "TEXT", content = content,
        senderId = senderId,
    )
    is Message.File -> MessageEntity(
        id = id, sessionId = sessionId, origin = origin.name, timestamp = timestamp,
        kind = "FILE",
        fileId = fileId, fileName = name, fileSize = sizeBytes,
        fileMime = mime, fileStatus = status.name,
        senderId = senderId,
    )
}

internal fun MessageEntity.toMessage(): Message = when (kind) {
    "TEXT" -> Message.Text(
        id = id, origin = Origin.valueOf(origin), timestamp = timestamp,
        content = content ?: "",
        senderId = senderId,
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
    )
    else -> error("Unknown message kind: $kind")
}
