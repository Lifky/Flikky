package com.example.flikky.session

import kotlinx.serialization.Serializable

enum class Origin { PHONE, BROWSER }

enum class MessageKind { TEXT, FILE }

@Serializable
sealed class Message {
    abstract val id: Long
    abstract val origin: Origin
    abstract val timestamp: Long
    /**
     * Stable origin-side identity for recall authorization (v1.3).
     * - Phone: `"phone-${Settings.Secure.ANDROID_ID}"` per device, set by TransferService.
     * - Browser: client `crypto.randomUUID()` from `X-Client-Id`, set by routes.
     * Nullable for pre-v1.3 rows.
     */
    abstract val senderId: String?
    /**
     * Recall timestamp (v1.3). Non-null means this message has been retracted;
     * UI renders the bubble as a "[消息已撤回]" placeholder. Repository drops
     * recalled rows from the FTS index too, so search results never surface them.
     */
    abstract val recalledAt: Long?

    @Serializable
    data class Text(
        override val id: Long,
        override val origin: Origin,
        override val timestamp: Long,
        val content: String,
        override val senderId: String? = null,
        override val recalledAt: Long? = null,
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
        override val senderId: String? = null,
        override val recalledAt: Long? = null,
    ) : Message() {
        enum class Status { OFFERED, IN_PROGRESS, COMPLETED, FAILED }
    }
}
