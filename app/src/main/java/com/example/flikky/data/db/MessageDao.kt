package com.example.flikky.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.flikky.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Projection used by full-text and LIKE-fallback search results. Each row
 * carries enough context to render a search hit (session name, timestamp,
 * kind/content/fileName) without extra fetches.
 */
data class MessageSearchResult(
    val messageId: Long,
    val sessionId: Long,
    val sessionName: String,
    val kind: String,
    val content: String?,
    val fileName: String?,
    val timestamp: Long,
)

@Dao
interface MessageDao {
    @Insert suspend fun insert(message: MessageEntity)
    @Update suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY timestamp ASC")
    fun observeBySession(sid: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY timestamp ASC")
    suspend fun listBySession(sid: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    /**
     * v1.3 撤回 / History 单条删除统一入口。真删行：messages_fts_ad 触发器
     * 自动清 FTS。recalledAt 字段在 v1.3 中段废弃（验收反馈：撤回应该是
     * "完全消失"而不是"标记占位符"，见 retrospective），保留 column 仅为
     * schema 向前兼容，无代码再写入。
     */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE messages SET fileStatus = :status, fileSize = :sizeBytes WHERE id = :id")
    suspend fun updateFileStatus(id: Long, status: String, sizeBytes: Long)

    @Query("DELETE FROM messages WHERE id = :id AND fileStatus = :status")
    suspend fun deleteByIdAndStatus(id: Long, status: String): Int

    @Query("SELECT * FROM messages WHERE fileStatus = :status")
    suspend fun listByStatus(status: String): List<MessageEntity>

    @Insert
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sid")
    suspend fun countBySession(sid: Long): Int

    @Query("""
        SELECT content FROM messages
        WHERE sessionId = :sid AND kind = 'TEXT'
        ORDER BY timestamp ASC LIMIT 1
    """)
    suspend fun firstTextContent(sid: Long): String?

    /**
     * Full-text search across non-recalled messages. Uses the `messages_fts`
     * FTS4 virtual table (see `FlikkyDatabase.MIGRATION_1_2`). The caller
     * is responsible for shaping `query` into valid FTS4 MATCH syntax
     * (e.g. appending `*` for prefix queries, quoting phrases).
     */
    @Query("""
        SELECT m.id as messageId, m.sessionId as sessionId, s.name as sessionName,
               m.kind as kind, m.content as content, m.fileName as fileName, m.timestamp as timestamp
          FROM messages m
          JOIN sessions s ON m.sessionId = s.id
         WHERE m.recalledAt IS NULL
           AND m.id IN (SELECT rowid FROM messages_fts WHERE messages_fts MATCH :query)
         ORDER BY m.timestamp DESC LIMIT 200
    """)
    suspend fun searchMessagesFts(query: String): List<MessageSearchResult>

    /**
     * LIKE-based fallback for cases where FTS tokenization wouldn't match
     * (e.g. arbitrary mid-token substrings in CJK with unicode61). `pattern`
     * must already include any wildcards (e.g. `"%foo%"`).
     */
    @Query("""
        SELECT m.id as messageId, m.sessionId as sessionId, s.name as sessionName,
               m.kind as kind, m.content as content, m.fileName as fileName, m.timestamp as timestamp
          FROM messages m
          JOIN sessions s ON m.sessionId = s.id
         WHERE m.recalledAt IS NULL
           AND (m.content LIKE :pattern OR m.fileName LIKE :pattern)
         ORDER BY m.timestamp DESC LIMIT 200
    """)
    suspend fun searchMessagesLike(pattern: String): List<MessageSearchResult>
}
