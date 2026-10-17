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

/**
 * 「本场名册」的一项（`peers` 事件，推给每台浏览器）：让浏览器认得会话里的其他设备，
 * 按发送设备画头像与名字。只有指代与展示用的字段 —— 凭据、UA 不进来（spec §12）。
 * [present] 为假 = 已被手机断开，仍列出是因为它发过的消息还在会话里。
 */
@Serializable
data class PeerEntryDto(
    val deviceId: String,
    val name: String,
    val defaultLabel: String?,
    val defaultOrdinal: Int,
    val avatarKey: String,
    val online: Boolean,
    val present: Boolean,
)

@Serializable
data class PeerRosterDto(val devices: List<PeerEntryDto>)

/** [known]：本会话见过的每台设备的最近记录；[current]：此刻 registry 里的可见设备（以它为准）。按加入先后排。 */
fun peerRosterJson(known: Collection<PeerDevice>, current: List<PeerDevice>): String {
    val currentIds = current.mapTo(HashSet()) { it.deviceId }
    val merged = LinkedHashMap<String, PeerDevice>()
    known.forEach { merged[it.deviceId] = it }
    current.forEach { merged[it.deviceId] = it }
    val entries = merged.values.sortedBy { it.connectedAt }.map { d ->
        val present = d.deviceId in currentIds
        PeerEntryDto(d.deviceId, d.name, d.defaultLabel, d.defaultOrdinal, d.avatarKey, present && d.online, present)
    }
    return WireJson.encodeToString(PeerRosterDto.serializer(), PeerRosterDto(entries))
}

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
