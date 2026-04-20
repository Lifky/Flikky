package com.example.flikky.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import com.example.flikky.di.ServiceLocator
import com.example.flikky.server.KtorServer
import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.StatusDto
import com.example.flikky.util.IdGen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransferService : Service() {

    data class Running(val ip: String, val port: Int, val pin: String, val sessionId: Long)

    inner class Binding : Binder() {
        val running: StateFlow<Running?> get() = _running
        val controller: TransferController? get() = this@TransferService.controller
    }

    private val binding = Binding()
    private val _running = MutableStateFlow<Running?>(null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var ktor: KtorServer? = null
    private var pinAuth: PinAuth? = null
    private var controller: TransferController? = null
    private var currentSessionId: Long = -1L

    override fun onBind(intent: Intent?): IBinder = binding

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_START -> startTransfer()
            ACTION_STOP -> stopTransfer()
        }
        return START_NOT_STICKY
    }

    private fun startTransfer() {
        if (ktor != null) return
        NotificationHelper.ensureChannel(this)
        val ip = ServiceLocator.networkInfo.currentWifiIpv4()
            ?: run { stopSelf(); return }

        val now = System.currentTimeMillis()
        val name = defaultSessionName(now)
        val sid = runBlocking {
            runCatching {
                val id = ServiceLocator.repository.beginSession(name, startedAt = now)
                ServiceLocator.repository.fifoSweep()
                id
            }.getOrNull()
        }
        if (sid == null) { stopSelf(); return }
        currentSessionId = sid
        ServiceLocator.session.startNew(sid)

        val auth = PinAuth(
            nowMs = System::currentTimeMillis,
            pinSupplier = { IdGen.newPin() },
            tokenSupplier = { IdGen.newToken() },
        )
        pinAuth = auth

        val server = KtorServer(
            host = ip,
            pinAuth = auth,
            session = ServiceLocator.session,
            stats = ServiceLocator.stats,
            fileStore = ServiceLocator.fileStore,
            assetLoader = { path -> assets.open(path).use { it.readBytes() } },
            currentSessionId = { currentSessionId },
            onPersistMessage = { msg ->
                ServiceLocator.repository.appendMessage(currentSessionId, msg)
            },
        )
        val port = server.start()
        ktor = server

        controller = TransferController(
            session = ServiceLocator.session,
            stats = ServiceLocator.stats,
            fileStore = ServiceLocator.fileStore,
            repository = ServiceLocator.repository,
            wsHub = server.wsHub,
            nowMs = System::currentTimeMillis,
        )

        val pin = auth.currentPin() ?: "------"
        _running.value = Running(ip, port, pin, sid)

        scope.launch {
            while (isActive) {
                val snap = ServiceLocator.session.snapshot.value
                val uptime = (System.currentTimeMillis() - snap.serviceStartedAt) / 1000
                val status = StatusDto(
                    startedAt = snap.serviceStartedAt,
                    uptime = uptime,
                    fileCount = ServiceLocator.stats.fileCount(),
                    totalBytes = ServiceLocator.stats.totalBytes(),
                    bytesPerSecond = ServiceLocator.stats.bytesPerSecond(),
                    clientConnected = snap.clientConnected,
                )
                val payload = Json.encodeToString(StatusDto.serializer(), status)
                server.wsHub.broadcast("status", payload)
                delay(1000)
            }
        }

        val notif = NotificationHelper.build(
            context = this,
            title = "Flikky 服务运行中",
            text = "URL  http://$ip:$port\nPIN  打开 APP 查看",
        )
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun stopTransfer() {
        ktor?.stop(); ktor = null
        controller = null
        val sid = currentSessionId
        if (sid > 0) {
            runCatching {
                runBlocking { ServiceLocator.repository.endSession(sid, endedAt = System.currentTimeMillis()) }
            }
        }
        currentSessionId = -1L
        _running.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (ktor != null) {
            stopTransfer()
        }
        pinAuth = null
        ServiceLocator.reset()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.flikky.START"
        const val ACTION_STOP = "com.example.flikky.STOP"

        private val FMT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        fun defaultSessionName(nowMs: Long): String = "${FMT.format(Date(nowMs))} 会话"
    }
}
