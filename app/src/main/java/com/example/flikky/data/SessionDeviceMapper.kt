package com.example.flikky.data

import com.example.flikky.data.db.entities.SessionDeviceEntity
import com.example.flikky.session.DeviceKind
import com.example.flikky.session.PeerDevice

internal fun PeerDevice.toSessionDeviceEntity(sessionId: Long) = SessionDeviceEntity(
    sessionId = sessionId,
    deviceId = deviceId,
    kind = kind.name,
    name = name,
    defaultLabel = defaultLabel,
    defaultOrdinal = defaultOrdinal,
    avatarKey = avatarKey,
    firstSeenAt = connectedAt,
)

internal fun SessionDeviceEntity.toPeerDevice() = PeerDevice(
    deviceId = deviceId,
    kind = runCatching { DeviceKind.valueOf(kind) }.getOrDefault(DeviceKind.BROWSER),
    name = name,
    defaultLabel = defaultLabel,
    defaultOrdinal = defaultOrdinal,
    avatarKey = avatarKey,
    connectedAt = firstSeenAt,
    visible = true,
)
