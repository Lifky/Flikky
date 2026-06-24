package com.example.flikky.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(
            value = ["sourceSessionId", "sourceMessageId"],
            unique = true,
            name = "index_favorites_sourceSessionId_sourceMessageId",
        ),
        Index(value = ["groupId"], name = "index_favorites_groupId"),
    ],
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceSessionId: Long,
    val sourceMessageId: Long,
    val kind: String,
    val textContent: String? = null,
    val fileId: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileMime: String? = null,
    val groupId: Long? = null,
    val createdAt: Long,
    val sourceSessionName: String? = null,
    val origin: String? = null,
)
