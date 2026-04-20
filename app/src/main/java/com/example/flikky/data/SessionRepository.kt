package com.example.flikky.data

import com.example.flikky.data.db.MessageDao
import com.example.flikky.data.db.SessionDao
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.session.Message

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
}
