package com.example.flikky.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * 某次会话里出现过的对端设备快照（spec §8.1）。不是跨会话的设备档案 —— 浏览器身份只在会话内有效。
 * 主键 (sessionId, deviceId) 的首列覆盖外键索引。
 */
@Entity(
    tableName = "session_devices",
    primaryKeys = ["sessionId", "deviceId"],
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SessionDeviceEntity(
    val sessionId: Long,
    val deviceId: String,
    val kind: String,
    /** 空串 = 用默认名。 */
    val name: String,
    /** null = UA 识别不出，渲染为「浏览器」。 */
    val defaultLabel: String?,
    val defaultOrdinal: Int,
    val avatarKey: String,
    val firstSeenAt: Long,
)
