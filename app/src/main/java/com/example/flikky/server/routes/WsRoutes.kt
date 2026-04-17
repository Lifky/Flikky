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
