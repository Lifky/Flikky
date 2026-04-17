package com.example.flikky.server.routes

import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
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
