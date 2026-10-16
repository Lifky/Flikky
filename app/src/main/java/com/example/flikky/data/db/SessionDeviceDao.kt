package com.example.flikky.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.flikky.data.db.entities.SessionDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDeviceDao {
    @Upsert
    suspend fun upsert(row: SessionDeviceEntity)

    @Upsert
    suspend fun upsertAll(rows: List<SessionDeviceEntity>)

    @Query("SELECT * FROM session_devices WHERE sessionId = :sessionId ORDER BY firstSeenAt")
    suspend fun listBySession(sessionId: Long): List<SessionDeviceEntity>

    @Query("SELECT * FROM session_devices WHERE sessionId = :sessionId ORDER BY firstSeenAt")
    fun observeBySession(sessionId: Long): Flow<List<SessionDeviceEntity>>
}
