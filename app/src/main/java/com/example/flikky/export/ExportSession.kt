package com.example.flikky.export

data class ExportSession(
    val sessionIds: List<Long>,
    val pin: String,
    val createdAt: Long,
    val consumed: Boolean = false,
    val requirePin: Boolean = true,
    val scope: ExportScope = ExportScope.SESSIONS,
    val favoriteCount: Int = 0,
    val settingsIncluded: Boolean = false,
)
