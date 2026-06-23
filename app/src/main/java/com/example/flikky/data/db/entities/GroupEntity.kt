package com.example.flikky.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
)
