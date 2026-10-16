package com.example.flikky.server.routes

import com.example.flikky.server.DeviceRegistry
import com.example.flikky.server.dto.ClientHelloDto
import com.example.flikky.server.dto.toDtoJson
import com.example.flikky.session.SessionState
import com.example.flikky.util.DeviceNamePolicy
import com.example.flikky.util.PeerAvatarPolicy
import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** 凭据无效（没登录、被撤销后重连、换了 IP）。浏览器据此停止重连并提示「登录已失效」（spec §4.6）。 */
const val WS_CLOSE_UNAUTHORIZED: Short = 4401

/** 手机断开了这台设备（spec §4.4）。浏览器据此停止重连。 */
const val WS_CLOSE_REVOKED: Short = 4403

class WsHub {
    private class Conn(val session: WebSocketServerSession, val deviceId: String?)

    private val mutex = Mutex()
    private val conns = mutableListOf<Conn>()

    suspend fun add(s: WebSocketServerSession, deviceId: String? = null) = mutex.withLock { conns.add(Conn(s, deviceId)) }
    suspend fun remove(s: WebSocketServerSession) = mutex.withLock { conns.removeAll { it.session === s } }
    suspend fun size(): Int = mutex.withLock { conns.size }

    suspend fun broadcast(type: String, jsonPayload: String) {
        val frame = """{"type":"$type","payload":$jsonPayload}"""
        val snapshot = mutex.withLock { conns.map { it.session } }
        snapshot.forEach { s -> runCatching { s.send(Frame.Text(frame)) } }
    }

    /** 只推给 [deviceId] 的所有标签页（spec §5.4：设备记录不外泄给别的设备）。 */
    suspend fun sendToDevice(deviceId: String, type: String, jsonPayload: String) {
        val frame = """{"type":"$type","payload":$jsonPayload}"""
        val targets = mutex.withLock { conns.filter { it.deviceId == deviceId }.map { it.session } }
        targets.forEach { s -> runCatching { s.send(Frame.Text(frame)) } }
    }

    /** 断开设备：先告诉它的每个标签页「是手机断开的」，再以 [WS_CLOSE_REVOKED] 关闭（spec §4.4）。 */
    suspend fun closeDevice(deviceId: String) {
        val targets = mutex.withLock { conns.filter { it.deviceId == deviceId }.map { it.session } }
        targets.forEach { s -> runCatching { s.send(Frame.Text("""{"type":"device_revoked","payload":{}}""")) } }
        targets.forEach { s -> runCatching { s.close(CloseReason(WS_CLOSE_REVOKED, "revoked")) } }
    }

    /**
     * v1.3 B4：撤回事件广播。两端收到后把对应消息 DOM/Compose 节点替换为
     * 「[消息已撤回]」占位符。手机端走 DB 监听自动更新；这里只是为了让浏览器
     * 立即看到撤回，不必等 polling。
     */
    suspend fun broadcastRecall(sessionId: Long, messageId: Long) {
        val payload = """{"sessionId":$sessionId,"messageId":$messageId}"""
        broadcast("message_recalled", payload)
    }

    /**
     * Tell every connected browser the server is stopping intentionally, then
     * close the WebSocket cleanly. The `server_stopped` event lets the client
     * tell the two disconnect causes apart:
     *
     *  - deliberate teardown (user tapped 停止服务 / export zip finished) →
     *    next service launch is a brand-new session with a fresh PIN/URL,
     *    auto-reconnect is wrong;
     *  - network blip (Wi-Fi drop, rebind on IP change) → reconnect is exactly
     *    what we want.
     *
     * Called by [com.example.flikky.service.TransferService.stopActiveServer]
     * — NOT from the rebind path so transient Ktor restarts look like a
     * network blip and trigger the normal reconnect flow.
     */
    suspend fun broadcastStopAndClose() {
        val goodbye = """{"type":"server_stopped","payload":{}}"""
        val snapshot = mutex.withLock { conns.map { it.session } }
        snapshot.forEach { s -> runCatching { s.send(Frame.Text(goodbye)) } }
        snapshot.forEach { s ->
            runCatching {
                s.close(CloseReason(CloseReason.Codes.NORMAL, "server stopped"))
            }
        }
        mutex.withLock { conns.clear() }
    }
}

private val pingPattern = Regex("""\{\s*"type"\s*:\s*"ping"\s*,\s*"id"\s*:\s*(\d+)\s*\}""")
private val helloJson = Json { ignoreUnknownKeys = true }

/** 只认 `type == "client_hello"` 的正规 JSON。名字是自由文本（引号、转义、emoji），不能再用正则抠。 */
internal fun parseClientHello(text: String): ClientHelloDto? {
    if (!text.contains("client_hello")) return null
    return runCatching { helloJson.decodeFromString(ClientHelloDto.serializer(), text) }
        .getOrNull()
        ?.takeIf { it.type == "client_hello" }
}

fun Route.wsRoutes(
    authGate: AuthGate,
    session: SessionState,
    hub: WsHub,
    registry: DeviceRegistry,
    /** 连接时上报的头像怎么定（spec §5.3）。TransferService 注入带 DataStore 的版本。 */
    decideHelloAvatar: suspend (announcedKey: String?, explicit: Boolean) -> PeerAvatarPolicy.Decision =
        { key, explicit -> PeerAvatarPolicy.onHello(key, explicit, storedDefault = null) },
) {
    webSocket("/ws") {
        val device = authGate.deviceFor(call.request.cookies[AUTH_COOKIE])
        if (device == null) {
            close(CloseReason(WS_CLOSE_UNAUTHORIZED, "unauthorized"))
            return@webSocket
        }
        val deviceId = device.deviceId
        hub.add(this, deviceId)
        registry.attach(deviceId)
        session.setClientConnected(true)
        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                // v1.3 B5：应用层 ping/pong。浏览器 setInterval 每秒检查"空闲 3 秒"后
                // 主动发 {"type":"ping","id":N}；服务端立即回 {"type":"pong","id":N}。
                // 让浏览器主动检测半开 TCP（OS 没立即撕，readyState 仍 OPEN），不再依赖
                // v1.2 的"4 秒 frame 超时被动 close"。regex 实现避免 JSON 解析依赖。
                val match = pingPattern.matchEntire(text.trim())
                if (match != null) {
                    send(Frame.Text("""{"type":"pong","id":${match.groupValues[1]}}"""))
                    continue
                }
                val hello = parseClientHello(text) ?: continue
                // 只采纳这台设备的第一次上报；之后以服务端为准 —— 手机在它离线时改的名不能被旧值盖回去。
                if (!registry.isAnnounced(deviceId)) {
                    val decision = decideHelloAvatar(hello.avatarKey, hello.avatarExplicit)
                    registry.applyHello(
                        deviceId = deviceId,
                        name = hello.deviceName?.let(DeviceNamePolicy::normalize),
                        avatarKey = decision.avatarKey,
                        avatarExplicit = decision.explicit,
                    )
                }
                // 回一份当前记录：浏览器据此写回本地（重连时拿到的是手机离线期间改过的值）。
                registry.device(deviceId)?.let { hub.sendToDevice(deviceId, "device_updated", it.toDtoJson()) }
            }
        } finally {
            hub.remove(this)
            registry.detach(deviceId)
            if (hub.size() == 0) session.setClientConnected(false)
        }
    }
}
