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
import com.example.flikky.data.SessionRepository
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
                is SessionRepository.RecallOutcome.Denied ->
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

    /** "我知道了" on the NetworkStatusBanner — fold Switched back to Ok. */
    fun acknowledgeNetworkSwitch() {
        ServiceLocator.session.acknowledgeNetworkSwitch()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(conn) }
    }
}
