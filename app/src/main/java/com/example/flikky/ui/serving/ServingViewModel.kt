package com.example.flikky.ui.serving

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.di.ServiceLocator
import com.example.flikky.service.TransferController
import com.example.flikky.service.TransferService
import com.example.flikky.session.Message
import com.example.flikky.session.NetworkStatus
import com.example.flikky.session.Origin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class ServingUiState(
    val url: String = "",
    val pin: String = "",
    val uptimeSeconds: Long = 0L,
    val fileCount: Int = 0,
    val bytesPerSecond: Long = 0L,
    val clientConnected: Boolean = false,
    val messages: List<Message> = emptyList(),
    val networkStatus: NetworkStatus = NetworkStatus.Ok,
)

class ServingViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(ServingUiState())
    val ui: StateFlow<ServingUiState> = _ui

    /**
     * One-shot UI events (snackbar 文案)。Channel + receiveAsFlow 比 SharedFlow
     * 更适合"不可丢、消费一次"的场景：撤回提醒不能因为重组错过。
     */
    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    val fileTransferProgress: StateFlow<Map<Long, Float>> =
        ServiceLocator.session.fileTransferProgress

    /** M9: avatar chosen by the browser peer, set via client_hello WS frame. */
    val peerAvatarId: StateFlow<Int> = ServiceLocator.session.peerAvatarId
    val peerAvatarKey: StateFlow<String> = ServiceLocator.session.peerAvatarKey

    val settings: StateFlow<FlikkySettings> =
        ServiceLocator.settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.Eagerly, FlikkySettings())

    private var running: TransferService.Running? = null
    private var controller: TransferController? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as TransferService.Binding
            controller = b.controller
            b.running.onEach { r ->
                running = r
                _ui.value = _ui.value.copy(
                    url = r?.let { "http://${it.ip}:${it.port}" } ?: "",
                    pin = r?.pin ?: "",
                )
            }.launchIn(viewModelScope)
        }
        override fun onServiceDisconnected(name: ComponentName?) { controller = null }
    }

    init {
        val ctx = getApplication<Application>()
        ctx.bindService(
            Intent(ctx, TransferService::class.java), conn, Context.BIND_AUTO_CREATE
        )

        // v1.3 对端撤回 snackbar：浏览器撤回消息后 TransferService 桥接
        // emit 到 ServiceLocator.recallNotifications → 这里转发到 events channel
        // → ServingScreen 弹 snackbar「对方撤回了一条消息」。
        ServiceLocator.recallNotifications.onEach { msg ->
            _events.trySend(msg)
        }.launchIn(viewModelScope)

        combine(
            ServiceLocator.session.snapshot,
            tick1Hz(),
        ) { snap, _ ->
            val seconds = if (snap.serviceStartedAt == 0L) 0L else
                (System.currentTimeMillis() - snap.serviceStartedAt) / 1000
            ServingUiState(
                url = _ui.value.url,
                pin = _ui.value.pin,
                uptimeSeconds = seconds,
                fileCount = ServiceLocator.stats.fileCount(),
                bytesPerSecond = ServiceLocator.stats.bytesPerSecond(),
                clientConnected = snap.clientConnected,
                messages = snap.messages,
                networkStatus = snap.networkStatus,
            )
        }.onEach { _ui.value = it }.launchIn(viewModelScope)
    }

    private fun tick1Hz() = flow { while (true) { emit(Unit); delay(1000) } }

    fun sendText(text: String) {
        viewModelScope.launch { controller?.sendText(text) }
    }

    fun offerFile(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch { controller?.offerFile(uri, resolver) }
    }

    fun sendFavorite(favorite: FavoriteEntity) {
        when (favorite.kind) {
            "TEXT" -> sendText(favorite.textContent.orEmpty())
            "FILE" -> sendFavoriteFile(favorite)
        }
    }

    fun recordRecentFavorite(favoriteId: Long) {
        viewModelScope.launch { ServiceLocator.settingsRepository.recordRecentFavorite(favoriteId) }
    }

    // 进行中会话的快捷设置：会话期间「设置」tab 被锁，用户改不了这些常调项。
    // 写的是与设置页同一份 settings —— 一处改动，App 气泡 + 已连浏览器气泡 + 设置页全同步。
    fun setBubbleCornerRadius(dp: Int) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setBubbleCornerRadius(dp) }
    }

    fun setDarkMode(mode: DarkMode) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setDarkMode(mode) }
    }

    private fun sendFavoriteFile(favorite: FavoriteEntity) {
        val depotId = favorite.fileId ?: return
        val source = ServiceLocator.favoriteFileStore.resolve(depotId)
        val name = favorite.fileName ?: "unnamed"
        val size = favorite.fileSize ?: source.length()
        val mime = favorite.fileMime ?: "application/octet-stream"
        viewModelScope.launch {
            val sent = controller?.offerStoredFile(source, name, size, mime) == true
            if (!sent) _events.trySend("收藏文件不存在")
        }
    }

    /**
     * v1.3 D26 修订：撤回入口（仅 ServingScreen 提供）。
     * controller 为 null 不该发生（ServingScreen 只在服务运行中可见），但加防御。
     */
    fun recallMessage(messageId: Long) {
        val ctrl = controller ?: run {
            _events.trySend("无法撤回：服务已停止"); return
        }
        viewModelScope.launch {
            when (ctrl.recallMessage(messageId)) {
                is SessionRepository.RecallOutcome.Success ->
                    _events.trySend("消息已撤回")
                is SessionRepository.RecallOutcome.NotFound ->
                    _events.trySend("消息已撤回")  // 真删后再撤等价 idempotent 成功
                is SessionRepository.RecallOutcome.Denied -> // unreachable since v1.5.0 — recallMessage never returns Denied
                    _events.trySend("只能撤回自己发的消息")
            }
        }
    }

    /**
     * 打开浏览器上传的文件。
     * 只对 origin=BROWSER & status=COMPLETED 的 Message.File 有效：
     * 文件在 context.filesDir/transfer/{fileId}，通过 FileProvider 以 msg.name 暴露给第三方 APP。
     * 手机自己发送的文件（origin=PHONE）不走这条路径——那是用户从手机选出去的，自己已有。
     */
    fun openFile(msg: Message.File) {
        if (msg.status != Message.File.Status.COMPLETED) return
        val ctx = getApplication<Application>()
        val sid = ServiceLocator.session.snapshot.value.currentSessionId ?: run {
            Toast.makeText(ctx, "无会话上下文", Toast.LENGTH_SHORT).show()
            return
        }
        val f = File(File(File(ctx.filesDir, "sessions/$sid"), "files"), msg.fileId)
        if (!f.exists()) {
            Toast.makeText(ctx, "文件不存在（服务重启后浏览器上传的文件会丢失）", Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${ctx.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(ctx, authority, f, msg.name)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(ctx, "无法暴露此文件（FileProvider 路径未配置）", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, msg.mime.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(Intent.createChooser(intent, "打开文件").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "没有可以打开此类型文件的应用", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopService() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, TransferService::class.java).apply { action = TransferService.ACTION_STOP })
    }

    fun removeFailedMessage(messageId: Long) {
        ServiceLocator.session.removeMessage(messageId)
    }

    // ── Soft-delete + undo (M8) ──────────────────────────────────────────────

    /**
     * 本地软删除：从内存消息列表移除消息，保留快照用于撤销。
     * 不广播浏览器，不写 DB（提交由 [commitDelete] 完成）。
     * 撤销由 [undoDelete] 完成。
     */
    private var pendingDelete: Message? = null

    fun deleteLocalWithUndo(id: Long) {
        pendingDelete = ServiceLocator.session.snapshot.value.messages.firstOrNull { it.id == id }
        ServiceLocator.session.removeMessage(id)
    }

    /**
     * 撤销软删除：将消息恢复到原始位置。
     * addMessage 按 timestamp 插入，消息会回到列表中时间顺序正确的位置。
     * LazyColumn key={it.id} 保证动画平滑。
     */
    fun undoDelete() {
        pendingDelete?.let { ServiceLocator.session.addMessage(it) }
        pendingDelete = null
    }

    /**
     * 提交软删除到 DB。若消息是文件，repository.deleteMessage 会同时删盘。
     * runCatching 保证 DB 写失败不会崩溃——内存已移除，下次启动 finalizeOrphans 会清理。
     */
    fun commitDelete(id: Long) {
        pendingDelete = null
        viewModelScope.launch {
            runCatching { ServiceLocator.repository.deleteMessage(id) }
        }
    }

    /** "我知道了" on the NetworkStatusBanner — fold Switched back to Ok. */
    fun acknowledgeNetworkSwitch() {
        ServiceLocator.session.acknowledgeNetworkSwitch()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(conn) }
    }
}
