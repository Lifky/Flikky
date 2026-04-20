package com.example.flikky.service

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.server.dto.FileMessageDto
import com.example.flikky.server.dto.TextMessageDto
import com.example.flikky.server.routes.WsHub
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import com.example.flikky.util.IdGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID

class TransferController(
    private val session: SessionState,
    private val stats: TransferStats,
    private val fileStore: SessionFileStore,
    private val repository: SessionRepository,
    private val wsHub: WsHub,
    private val nowMs: () -> Long,
) {
    suspend fun sendText(text: String) {
        if (text.isBlank()) return
        val sid = session.snapshot.value.currentSessionId ?: return
        val msg = Message.Text(
            id = IdGen.newMessageId(),
            origin = Origin.PHONE,
            timestamp = nowMs(),
            content = text,
        )
        session.addMessage(msg)
        runCatching { repository.appendMessage(sid, msg) }
        val dto = TextMessageDto(msg.id, msg.origin.name, msg.timestamp, msg.content)
        wsHub.broadcast("text_added", Json.encodeToString(TextMessageDto.serializer(), dto))
    }

    suspend fun offerFile(uri: Uri, resolver: ContentResolver) {
        val sid = session.snapshot.value.currentSessionId ?: return
        var name = "unnamed"; var size = -1L
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (ni >= 0) name = c.getString(ni) ?: name
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (si >= 0) size = c.getLong(si)
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val fileId = UUID.randomUUID().toString()

        val archived = runCatching {
            withContext(Dispatchers.IO) {
                val input = resolver.openInputStream(uri) ?: error("cannot open uri")
                fileStore.archiveFromStream(sid, fileId, input)
            }
        }
        val target = archived.getOrNull() ?: return
        val realSize = if (size > 0) size else target.length()

        val msg = Message.File(
            id = IdGen.newMessageId(),
            origin = Origin.PHONE,
            timestamp = nowMs(),
            fileId = fileId,
            name = name,
            sizeBytes = realSize,
            mime = mime,
            status = Message.File.Status.COMPLETED,
        )
        session.addMessage(msg)
        stats.incrementFileCount()
        runCatching { repository.appendMessage(sid, msg) }

        val dto = FileMessageDto(
            msg.id, msg.origin.name, msg.timestamp, msg.fileId,
            msg.name, msg.sizeBytes, msg.mime, msg.status.name,
        )
        wsHub.broadcast("file_added", Json.encodeToString(FileMessageDto.serializer(), dto))
    }
}
