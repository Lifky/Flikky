package com.example.flikky.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,
    val name: String,
    val pinned: Boolean = false,
    val messageCount: Int = 0,
    val fileCount: Int = 0,
    val totalBytes: Long = 0,
    val previewText: String? = null,
)
