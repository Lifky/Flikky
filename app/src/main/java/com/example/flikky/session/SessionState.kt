package com.example.flikky.session

import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
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

    private val _exportMode = MutableStateFlow<ExportMode>(ExportMode.Idle)
    val exportMode: StateFlow<ExportMode> = _exportMode

    /**
     * 从 Idle 迁移到 Armed，备好 PIN + snapshot 等浏览器来连。
     * 若当前非 Idle（已有导出在进行）→ 抛 IllegalStateException，调用方负责先 clear。
     */
    fun armExport(session: ExportSession, snapshot: ExportSnapshot) {
        val current = _exportMode.value
        check(current is ExportMode.Idle) {
            "armExport requires Idle state, was $current"
        }
        _exportMode.value = ExportMode.Armed(session, snapshot)
    }

    /**
     * 从 Armed 迁移到 Sending；再调进一步更新字节进度。
     * 状态不匹配抛 IllegalStateException。
     */
    fun updateExportProgress(bytesSent: Long, totalBytes: Long) {
        val session = when (val current = _exportMode.value) {
            is ExportMode.Armed -> current.session
            is ExportMode.Sending -> current.session
            else -> throw IllegalStateException(
                "updateExportProgress requires Armed or Sending, was $current"
            )
        }
        _exportMode.value = ExportMode.Sending(
            session = session,
            bytesSent = bytesSent,
            totalBytes = totalBytes,
        )
    }

    /**
     * 从 Sending 迁移到 Done。状态不匹配抛 IllegalStateException。
     */
    fun markExportDone() {
        val current = _exportMode.value
        check(current is ExportMode.Sending) {
            "markExportDone requires Sending state, was $current"
        }
        _exportMode.value = ExportMode.Done(current.session)
    }

    /**
     * 回到 Idle。从任何状态调用都合法，幂等——用户点"取消"、zip 发完后服务停、
     * APP 崩恢复时都走这里。
     */
    fun clearExport() {
        _exportMode.value = ExportMode.Idle
    }
}
