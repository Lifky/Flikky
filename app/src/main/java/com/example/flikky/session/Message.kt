package com.example.flikky.session

import kotlinx.serialization.Serializable

enum class Origin { PHONE, BROWSER }

enum class MessageKind { TEXT, FILE }

@Serializable
sealed class Message {
    abstract val id: Long
    abstract val origin: Origin
    abstract val timestamp: Long

    @Serializable
    data class Text(
        override val id: Long,
        override val origin: Origin,
        override val timestamp: Long,
        val content: String,
    ) : Message()

    @Serializable
    data class File(
        override val id: Long,
        override val origin: Origin,
        override val timestamp: Long,
        val fileId: String,
        val name: String,
        val sizeBytes: Long,
        val mime: String,
        val status: Status,
    ) : Message() {
        enum class Status { OFFERED, IN_PROGRESS, COMPLETED, FAILED }
    }
}
