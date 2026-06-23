package com.example.flikky.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.flikky.data.db.entities.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert suspend fun insert(group: GroupEntity): Long

    @Update suspend fun update(group: GroupEntity)

    @Query("DELETE FROM session_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM session_groups ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM session_groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM session_groups")
    suspend fun maxSortOrder(): Int

    @Query("SELECT id FROM sessions WHERE groupId = :gid")
    suspend fun memberIds(gid: Long): List<Long>
}
