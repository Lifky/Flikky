package com.example.flikky.session

import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lifecycle of the Wi-Fi link under the running server, exposed to the UI so
 * ServingScreen / ExportingScreen can render a status banner.
 *
 *  - [Ok]: IPv4 hasn't changed since the server was bound.
 *  - [Switching]: a rebind is in progress (Ktor stopped, new engine not up yet).
 *  - [Lost]: we used to have IPv4 and now don't — user probably lost Wi-Fi.
 *  - [Switched]: rebind finished; UI shows the new URL + "我知道了" button
 *    whose callback is [SessionState.acknowledgeNetworkSwitch].
 */
sealed class NetworkStatus {
    object Ok : NetworkStatus()
    object Switching : NetworkStatus()
    object Lost : NetworkStatus()
    data class Switched(val newUrl: String) : NetworkStatus()
}

class SessionState(private val nowMs: () -> Long) {

    private val _fileTransferProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val fileTransferProgress: StateFlow<Map<Long, Float>> = _fileTransferProgress

    fun updateProgress(messageId: Long, progress: Float) {
        _fileTransferProgress.update { it + (messageId to progress) }
    }

    fun clearProgress(messageId: Long) {
        _fileTransferProgress.update { it - messageId }
    }

    data class Snapshot(
        val serviceStartedAt: Long,
        val currentSessionId: Long?,
        val messages: List<Message>,
        val clientConnected: Boolean,
        /**
         * Port the in-process Ktor server is actually bound to, or 0 when no
         * server is up. KtorServer scans [8080, 8099] for the first free port,
         * so this is the only authoritative source for the URL shown in the UI.
         * Defaulted so existing test sites (and older snapshots) behave unchanged.
         */
        val boundPort: Int = 0,
        /**
         * Current network link status — drives the status banner in
         * ServingScreen / ExportingScreen. Defaulted so older tests and
         * snapshot consumers stay unchanged.
         */
        val networkStatus: NetworkStatus = NetworkStatus.Ok,
    )

    private val _snapshot = MutableStateFlow(
        Snapshot(
            serviceStartedAt = nowMs(),
            currentSessionId = null,
            messages = emptyList(),
            clientConnected = false,
            boundPort = 0,
            networkStatus = NetworkStatus.Ok,
        )
    )
    val snapshot: StateFlow<Snapshot> = _snapshot

    fun startNew(sessionId: Long) {
        _snapshot.value = Snapshot(
            serviceStartedAt = nowMs(),
            currentSessionId = sessionId,
            messages = emptyList(),
            clientConnected = false,
            boundPort = 0,
            networkStatus = NetworkStatus.Ok,
        )
    }

    /**
     * Called by [com.example.flikky.service.TransferService] right after
     * [com.example.flikky.server.KtorServer.start] returns the actual bound
     * port — for both transfer and export modes. UI layer reads this to build
     * the browser URL. Resets to 0 on [reset].
     */
    fun updateBoundPort(port: Int) {
        _snapshot.update { it.copy(boundPort = port) }
    }

    fun addMessage(msg: Message) {
        _snapshot.update { it.copy(messages = it.messages + msg) }
    }

    fun updateMessage(id: Long, transform: (Message) -> Message) {
        _snapshot.update { s ->
            s.copy(messages = s.messages.map { if (it.id == id) transform(it) else it })
        }
    }

    /**
     * v1.3 撤回内存同步：从当前会话的消息列表里移除指定消息。
     * TransferController.recallMessage 在 repository 真删 DB 后调用，让
     * ServingScreen / WS 广播都能立即反映"消息消失"。
     */
    fun removeMessage(id: Long) {
        _snapshot.update { s ->
            s.copy(messages = s.messages.filterNot { it.id == id })
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
            boundPort = 0,
            networkStatus = NetworkStatus.Ok,
        )
        _fileTransferProgress.value = emptyMap()
        _peerAvatarId.value = 0
    }

    /**
     * Set the current network link status. Called from TransferService's
     * NetworkCallback as the rebinder decides Ok/Switching/Lost/Switched.
     */
    fun updateNetworkStatus(status: NetworkStatus) {
        _snapshot.update { it.copy(networkStatus = status) }
    }

    /**
     * UI's "我知道了" button on the Switched banner folds state back to Ok.
     * No-op when the status is already Ok; from Switching/Lost we also allow
     * the ack to demote to Ok (the banner is advisory, not authoritative).
     */
    fun acknowledgeNetworkSwitch() {
        _snapshot.update { it.copy(networkStatus = NetworkStatus.Ok) }
    }

    // M9: browser-side avatar chosen by the PC user, received via client_hello WS frame.
    private val _peerAvatarId = MutableStateFlow(0)
    val peerAvatarId: StateFlow<Int> = _peerAvatarId.asStateFlow()

    fun setPeerAvatar(id: Int) { _peerAvatarId.value = id }

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
