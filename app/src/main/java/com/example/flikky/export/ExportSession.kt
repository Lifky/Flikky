package com.example.flikky.export

data class ExportSession(
    val sessionIds: List<Long>,
    val pin: String,
    val createdAt: Long,
    val consumed: Boolean = false,
)
