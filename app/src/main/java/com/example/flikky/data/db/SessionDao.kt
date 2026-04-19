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
}
