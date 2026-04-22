package com.example.flikky.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.example.flikky.di.ServiceLocator
import com.example.flikky.export.ExportMode
import com.example.flikky.server.KtorServer
import com.example.flikky.server.PinAuth
import com.example.flikky.server.ServiceMode
import com.example.flikky.server.dto.StatusDto
import com.example.flikky.util.IdGen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var currentMode: ServiceMode? = null
    private var statusBroadcastJob: Job? = null

    override fun onBind(intent: Intent?): IBinder = binding

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_EXPORT -> handleExport()
        }
        return START_NOT_STICKY
    }

    private fun handleStart() {
        if (currentMode == ServiceMode.Export) {
            Log.w(TAG, "ACTION_START refused: export in progress")
            NotificationHelper.ensureChannel(this)
            // No startForeground here — export path already holds the foreground slot.
            return
        }
        if (ktor != null) return
        startTransfer()
    }

    private fun startTransfer() {
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
            mode = ServiceMode.Transfer,
        )
        val port = server.start()
        ktor = server
        currentMode = ServiceMode.Transfer
        ServiceLocator.session.updateBoundPort(port)

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

        statusBroadcastJob = scope.launch {
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

    private fun handleExport() {
        if (currentMode == ServiceMode.Transfer) {
            Log.w(TAG, "ACTION_EXPORT refused: transfer in progress")
            return
        }
        if (currentMode == ServiceMode.Export || ktor != null) {
            Log.w(TAG, "ACTION_EXPORT ignored: export already running")
            return
        }

        // Caller (HomeViewModel) must have already armed the export snapshot.
        val armed = ServiceLocator.session.exportMode.value as? ExportMode.Armed
        if (armed == null) {
            Log.e(TAG, "ACTION_EXPORT without Armed state (was ${ServiceLocator.session.exportMode.value})")
            stopSelf()
            return
        }

        NotificationHelper.ensureChannel(this)
        val ip = ServiceLocator.networkInfo.currentWifiIpv4()
            ?: run {
                Log.e(TAG, "ACTION_EXPORT aborted: no Wi-Fi IPv4")
                ServiceLocator.session.clearExport()
                stopSelf()
                return
            }

        // Align with ACTION_START: fresh PinAuth whose initial PIN comes from
        // IdGen (same 6-digit generator). The HomeViewModel's armExport call will
        // have recorded the same PIN onto the snapshot; here we mint a Ktor-side
        // auth instance seeded with that PIN so /auth/pin accepts it.
        val auth = PinAuth(
            nowMs = System::currentTimeMillis,
            pinSupplier = { armed.session.pin },
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
            currentSessionId = { -1L },
            onPersistMessage = { /* no-op in export mode — export routes don't write messages */ },
            mode = ServiceMode.Export,
            onZipSent = { handleZipSent() },
        )
        val port = server.start()
        ktor = server
        currentMode = ServiceMode.Export
        ServiceLocator.session.updateBoundPort(port)
        // _running is the transfer-mode signal consumed by ServingViewModel.
        // Export mode has its own UI (ExportingScreen) that reads SessionState.exportMode
        // directly, so we intentionally leave _running null here.

        val notif = NotificationHelper.build(
            context = this,
            title = ExportNotificationText.TITLE,
            text = ExportNotificationText.body(armed.snapshot),
        )
        startForeground(
            EXPORT_NOTIFICATION_ID,
            notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private suspend fun handleZipSent() {
        // ExportRoutes already called markExportDone before invoking this callback.
        // Give the OS a moment to flush the response body before tearing down the
        // engine — tests cover that flushing the 1 s grace in KtorServer.stop() is
        // enough, but 500 ms of extra slack here avoids racing the finalizer.
        delay(500)
        stopSelf()
    }

    private fun handleStop() {
        stopActiveServer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopActiveServer() {
        val mode = currentMode
        ktor?.stop(); ktor = null
        controller = null
        statusBroadcastJob?.cancel(); statusBroadcastJob = null

        if (mode == ServiceMode.Transfer) {
            val sid = currentSessionId
            if (sid > 0) {
                runCatching {
                    runBlocking {
                        ServiceLocator.repository.endSession(sid, endedAt = System.currentTimeMillis())
                    }
                }
            }
            currentSessionId = -1L
        }
        if (mode == ServiceMode.Export) {
            runCatching { ServiceLocator.session.clearExport() }
        }
        currentMode = null
        pinAuth = null
        _running.value = null
    }

    override fun onDestroy() {
        if (ktor != null || currentMode != null) {
            stopActiveServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        ServiceLocator.reset()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.flikky.START"
        const val ACTION_STOP = "com.example.flikky.STOP"
        const val ACTION_EXPORT = "com.example.flikky.action.EXPORT"

        const val EXPORT_NOTIFICATION_ID = 1002

        private const val TAG = "TransferService"

        private val FMT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        fun defaultSessionName(nowMs: Long): String = "${FMT.format(Date(nowMs))} 会话"
    }
}
