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
import com.example.flikky.di.ServiceLocator
import com.example.flikky.service.TransferController
import com.example.flikky.service.TransferService
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
)

class ServingViewModel(app: Application) : AndroidViewModel(app) {
    private val _ui = MutableStateFlow(ServingUiState())
    val ui: StateFlow<ServingUiState> = _ui

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
     * 打开浏览器上传的文件。
     * 只对 origin=BROWSER & status=COMPLETED 的 Message.File 有效：
     * 文件在 context.filesDir/transfer/{fileId}，通过 FileProvider 以 msg.name 暴露给第三方 APP。
     * 手机自己发送的文件（origin=PHONE）不走这条路径——那是用户从手机选出去的，自己已有。
     */
    fun openFile(msg: Message.File) {
        if (msg.origin != Origin.BROWSER || msg.status != Message.File.Status.COMPLETED) return
        val ctx = getApplication<Application>()
        val f = File(File(ctx.filesDir, "transfer"), msg.fileId)
        if (!f.exists()) {
            Toast.makeText(ctx, "文件不存在（服务重启后浏览器上传的文件会丢失）", Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${ctx.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(ctx, authority, f, msg.name)
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

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(conn) }
    }
}
