package com.example.flikky.data

import com.example.flikky.data.db.MessageDao
import com.example.flikky.data.db.SessionDao
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.SessionExport
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 业务入口：所有 UI/Service 调 Repository，不碰 DAO。依赖 now 和 retainLimit 注入，便于测试。
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val fileStore: SessionFileStore,
    private val now: () -> Long,
    private val retainLimit: Int = 20,
) {
    suspend fun beginSession(name: String, startedAt: Long): Long {
        return sessionDao.insert(SessionEntity(
            startedAt = startedAt, endedAt = null, name = name,
        ))
    }

    suspend fun appendMessage(sessionId: Long, msg: Message) {
        messageDao.insert(msg.toEntity(sessionId))
    }

    suspend fun endSession(sessionId: Long, endedAt: Long) {
        val row = sessionDao.getById(sessionId) ?: return
        val messages = messageDao.listBySession(sessionId)
        if (messages.isEmpty()) {
            sessionDao.delete(row)
            fileStore.deleteSessionDir(sessionId)
            return
        }
        val files = messages.filter { it.kind == "FILE" }
        val updated = row.copy(
            endedAt = endedAt,
            messageCount = messages.size,
            fileCount = files.size,
            totalBytes = files.sumOf { it.fileSize ?: 0L },
            previewText = messageDao.firstTextContent(sessionId)?.take(40),
        )
        sessionDao.update(updated)
    }

    /**
     * Retain at most `retainLimit` non-pinned finished sessions; drop the oldest ones
     * (with their file directories). Pinned and in-progress sessions are untouched.
     */
    suspend fun fifoSweep() {
        val oldestFirst = sessionDao.nonPinnedOldestFirst()
        val excess = oldestFirst.size - retainLimit
        if (excess <= 0) return
        oldestFirst.take(excess).forEach { s ->
            sessionDao.delete(s)
            fileStore.deleteSessionDir(s.id)
        }
    }

    /**
     * Called at app start. Two tasks:
     *  - close any `endedAt IS NULL` sessions: if they have messages, compute endedAt
     *    and stats; if zero messages, roll back as empty
     *  - delete filesystem session dirs not present in DB (stray dirs)
     */
    suspend fun finalizeOrphans() {
        sessionDao.listUnfinished().forEach { row ->
            val messages = messageDao.listBySession(row.id)
            if (messages.isEmpty()) {
                sessionDao.delete(row)
                fileStore.deleteSessionDir(row.id)
            } else {
                val files = messages.filter { it.kind == "FILE" }
                val endedAt = maxOf(row.startedAt, messages.maxOf { it.timestamp })
                sessionDao.update(row.copy(
                    endedAt = endedAt,
                    messageCount = messages.size,
                    fileCount = files.size,
                    totalBytes = files.sumOf { it.fileSize ?: 0L },
                    previewText = messageDao.firstTextContent(row.id)?.take(40),
                ))
            }
        }
        fileStore.listSessionDirs().forEach { sid ->
            if (sessionDao.getById(sid) == null) fileStore.deleteSessionDir(sid)
        }
    }

    suspend fun rename(sessionId: Long, newName: String) {
        val row = sessionDao.getById(sessionId) ?: return
        val trimmed = newName.trim().ifEmpty { DEFAULT_NAME_FALLBACK }.take(40)
        sessionDao.update(row.copy(name = trimmed))
    }

    suspend fun setPinned(sessionId: Long, pinned: Boolean) {
        val row = sessionDao.getById(sessionId) ?: return
        sessionDao.update(row.copy(pinned = pinned))
    }

    suspend fun deleteSession(sessionId: Long) {
        val row = sessionDao.getById(sessionId) ?: return
        sessionDao.delete(row)
        fileStore.deleteSessionDir(sessionId)
    }

    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()

    fun observeSession(sessionId: Long): Flow<SessionEntity?> = sessionDao.observeById(sessionId)

    fun observeMessages(sessionId: Long): Flow<List<Message>> =
        messageDao.observeBySession(sessionId)
            .map { list -> list.map { it.toMessage() } }

    /**
     * 组装一批会话的导出快照。
     *
     * 过滤规则：
     * - `endedAt IS NULL`（进行中会话）跳过，不写入 snapshot。UI 层应已灰掉此类选项，
     *   repo 层再兜底一次，避免误导出不完整会话。
     * - 不存在的 id 静默忽略。
     *
     * 不读文件二进制内容——ZipExporter 运行时通过 fileResolver lambda 再按需读。
     * snapshot.exportedAt = now()。
     */
    suspend fun exportSnapshot(ids: List<Long>): ExportSnapshot {
        val sessions = mutableListOf<SessionExport>()
        for (id in ids) {
            val row = sessionDao.getById(id) ?: continue
            if (row.endedAt == null) continue
            val messages = messageDao.listBySession(row.id)
            sessions.add(row.toSessionExport(messages))
        }
        return ExportSnapshot(sessions = sessions, exportedAt = now())
    }

    private fun SessionEntity.toSessionExport(messages: List<MessageEntity>): SessionExport =
        SessionExport(
            id = id,
            name = name,
            startedAt = startedAt,
            endedAt = endedAt,
            pinned = pinned,
            messages = messages.map { it.toMessageExport() },
        )

    private fun MessageEntity.toMessageExport(): MessageExport = when (kind) {
        "TEXT" -> MessageExport.Text(
            ts = timestamp,
            origin = Origin.valueOf(origin),
            content = content ?: "",
        )
        "FILE" -> MessageExport.File(
            ts = timestamp,
            origin = Origin.valueOf(origin),
            fileId = fileId ?: error("FILE message missing fileId"),
            name = fileName ?: "",
            mime = fileMime ?: "application/octet-stream",
            sizeBytes = fileSize ?: 0L,
        )
        else -> error("Unknown message kind: $kind")
    }

    /**
     * 跨会话消息搜索结果（v1.3 D24）。`snippet` 已根据 kind 提取：
     * TEXT 取 `content.take(80)`，FILE 取 `fileName`（缺失则空串）。
     */
    data class SearchHit(
        val sessionId: Long,
        val sessionName: String,
        val messageId: Long,
        val snippet: String,
        val timestamp: Long,
        val kind: String,
    )

    /**
     * 跨会话消息搜索。空 query → 空列表。
     *
     * 分支策略（D24，v1.3 装机修订）：
     * - 纯 ASCII 且长度 >= 5 → 走 FTS4 MATCH
     * - 含非 ASCII（CJK 等）或长度 < 5 → 走 LIKE %query% 兜底
     *
     * 修订理由：Android SQLite 不带 ICU，unicode61 tokenizer 无法识别 CJK 字符
     * （Unicode 'Lo' 类别），中文输入走 FTS 永远拿不到结果。任何包含非 ASCII 字符
     * 的 query 直接走 LIKE。装机崩溃确认 `categories='L* N* Co'` 参数 Android
     * SQLite 不接受，所以不能靠 tokenizer 解决 CJK 分词。
     *
     * 撤回过滤：由 DAO 的 `WHERE m.recalledAt IS NULL` 保证，调用方无需感知。
     * 排序：由 DAO 的 `ORDER BY m.timestamp DESC LIMIT 200` 保证，新消息优先。
     *
     * TODO(v1.4): 当前 LIKE 分支不转义 `%` `_` `\`，用户输入这些字符会被当作通配。
     */
    suspend fun search(query: String): List<SearchHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val pureAsciiLong = trimmed.length >= 5 && trimmed.all { it.code < 128 }
        val rows: List<com.example.flikky.data.db.MessageSearchResult> =
            if (pureAsciiLong) {
                messageDao.searchMessagesFts(trimmed)
            } else {
                messageDao.searchMessagesLike("%$trimmed%")
            }

        return rows.map { r ->
            SearchHit(
                sessionId = r.sessionId,
                sessionName = r.sessionName,
                messageId = r.messageId,
                snippet = when (r.kind) {
                    "TEXT" -> r.content?.take(80) ?: ""
                    "FILE" -> r.fileName ?: ""
                    else -> ""
                },
                timestamp = r.timestamp,
                kind = r.kind,
            )
        }
    }

    /**
     * recallMessage 的四种结果（D26）。Success / AlreadyRecalled 都携带 recalledAt
     * 以便上层广播一致的撤回事件；NotFound / Denied 是错误分支。
     */
    sealed class RecallOutcome {
        data class Success(
            val messageId: Long,
            val sessionId: Long,
            val recalledAt: Long,
        ) : RecallOutcome()
        object NotFound : RecallOutcome()
        object Denied : RecallOutcome()
        data class AlreadyRecalled(
            val messageId: Long,
            val sessionId: Long,
            val recalledAt: Long,
        ) : RecallOutcome()
    }

    /**
     * 撤回单条消息（v1.3 D26）。
     *
     * 鉴权：调用方传入的 `callerSenderId` 必须严格等于消息 `senderId`。
     * `senderId` 为 null 的旧消息（pre-v1.3 数据）无法撤回 → Denied，不能让任何
     * 客户端撤回身份不明的历史消息。
     *
     * 副作用：
     * - 文本消息：仅写 `messages.recalledAt`，FTS 行由触发器 `messages_fts_au`
     *   自动 DELETE（new.recalledAt 非 null 时不会重新插入）。
     * - 文件消息：同时删盘 + 更新 SessionEntity 聚合字段（fileCount-1, totalBytes -= fileSize）。
     *   删盘失败不阻塞撤回——逻辑上消息已撤回，磁盘残留交给后续清理。
     *
     * 幂等：已撤回的消息再次调用返回 AlreadyRecalled（带原 recalledAt），不写库。
     */
    suspend fun recallMessage(messageId: Long, callerSenderId: String): RecallOutcome {
        val msg = messageDao.getById(messageId) ?: return RecallOutcome.NotFound
        msg.recalledAt?.let {
            return RecallOutcome.AlreadyRecalled(msg.id, msg.sessionId, it)
        }
        if (msg.senderId == null || msg.senderId != callerSenderId) {
            return RecallOutcome.Denied
        }

        val recalledAt = now()
        messageDao.markRecalled(messageId, recalledAt)

        if (msg.kind == "FILE") {
            val sessionRow = sessionDao.getById(msg.sessionId)
            msg.fileId?.let { fileStore.deleteMessageFile(msg.sessionId, it) }
            if (sessionRow != null) {
                val newFileCount = (sessionRow.fileCount - 1).coerceAtLeast(0)
                val newTotalBytes = (sessionRow.totalBytes - (msg.fileSize ?: 0L)).coerceAtLeast(0L)
                sessionDao.update(
                    sessionRow.copy(
                        fileCount = newFileCount,
                        totalBytes = newTotalBytes,
                    )
                )
            }
        }

        return RecallOutcome.Success(messageId, msg.sessionId, recalledAt)
    }

    companion object {
        const val DEFAULT_NAME_FALLBACK = "会话"
    }
}
