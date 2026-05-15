package com.example.flikky.service

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.SessionRepository.RecallOutcome
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
    /**
     * Lambda 而不是直接持有 [WsHub]：rebind 时 TransferService 会换新 KtorServer +
     * 新 wsHub，但 controller 是 startTransfer 阶段一次性创建的；若闭包死引用旧 hub，
     * APP→browser 方向的广播会发到已废弃的 hub，浏览器永远收不到 APP 发的消息（直到
     * 浏览器手动刷新走 /api/messages 历史接口才看到）。每次 broadcast 时由 lambda
     * 取当前 hub，rebind 自动跟随。
     */
    private val wsHub: () -> WsHub?,
    private val nowMs: () -> Long,
    /**
     * Stable phone-side identity stamped onto every Message this controller
     * creates. v1.3 recall authorization compares this against the message's
     * senderId, so the same physical device can always recall its own
     * historical messages — even across service restarts. Set by
     * TransferService to `"phone-${Settings.Secure.ANDROID_ID}"` (D31).
     */
    private val senderId: String,
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
    }

    /**
     * v1.3 T11 手机端撤回入口。HistoryViewModel 调本方法（不直接调 repository），
     * 因为撤回成功后要触发 WS 广播让浏览器同步成 [消息已撤回] 占位。
     *
     * 鉴权交给 repository：[SessionRepository.recallMessage] 用消息上记录的 senderId
     * 比对本 controller 的 [senderId]，跨服务重启依然能撤回自己历史消息。
     *
     * 返回 [RecallOutcome] 让上层决定 UI 反馈：Success / AlreadyRecalled 静默更新即可；
     * NotFound / Denied 上层可以 Toast 或弹 dialog。
     *
     * 注意：内存 [SessionState.snapshot].messages 不在此处更新——历史列表通过
     * repository 的 observeMessages flow 重新拉取，DB 已写入 recalledAt 后 flow
     * 会自动收到下一帧。否则需要给 SessionState 加专门的 "mark recalled" API，
     * 与 v1.3 数据模型一并复杂化，无必要。
     */
    suspend fun recallMessage(messageId: Long): RecallOutcome {
        val outcome = repository.recallMessage(messageId, senderId)
        if (outcome is RecallOutcome.Success) {
            wsHub()?.broadcastRecall(outcome.sessionId, outcome.messageId)
        }
        return outcome
    }
}
