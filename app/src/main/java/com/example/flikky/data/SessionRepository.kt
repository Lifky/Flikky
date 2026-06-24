package com.example.flikky.data

import com.example.flikky.data.db.MessageDao
import com.example.flikky.data.db.GroupDao
import com.example.flikky.data.db.SessionDao
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.ParsedMessage
import com.example.flikky.export.ParsedSession
import com.example.flikky.export.SessionExport
import com.example.flikky.export.ZipImporter
import com.example.flikky.export.wireNameToOrigin
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.util.IdGen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/**
 * 业务入口：所有 UI/Service 调 Repository，不碰 DAO。依赖 now 和 retainLimit 注入，便于测试。
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val fileStore: SessionFileStore,
    private val now: () -> Long,
    private val retainLimitProvider: suspend () -> Int = { 20 },
) {
    suspend fun beginSession(name: String, startedAt: Long, groupId: Long? = null): Long {
        return sessionDao.insert(SessionEntity(
            startedAt = startedAt,
            endedAt = null,
            name = name,
            groupId = groupId,
        ))
    }

    suspend fun appendMessage(sessionId: Long, msg: Message) {
        messageDao.insert(msg.toEntity(sessionId))
    }

    suspend fun endSession(sessionId: Long, endedAt: Long, peerAvatarId: Int = 0) {
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
            peerAvatarId = peerAvatarId,
        )
        sessionDao.update(updated)
    }

    /**
     * Retain at most `retainLimitProvider()` non-pinned finished sessions; drop the oldest ones
     * (with their file directories). Pinned and in-progress sessions are untouched.
     *
     * Semantics:
     * - `-1` = unlimited, never evict
     * - `0`  = keep zero non-pinned finished sessions (all evicted)
     * - `N>0` = keep newest N non-pinned finished sessions
     */
    suspend fun fifoSweep() {
        fifoSweep(retainLimitProvider())
    }

    /**
     * Variant that accepts a pre-read limit — used by [importSessions] which reads
     * the limit BEFORE entering [withContext][kotlinx.coroutines.withContext](Dispatchers.IO) to
     * avoid calling the DataStore-backed [retainLimitProvider] from inside an IO-dispatched
     * coroutine (DataStore 1.1 uses a SingleThreadExecutor for its internal actor; calling
     * `data.first()` from Dispatchers.IO can deadlock if that thread is the same executor thread).
     */
    private suspend fun fifoSweep(limit: Int) {
        if (limit < 0) return
        val oldestFirst = sessionDao.nonPinnedOldestFirst()
        val excess = oldestFirst.size - limit
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
        // B7 v1.4: clean up IN_PROGRESS file messages left by crashed transfers.
        // These represent files that were partially copied when the app died.
        cleanupInProgressMessages()
    }

    private suspend fun cleanupInProgressMessages() {
        val inProgressMessages = messageDao.listByStatus("IN_PROGRESS")
        for (msg in inProgressMessages) {
            msg.fileId?.let { fileStore.deleteMessageFile(msg.sessionId, it) }
            messageDao.deleteById(msg.id)
        }
        if (inProgressMessages.isNotEmpty()) {
            val affectedSessionIds = inProgressMessages.map { it.sessionId }.distinct()
            for (sid in affectedSessionIds) {
                val row = sessionDao.getById(sid) ?: continue
                val messages = messageDao.listBySession(sid)
                val files = messages.filter { it.kind == "FILE" }
                sessionDao.update(row.copy(
                    messageCount = messages.size,
                    fileCount = files.size,
                    totalBytes = files.sumOf { it.fileSize ?: 0L },
                ))
            }
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

    fun observeGroups(): Flow<List<GroupEntity>> = groupDao.observeAll()

    suspend fun createGroup(name: String): Long =
        groupDao.insert(
            GroupEntity(
                name = name.trim(),
                sortOrder = groupDao.maxSortOrder() + 1,
                createdAt = now(),
            )
        )

    suspend fun renameGroup(id: Long, name: String) {
        groupDao.getById(id)?.let { groupDao.update(it.copy(name = name.trim())) }
    }

    suspend fun deleteGroup(id: Long): Pair<GroupEntity, List<Long>>? {
        val group = groupDao.getById(id) ?: return null
        val members = groupDao.memberIds(id)
        sessionDao.unbindGroup(id)
        groupDao.deleteById(id)
        return group to members
    }

    suspend fun restoreGroup(group: GroupEntity, memberIds: List<Long>): Long {
        val newId = groupDao.insert(group.copy(id = 0))
        if (memberIds.isNotEmpty()) sessionDao.bindSessions(newId, memberIds)
        return newId
    }

    suspend fun reorderGroups(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id ->
            groupDao.getById(id)?.let { groupDao.update(it.copy(sortOrder = index)) }
        }
    }

    /** 批量把会话移动到 [groupId]（null = 移出分组，回到「全部」）。 */
    suspend fun moveSessionsToGroup(ids: List<Long>, groupId: Long?) {
        if (ids.isEmpty()) return
        sessionDao.setGroupForSessions(ids, groupId)
    }

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
     * recallMessage 的三种结果（v1.3 D26 修订）。Success 不再携带 recalledAt——
     * 撤回是真删（DELETE FROM messages），没有时间戳可保留；上层广播只需
     * (sessionId, messageId) 让两端从 UI 中移除对应节点。
     */
    sealed class RecallOutcome {
        data class Success(val messageId: Long, val sessionId: Long, val wasFile: Boolean) : RecallOutcome()
        object NotFound : RecallOutcome()
        object Denied : RecallOutcome()
    }

    /**
     * 撤回单条消息（v1.3 D26 修订：从"软删 + 占位符"改为"真删 + UI 完全消失"）。
     *
     * v1.5 修订：移除 senderId 鉴权。Flikky 单 PIN 单用户模型中，手机端 UI 只对
     * 自己发送的消息显示撤回按钮，浏览器端同理——服务端再校验 senderId 是冗余且会
     * 在 WiFi rebind（clientId 变化）或旧数据（senderId=null）场景下误拒绝合法请求。
     * `callerSenderId` 参数保留以免大改调用侧，但不再用于鉴权。
     *
     * 副作用：
     * - 真删 messages 行；messages_fts_ad 触发器自动清 FTS。
     * - 文件消息：同时删盘 + 更新 SessionEntity 聚合字段（fileCount-1, totalBytes -= fileSize）。
     *   删盘失败不阻塞撤回——逻辑上消息已撤回，磁盘残留交给 fifoSweep 兜底。
     *
     * 不再有 AlreadyRecalled 分支——真删后再次调用是 NotFound（行已不存在），上层
     * 把 NotFound 当作 idempotent 成功处理（消息已经"撤回"了，浏览器节点也已移除）。
     */
    suspend fun recallMessage(messageId: Long, callerSenderId: String): RecallOutcome {
        // callerSenderId intentionally unused for auth — UI-gated, single-PIN model.
        val msg = messageDao.getById(messageId) ?: return RecallOutcome.NotFound

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

        val wasFile = msg.kind == "FILE"
        messageDao.deleteById(messageId)
        return RecallOutcome.Success(messageId, msg.sessionId, wasFile)
    }

    /**
     * v1.3 History 单条消息删除入口。与撤回的区别：
     * - 不鉴权（History 是本地操作，不分谁发的）
     * - 不广播 WS（服务可能没在跑；即便在跑也不通知浏览器，纯本地清理）
     * - 文件消息同样删盘 + 更新聚合
     */
    suspend fun deleteMessage(messageId: Long) {
        val msg = messageDao.getById(messageId) ?: return
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
        messageDao.deleteById(messageId)
    }

    data class ImportResult(
        val imported: List<ImportedSession>,
        val skipped: List<SkippedSession>,
        val errors: List<ImportError>,
    )
    data class ImportedSession(val originalName: String, val newId: Long, val messageCount: Int, val fileCount: Int)
    data class SkippedSession(val name: String, val reason: String)
    data class ImportError(val name: String, val error: String)

    suspend fun importSessions(tempZipFile: File): ImportResult {
        // Read the retain limit BEFORE withContext(Dispatchers.IO). retainLimitProvider may
        // suspend on a DataStore flow (DataStore 1.1 uses a SingleThreadExecutor-backed actor
        // internally); calling data.first() from inside withContext(Dispatchers.IO) can
        // deadlock when that executor thread happens to be the same IO thread the coroutine
        // is pinned to.
        val retainLimit = retainLimitProvider()
        return withContext(Dispatchers.IO) { importSessionsInIo(tempZipFile, retainLimit) }
    }

    private suspend fun importSessionsInIo(tempZipFile: File, retainLimit: Int): ImportResult {
        val imported = mutableListOf<ImportedSession>()
        val skipped = mutableListOf<SkippedSession>()
        val errors = mutableListOf<ImportError>()

        val zipFile = try { ZipFile(tempZipFile) } catch (e: Exception) {
            errors.add(ImportError("zip", e.message ?: "Failed to open zip"))
            return ImportResult(imported, skipped, errors)
        }

        try {
            val parsedSessions = ZipImporter.parse(zipFile)

            for (parsed in parsedSessions) {
                try {
                    val existing = sessionDao.findByNameAndStartedAt(parsed.name, parsed.startedAt)
                    if (existing != null) {
                        skipped.add(SkippedSession(parsed.name, "已存在"))
                        continue
                    }

                    val newSessionId = sessionDao.insert(SessionEntity(
                        startedAt = parsed.startedAt,
                        endedAt = parsed.endedAt,
                        name = parsed.name,
                        pinned = parsed.pinned,
                    ))

                    val entities = mutableListOf<MessageEntity>()
                    val fileMessages = parsed.messages.filterIsInstance<ParsedMessage.File>()
                    var importedFileCount = 0
                    var importedTotalBytes = 0L

                    for (msg in parsed.messages) {
                        val newMessageId = IdGen.newMessageId()
                        when (msg) {
                            is ParsedMessage.Text -> entities.add(MessageEntity(
                                id = newMessageId,
                                sessionId = newSessionId,
                                origin = msg.origin,
                                timestamp = msg.ts,
                                kind = "TEXT",
                                content = msg.content,
                            ))
                            is ParsedMessage.File -> {
                                val newFileId = UUID.randomUUID().toString()
                                val entry = ZipImporter.resolveFileEntry(
                                    parsed.version, fileMessages, msg.fileId,
                                    parsed.sessionDir, zipFile,
                                )
                                if (entry != null) {
                                    val targetFile = File(fileStore.fileDir(newSessionId), newFileId)
                                    ZipImporter.getEntryStream(zipFile, entry).use { input ->
                                        targetFile.outputStream().use { out -> input.copyTo(out) }
                                    }
                                }
                                importedFileCount++
                                importedTotalBytes += msg.sizeBytes
                                entities.add(MessageEntity(
                                    id = newMessageId,
                                    sessionId = newSessionId,
                                    origin = msg.origin,
                                    timestamp = msg.ts,
                                    kind = "FILE",
                                    fileId = newFileId,
                                    fileName = msg.name,
                                    fileSize = msg.sizeBytes,
                                    fileMime = msg.mime,
                                    fileStatus = "COMPLETED",
                                ))
                            }
                        }
                    }

                    if (entities.isNotEmpty()) {
                        messageDao.insertAll(entities)
                    }

                    val previewText = parsed.messages
                        .filterIsInstance<ParsedMessage.Text>()
                        .firstOrNull()?.content?.take(40)

                    sessionDao.update(SessionEntity(
                        id = newSessionId,
                        startedAt = parsed.startedAt,
                        endedAt = parsed.endedAt,
                        name = parsed.name,
                        pinned = parsed.pinned,
                        messageCount = entities.size,
                        fileCount = importedFileCount,
                        totalBytes = importedTotalBytes,
                        previewText = previewText,
                    ))

                    imported.add(ImportedSession(
                        parsed.name, newSessionId, entities.size, importedFileCount,
                    ))
                } catch (e: Exception) {
                    errors.add(ImportError(parsed.name, e.message ?: "Unknown error"))
                }
            }

            fifoSweep(retainLimit)
        } finally {
            runCatching { zipFile.close() }
        }

        return ImportResult(imported, skipped, errors)
    }

    suspend fun updateFileStatus(messageId: Long, status: String, sizeBytes: Long) {
        messageDao.updateFileStatus(messageId, status, sizeBytes)
    }

    suspend fun deleteMessageAndFile(messageId: Long, sessionId: Long, fileId: String) {
        val msg = messageDao.getById(messageId)
        fileStore.deleteMessageFile(sessionId, fileId)
        messageDao.deleteById(messageId)
        if (msg != null) {
            val sessionRow = sessionDao.getById(sessionId) ?: return
            sessionDao.update(sessionRow.copy(
                fileCount = (sessionRow.fileCount - 1).coerceAtLeast(0),
                totalBytes = (sessionRow.totalBytes - (msg.fileSize ?: 0L)).coerceAtLeast(0L),
                messageCount = (sessionRow.messageCount - 1).coerceAtLeast(0),
            ))
        }
    }

    companion object {
        const val DEFAULT_NAME_FALLBACK = "会话"
    }
}
