package com.example.flikky.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class SessionState(private val nowMs: () -> Long) {
    data class Snapshot(
        val serviceStartedAt: Long,
        val currentSessionId: Long?,
        val messages: List<Message>,
        val clientConnected: Boolean,
    )

    private val _snapshot = MutableStateFlow(
        Snapshot(
            serviceStartedAt = nowMs(),
            currentSessionId = null,
            messages = emptyList(),
            clientConnected = false,
        )
    )
    val snapshot: StateFlow<Snapshot> = _snapshot

    fun startNew(sessionId: Long) {
        _snapshot.value = Snapshot(
            serviceStartedAt = nowMs(),
            currentSessionId = sessionId,
            messages = emptyList(),
            clientConnected = false,
        )
    }

    fun addMessage(msg: Message) {
        _snapshot.update { it.copy(messages = it.messages + msg) }
    }

    fun updateMessage(id: Long, transform: (Message) -> Message) {
        _snapshot.update { s ->
            s.copy(messages = s.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    fun setClientConnected(connected: Boolean) {
        _snapshot.update { it.copy(clientConnected = connected) }
    }

    fun reset() {
        _snapshot.value = Snapshot(
            serviceStartedAt = nowMs(),
            currentSessionId = null,
            messages = emptyList(),
            clientConnected = false,
        )
    }
}
