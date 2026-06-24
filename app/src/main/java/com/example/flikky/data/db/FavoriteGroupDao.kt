package com.example.flikky.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteGroupDao {
    @Insert suspend fun insert(group: FavoriteGroupEntity): Long

    @Update suspend fun update(group: FavoriteGroupEntity)

    @Query("DELETE FROM favorite_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM favorite_groups ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<FavoriteGroupEntity>>

    @Query("SELECT * FROM favorite_groups WHERE id = :id")
    suspend fun getById(id: Long): FavoriteGroupEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM favorite_groups")
    suspend fun maxSortOrder(): Int
}
