package com.example.flikky.ui.serving

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.di.ServiceLocator
import com.example.flikky.service.TransferController
import com.example.flikky.service.TransferService
import com.example.flikky.session.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

    fun stopService() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, TransferService::class.java).apply { action = TransferService.ACTION_STOP })
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unbindService(conn) }
    }
}
