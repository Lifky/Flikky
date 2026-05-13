package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.session.SessionState
import io.ktor.server.routing.Route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
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
                // v1 ignores inbound; ping/pong handled by Ktor
            }
        } finally {
            hub.remove(this)
            if (hub.size() == 0) session.setClientConnected(false)
        }
    }
}
