package com.example.flikky.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.flikky.data.db.entities.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert suspend fun insert(favorite: FavoriteEntity): Long

    @Update suspend fun update(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: Long): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE sourceSessionId = :sid AND sourceMessageId = :mid LIMIT 1")
    suspend fun findBySource(sid: Long, mid: Long): FavoriteEntity?

    @Query("SELECT sourceMessageId FROM favorites WHERE sourceSessionId = :sid ORDER BY sourceMessageId ASC")
    fun observeFavoritedMessageIds(sid: Long): Flow<List<Long>>

    @Query("UPDATE favorites SET groupId = NULL WHERE groupId = :gid")
    suspend fun rehomeGroup(gid: Long)

    @Query("UPDATE favorites SET groupId = :gid WHERE id IN (:ids)")
    suspend fun setGroupForFavorites(ids: List<Long>, gid: Long?)

    @Query("SELECT id FROM favorites WHERE groupId = :gid ORDER BY id ASC")
    suspend fun memberIds(gid: Long): List<Long>
}
