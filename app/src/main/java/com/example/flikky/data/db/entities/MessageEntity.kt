package com.example.flikky.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index("timestamp")],
)
data class MessageEntity(
    @PrimaryKey val id: Long,
    val sessionId: Long,
    val origin: String,
    val timestamp: Long,
    val kind: String,
    val content: String? = null,
    val fileId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileMime: String? = null,
    val fileStatus: String? = null,
)
