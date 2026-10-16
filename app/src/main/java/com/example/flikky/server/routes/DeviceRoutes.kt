package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.dto.DeviceSelfUpdateRequest
import com.example.flikky.server.dto.toDto
import com.example.flikky.session.PeerDevice
import com.example.flikky.util.DeviceNamePolicy
import com.example.flikky.util.PeerAvatarPolicy
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.put

/**
 * 浏览器给自己改名 / 换头像（spec §5.4）。PUT 属于状态修改请求，在 KtorServer 层先过 D79 的同源校验。
 * 浏览器自己选的头像标记为「本机自选」，手机改默认值时不再跟着变。
 */
fun Route.deviceRoutes(
    authGate: AuthGate,
    registry: DeviceRegistry,
    /** 改完推给这台设备的其他标签页（KtorServer 用自己的 wsHub）。 */
    onSelfUpdated: suspend (PeerDevice) -> Unit = {},
) {
    put("/api/devices/self") {
        val device = authGate.deviceFor(call.request.cookies[AUTH_COOKIE])
        if (device == null) { call.respond(HttpStatusCode.Unauthorized); return@put }
        val req = runCatching { call.receive<DeviceSelfUpdateRequest>() }.getOrNull()
        if (req == null) { call.respond(HttpStatusCode.BadRequest); return@put }
        val avatar = req.avatarKey
        if (avatar != null && !PeerAvatarPolicy.isAcceptableKey(avatar)) {
            call.respond(HttpStatusCode.BadRequest)
            return@put
        }
        val updated = registry.update(
            deviceId = device.deviceId,
            name = req.name?.let(DeviceNamePolicy::normalize),
            avatarKey = avatar,
            avatarExplicit = if (avatar != null) true else null,
        )
        if (updated == null) { call.respond(HttpStatusCode.Unauthorized); return@put }
        onSelfUpdated(updated)
        call.respond(updated.toDto())
    }
}
