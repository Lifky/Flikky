package com.example.flikky.service

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.SessionRepository.RecallOutcome
import com.example.flikky.server.dto.FileMessageDto
import com.example.flikky.server.dto.FileProgressDto
import com.example.flikky.server.dto.FileReadyDto
import com.example.flikky.server.dto.FileRemovedDto
import com.example.flikky.server.dto.PeerAvatarChangedDto
import com.example.flikky.server.dto.TextMessageDto
import com.example.flikky.server.routes.WsHub
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import com.example.flikky.util.IdGen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Service-side entry point for phone-originated send / recall actions.
 *
 * UI layer (HomeViewModel / HistoryViewModel) calls this controller instead of
 * touching [SessionRepository] directly — that way the controller also handles
 * the WS broadcast side effect, so the browser sees phone-side changes
 * immediately without waiting for a polling refresh.
 *
 *  - [sendText] / [offerFile] : phone → browser content
 *  - [recallMessage]          : phone-side recall entry (v1.3 T11)
 */
class TransferController(
    private val session: SessionState,
    private val stats: TransferStats,
    private val fileStore: SessionFileStore,
    private val repository: SessionRepository,
    private val wsHub: () -> WsHub?,
    private val nowMs: () -> Long,
    private val senderId: String,
    private val scope: CoroutineScope,
) {
    suspend fun sendText(text: String) {
        if (text.isBlank()) return
        val sid = session.snapshot.value.currentSessionId ?: return
        val msg = Message.Text(
            id = IdGen.newMessageId(),
            origin = Origin.PHONE,
            timestamp = nowMs(),
            content = text,
            senderId = senderId,
        )
        session.addMessage(msg)
        runCatching { repository.appendMessage(sid, msg) }
        val dto = TextMessageDto(msg.id, msg.origin.name, msg.timestamp, msg.content)
        wsHub()?.broadcast("text_added", Json.encodeToString(TextMessageDto.serializer(), dto))
    }

    suspend fun offerFile(uri: Uri, resolver: ContentResolver) {
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
        offerFilePayload(
            name = name,
            size = size,
            mime = mime,
            input = { resolver.openInputStream(uri) ?: error("cannot open uri") },
        )
    }

    suspend fun offerStoredFile(source: File, name: String, size: Long, mime: String): Boolean {
        if (!source.exists() || !source.isFile) return false
        return offerFilePayload(
            name = name.ifBlank { source.name.ifBlank { "unnamed" } },
            size = if (size > 0) size else source.length(),
            mime = mime.ifBlank { "application/octet-stream" },
            input = { source.inputStream() },
        )
    }

    private suspend fun offerFilePayload(
        name: String,
        size: Long,
        mime: String,
        input: () -> InputStream,
    ): Boolean {
        val sid = session.snapshot.value.currentSessionId ?: return false
        val fileId = UUID.randomUUID().toString()
        val knownSize = if (size > 0) size else 0L

        val msg = Message.File(
            id = IdGen.newMessageId(),
            origin = Origin.PHONE,
            timestamp = nowMs(),
            fileId = fileId,
            name = name,
            sizeBytes = knownSize,
            mime = mime,
            status = Message.File.Status.IN_PROGRESS,
            senderId = senderId,
        )
        session.addMessage(msg)
        stats.incrementFileCount()
        runCatching { repository.appendMessage(sid, msg) }

        val dto = FileMessageDto(
            msg.id, msg.origin.name, msg.timestamp, msg.fileId,
            msg.name, msg.sizeBytes, msg.mime, msg.status.name,
        )
        wsHub()?.broadcast("file_added", Json.encodeToString(FileMessageDto.serializer(), dto))

        scope.launch(Dispatchers.IO) {
            try {
                val target = File(fileStore.fileDir(sid), fileId)
                var totalCopied = 0L
                val totalSize = knownSize
                var lastReportedPct = -1

                input().use { ins ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            totalCopied += n
                            stats.recordBytes(n.toLong())

                            if (totalSize > 0) {
                                val pct = ((totalCopied * 100) / totalSize).toInt()
                                if (pct >= lastReportedPct + 5) {
                                    lastReportedPct = pct
                                    val ratio = totalCopied.toFloat() / totalSize
                                    session.updateProgress(msg.id, ratio)
                                    val progressDto = FileProgressDto(msg.id, totalCopied, totalSize)
                                    wsHub()?.broadcast("file_progress",
                                        Json.encodeToString(FileProgressDto.serializer(), progressDto))
                                }
                            }
                        }
                    }
                }

                val realSize = if (totalSize > 0) totalSize else target.length()
                session.updateMessage(msg.id) { m ->
                    (m as Message.File).copy(status = Message.File.Status.COMPLETED, sizeBytes = realSize)
                }
                runCatching { repository.updateFileStatus(msg.id, "COMPLETED", realSize) }

                val readyDto = FileReadyDto(msg.id, fileId, name, realSize)
                wsHub()?.broadcast("file_ready",
                    Json.encodeToString(FileReadyDto.serializer(), readyDto))
                session.clearProgress(msg.id)
            } catch (e: Exception) {
                session.updateMessage(msg.id) { m ->
                    (m as Message.File).copy(status = Message.File.Status.FAILED)
                }
                session.clearProgress(msg.id)
                runCatching { repository.deleteMessageAndFile(msg.id, sid, fileId) }
                stats.decrementFileCount()
                val removedDto = FileRemovedDto(msg.id)
                runCatching {
                    wsHub()?.broadcast("file_removed",
                        Json.encodeToString(FileRemovedDto.serializer(), removedDto))
                }
            }
        }
        return true
    }

    /**
     * v1.3 撤回入口（D26 修订：从"软删 + 占位符"改为"真删 + 节点消失"）。
     *
     * 流程：
     * 1) repository.recallMessage 真删 DB 行（鉴权 senderId 匹配）+ 文件删盘
     * 2) session.removeMessage 同步 ServingScreen 的 messages flow
     * 3) wsHub 广播 message_recalled，让浏览器收到后移除节点 + snackbar 提醒
     *
     * 失败分支（NotFound / Denied）不广播、不动 session；上层根据 outcome
     * 决定提示文案。
     */
    suspend fun recallMessage(messageId: Long): RecallOutcome {
        val outcome = repository.recallMessage(messageId, senderId)
        if (outcome is RecallOutcome.Success) {
            session.removeMessage(outcome.messageId)
            if (outcome.wasFile) stats.decrementFileCount()
            wsHub()?.broadcastRecall(outcome.sessionId, outcome.messageId)
        }
        return outcome
    }

    suspend fun setPeerAvatarKey(key: String) {
        session.setPeerAvatarKey(key)
        val dto = PeerAvatarChangedDto(key)
        wsHub()?.broadcast("peer_avatar_changed", Json.encodeToString(PeerAvatarChangedDto.serializer(), dto))
    }
}
