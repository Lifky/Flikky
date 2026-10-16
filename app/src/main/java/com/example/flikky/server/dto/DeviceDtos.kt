package com.example.flikky.server.dto

import com.example.flikky.session.PeerDevice
import kotlinx.serialization.Serializable

/** 推给浏览器的本机设备记录：`device_updated` 的 payload，也是 `PUT /api/devices/self` 的响应（spec §5.4）。 */
@Serializable
data class DeviceDto(
    val deviceId: String,
    val name: String,
    val defaultLabel: String?,
    val defaultOrdinal: Int,
    val avatarKey: String,
    val avatarExplicit: Boolean,
)

fun PeerDevice.toDto(): DeviceDto =
    DeviceDto(deviceId, name, defaultLabel, defaultOrdinal, avatarKey, avatarExplicit)

fun PeerDevice.toDtoJson(): String = WireJson.encodeToString(DeviceDto.serializer(), toDto())

/** 浏览器连接后发的第一帧（spec §5.4）。字段全部来自它的 localStorage。 */
@Serializable
data class ClientHelloDto(
    val type: String,
    val avatarKey: String? = null,
    val avatarExplicit: Boolean = false,
    val deviceName: String? = null,
)

@Serializable
data class DeviceSelfUpdateRequest(
    val name: String? = null,
    val avatarKey: String? = null,
)
