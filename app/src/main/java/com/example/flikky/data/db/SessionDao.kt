package com.example.flikky.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.flikky.data.db.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: SessionEntity): Long

    @Update suspend fun update(session: SessionEntity)

    @Delete suspend fun delete(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY pinned DESC, startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionEntity?>

    @Query("""
        SELECT * FROM sessions
        WHERE pinned = 0 AND endedAt IS NOT NULL
        ORDER BY startedAt ASC
    """)
    suspend fun nonPinnedOldestFirst(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL")
    suspend fun listUnfinished(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    suspend fun listFinished(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE name = :name AND startedAt = :startedAt LIMIT 1")
    suspend fun findByNameAndStartedAt(name: String, startedAt: Long): SessionEntity?

    @Query("UPDATE sessions SET groupId = NULL WHERE groupId = :gid")
    suspend fun unbindGroup(gid: Long)

    @Query("UPDATE sessions SET groupId = :gid WHERE id IN (:ids)")
    suspend fun bindSessions(gid: Long, ids: List<Long>)

    /** 批量把会话改到某分组；gid 为 null 表示移出分组（回到「全部」）。 */
    @Query("UPDATE sessions SET groupId = :gid WHERE id IN (:ids)")
    suspend fun setGroupForSessions(ids: List<Long>, gid: Long?)
}
