package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.session.SessionState
import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WsHub {
    private val mutex = Mutex()
    private val sessions = mutableListOf<WebSocketServerSession>()

    suspend fun add(s: WebSocketServerSession) = mutex.withLock { sessions.add(s) }
    suspend fun remove(s: WebSocketServerSession) = mutex.withLock { sessions.remove(s) }
    suspend fun size(): Int = mutex.withLock { sessions.size }

    suspend fun broadcast(type: String, jsonPayload: String) {
        val frame = """{"type":"$type","payload":$jsonPayload}"""
        val snapshot = mutex.withLock { sessions.toList() }
        snapshot.forEach { s -> runCatching { s.send(Frame.Text(frame)) } }
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
    /**
     * v1.3 B4：撤回事件广播。两端收到后把对应消息 DOM/Compose 节点替换为
     * 「[消息已撤回]」占位符。手机端走 DB 监听自动更新；这里只是为了让浏览器
     * 立即看到撤回，不必等 polling。
     */
    suspend fun broadcastRecall(sessionId: Long, messageId: Long) {
        val payload = """{"sessionId":$sessionId,"messageId":$messageId}"""
        broadcast("message_recalled", payload)
    }


    suspend fun broadcastStopAndClose() {
        val goodbye = """{"type":"server_stopped","payload":{}}"""
        val snapshot = mutex.withLock { sessions.toList() }
        snapshot.forEach { s -> runCatching { s.send(Frame.Text(goodbye)) } }
        snapshot.forEach { s ->
            runCatching {
                s.close(CloseReason(CloseReason.Codes.NORMAL, "server stopped"))
            }
        }
        mutex.withLock { sessions.clear() }
    }
}

private val pingPattern = Regex("""\{\s*"type"\s*:\s*"ping"\s*,\s*"id"\s*:\s*(\d+)\s*\}""")

fun Route.wsRoutes(
    pinAuth: PinAuth,
    session: SessionState,
    hub: WsHub,
) {
    webSocket("/ws") {
        val token = call.request.cookies[AUTH_COOKIE]
        if (token == null || !pinAuth.validateToken(token)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
            return@webSocket
        }
        hub.add(this)
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
                    val id = match.groupValues[1]
                    send(Frame.Text("""{"type":"pong","id":$id}"""))
                }
            }
        } finally {
            hub.remove(this)
            if (hub.size() == 0) session.setClientConnected(false)
        }
    }
}
