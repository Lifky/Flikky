package com.example.flikky.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.flikky.data.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert suspend fun insert(message: MessageEntity)
    @Update suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY timestamp ASC")
    fun observeBySession(sid: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY timestamp ASC")
    suspend fun listBySession(sid: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sid")
    suspend fun countBySession(sid: Long): Int

    @Query("""
        SELECT content FROM messages
        WHERE sessionId = :sid AND kind = 'TEXT'
        ORDER BY timestamp ASC LIMIT 1
    """)
    suspend fun firstTextContent(sid: Long): String?
}
