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

    companion object {
        const val DEFAULT_NAME_FALLBACK = "会话"
    }
}
