package com.example.flikky.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.example.flikky.data.SessionRepository
import com.example.flikky.di.ServiceLocator
import com.example.flikky.export.ExportMode
import com.example.flikky.network.LinkInfo
import com.example.flikky.network.NetworkRebinder
import com.example.flikky.network.RebindIntent
import com.example.flikky.server.KtorServer
import com.example.flikky.server.PinAuth
import com.example.flikky.server.ServiceMode
import com.example.flikky.server.dto.ServerRecallOutcome
import com.example.flikky.server.dto.StatusDto
import com.example.flikky.session.Message
import com.example.flikky.session.NetworkStatus
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
import java.net.Inet4Address
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

    private val rebinder = NetworkRebinder()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentHostIp: String? = null

    override fun onBind(intent: Intent?): IBinder = binding

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        // 兜底：Context.startForegroundService 调出后系统硬性要求 5 秒内调 startForeground，
        // 否则抛 ForegroundServiceDidNotStartInTimeException。所有 onStartCommand 分支的
        // early return 都必须先占住前台槽位。这里无脑先 startForeground 一个临时通知；
        // 业务分支若决定继续运行，会用真正的通知 replace；若 stopSelf，临时通知随 stopForeground 一并清掉。
        NotificationHelper.ensureChannel(this)
        if (currentMode == null) {
            val placeholder = NotificationHelper.build(
                context = this,
                title = "Flikky",
                text = "正在启动…",
            )
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                placeholder,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }
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
            // No-op: keep the running export foreground notification.
            return
        }
        if (ktor != null) return
        startTransfer()
    }

    private fun startTransfer() {
        val ip = ServiceLocator.networkInfo.currentWifiIpv4()
            ?: run {
                Log.e(TAG, "startTransfer aborted: no Wi-Fi IPv4")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

        val now = System.currentTimeMillis()
        val name = defaultSessionName(now)
        val sid = runBlocking {
            runCatching {
                val id = ServiceLocator.repository.beginSession(name, startedAt = now)
                ServiceLocator.repository.fifoSweep()
                id
            }.getOrNull()
        }
        if (sid == null) {
            Log.e(TAG, "startTransfer aborted: beginSession failed")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        currentSessionId = sid
        ServiceLocator.session.startNew(sid)

        val auth = PinAuth(
            nowMs = System::currentTimeMillis,
            pinSupplier = { IdGen.newPin() },
            tokenSupplier = { IdGen.newToken() },
        )
        pinAuth = auth

        val server = buildTransferKtor(ip, auth)
        val port = server.start()
        ktor = server
        currentMode = ServiceMode.Transfer
        currentHostIp = ip
        rebinder.prime(ip)
        ServiceLocator.session.updateBoundPort(port)
        registerNetworkCallbackIfNeeded()

        controller = TransferController(
            session = ServiceLocator.session,
            stats = ServiceLocator.stats,
            fileStore = ServiceLocator.fileStore,
            repository = ServiceLocator.repository,
            // 用 lambda 取 field-level ktor.wsHub —— 同 statusBroadcastJob 的修复，
            // 让 rebind 后 controller 自动跟随新 wsHub，不再朝旧 hub 广播。
            wsHub = { ktor?.wsHub },
            nowMs = System::currentTimeMillis,
            senderId = phoneSenderId(),
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
                // 必须用 field-level [ktor] 取当前 wsHub —— 闭包捕获 startTransfer
                // 局部 server.wsHub 会让 rebind 后 status 持续推到旧 hub，新 hub
                // 永远收不到帧，浏览器心跳 4 秒超时 → close → reconnect 死循环。
                ktor?.wsHub?.broadcast("status", payload)
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
            // Don't stopSelf — transfer is alive and owns the foreground.
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
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val ip = ServiceLocator.networkInfo.currentWifiIpv4()
            ?: run {
                Log.e(TAG, "ACTION_EXPORT aborted: no Wi-Fi IPv4")
                ServiceLocator.session.clearExport()
                stopForeground(STOP_FOREGROUND_REMOVE)
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

        val server = buildExportKtor(ip, auth)
        val port = server.start()
        ktor = server
        currentMode = ServiceMode.Export
        currentHostIp = ip
        rebinder.prime(ip)
        ServiceLocator.session.updateBoundPort(port)
        registerNetworkCallbackIfNeeded()
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
        // Before tearing down Ktor, tell live WS clients this is a deliberate
        // stop so they DON'T enter the reconnect loop — user's next service
        // launch is a fresh session with a new PIN/URL.
        // The rebind path goes around stopActiveServer entirely (it calls
        // ktor.stop() inline), so transient restarts still look like a
        // network blip to the browser and trigger normal reconnect.
        ktor?.wsHub?.let { hub ->
            runCatching {
                runBlocking { hub.broadcastStopAndClose() }
            }
        }
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
            // 只在用户取消（Armed/Sending）时清；如果 zip 已发完（Done）保留
            // exportMode 让 ExportingScreen 渲染"保留/删除"完成屏。用户在屏上
            // 选择后会通过 ExportingViewModel.acknowledge() 自己清。
            val em = ServiceLocator.session.exportMode.value
            if (em is ExportMode.Armed || em is ExportMode.Sending) {
                runCatching { ServiceLocator.session.clearExport() }
            }
        }
        currentMode = null
        pinAuth = null
        currentHostIp = null
        _running.value = null
    }

    /**
     * Builds a Transfer-mode KtorServer. Called from startTransfer() and the
     * rebind path after the previous engine was torn down — both flows use the
     * same PinAuth/session wiring; only [host] changes on a rebind.
     */
    private fun buildTransferKtor(host: String, auth: PinAuth): KtorServer = KtorServer(
        host = host,
        pinAuth = auth,
        session = ServiceLocator.session,
        stats = ServiceLocator.stats,
        fileStore = ServiceLocator.fileStore,
        assetLoader = { path -> assets.open(path).use { it.readBytes() } },
        currentSessionId = { currentSessionId },
        onPersistMessage = { msg: Message ->
            ServiceLocator.repository.appendMessage(currentSessionId, msg)
        },
        onRecallMessage = { messageId, callerSenderId ->
            // 桥接 data 层 RecallOutcome → server 层 ServerRecallOutcome，保持
            // server 包不反向依赖 data 包（CLAUDE.md「不把 Android Context 穿透到
            // server 包」同源原则的对偶：data 层细节也不外泄）。
            when (val out = ServiceLocator.repository.recallMessage(messageId, callerSenderId)) {
                is SessionRepository.RecallOutcome.Success ->
                    ServerRecallOutcome.Success(out.messageId, out.sessionId, out.recalledAt)
                is SessionRepository.RecallOutcome.NotFound -> ServerRecallOutcome.NotFound
                is SessionRepository.RecallOutcome.Denied -> ServerRecallOutcome.Denied
                is SessionRepository.RecallOutcome.AlreadyRecalled ->
                    ServerRecallOutcome.AlreadyRecalled(out.messageId, out.sessionId, out.recalledAt)
            }
        },
        mode = ServiceMode.Transfer,
    )

    /**
     * Builds an Export-mode KtorServer. Mirrors buildTransferKtor so the
     * rebind path has a symmetric factory to call.
     */
    private fun buildExportKtor(host: String, auth: PinAuth): KtorServer = KtorServer(
        host = host,
        pinAuth = auth,
        session = ServiceLocator.session,
        stats = ServiceLocator.stats,
        fileStore = ServiceLocator.fileStore,
        assetLoader = { path -> assets.open(path).use { it.readBytes() } },
        currentSessionId = { -1L },
        onPersistMessage = { /* no-op in export mode — export routes don't write messages */ },
        // Export 模式不挂 messageRoutes，撤回处理器永不被调用；显式 stub 是为了让
        // 读代码的人立刻看到这点，不必去 KtorServer 翻默认值。
        onRecallMessage = { _, _ -> ServerRecallOutcome.NotFound },
        mode = ServiceMode.Export,
        onZipSent = { handleZipSent() },
    )

    /**
     * Register a [ConnectivityManager.NetworkCallback] scoped to Wi-Fi so we
     * learn about IP changes (laptop hotspot → router, router → laptop hotspot,
     * etc). Idempotent — second call during the same Service lifetime no-ops.
     *
     * Note: on locked / Doze devices the callback may be delayed by several
     * seconds. v1.2 accepts that — the banner will still eventually surface
     * "已失联" once the OS re-dispatches. No heartbeat fallback in v1.2.
     */
    private fun registerNetworkCallbackIfNeeded() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: run {
                Log.w(TAG, "ConnectivityManager unavailable — rebind disabled")
                return
            }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val ipv4 = linkProperties.linkAddresses
                    .mapNotNull { it.address as? Inet4Address }
                    .firstOrNull { !it.isLoopbackAddress && !it.isAnyLocalAddress }
                    ?.hostAddress
                handleLinkEvent(LinkInfo(ipv4 = ipv4))
            }

            override fun onLost(network: Network) {
                handleLinkEvent(LinkInfo(ipv4 = null))
            }
        }
        runCatching {
            cm.registerNetworkCallback(request, cb)
        }.onSuccess {
            networkCallback = cb
        }.onFailure {
            Log.w(TAG, "registerNetworkCallback failed: $it")
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    /**
     * Bridge one [LinkInfo] through the pure-logic [NetworkRebinder] and
     * take the resulting action. Keeps the Android-specific callback body
     * thin so the interesting state transitions are testable in T25.
     */
    private fun handleLinkEvent(info: LinkInfo) {
        when (val intent = rebinder.onLink(info)) {
            is RebindIntent.StayPut -> Unit
            is RebindIntent.Lost -> {
                // Don't stop Ktor here — the OS already invalidated the
                // listening socket on its own. We'll rebuild it on the next
                // Rebind (which the rebinder fires when an IP comes back,
                // even if it's the same one we used to have).
                ServiceLocator.session.updateNetworkStatus(NetworkStatus.Lost)
            }
            is RebindIntent.Rebind -> rebindTo(intent.newIp)
        }
    }

    /**
     * Stop the current Ktor engine and bring a new one up bound to [newIp].
     * Preserves currentMode / pinAuth so in-flight session identity (PIN,
     * sessionId, exportMode snapshot) survives the swap — only the socket
     * re-opens on the new interface.
     */
    private fun rebindTo(newIp: String) {
        val mode = currentMode ?: return
        val auth = pinAuth ?: return
        ServiceLocator.session.updateNetworkStatus(NetworkStatus.Switching)
        runCatching { ktor?.stop() }
        val replacement = when (mode) {
            ServiceMode.Transfer -> buildTransferKtor(newIp, auth)
            ServiceMode.Export -> buildExportKtor(newIp, auth)
        }
        val port = runCatching { replacement.start() }.getOrNull()
        if (port == null) {
            Log.e(TAG, "rebind to $newIp failed — no available port")
            ServiceLocator.session.updateNetworkStatus(NetworkStatus.Lost)
            ktor = null
            return
        }
        ktor = replacement
        currentHostIp = newIp
        ServiceLocator.session.updateBoundPort(port)
        // Transfer-mode ServingViewModel keys its UI off _running (ip/port);
        // refresh it so the screen shows the new URL too.
        if (mode == ServiceMode.Transfer) {
            val prev = _running.value
            if (prev != null) {
                _running.value = prev.copy(ip = newIp, port = port)
            }
        }
        // 通知栏 URL 也要随 rebind 刷新；不刷的话用户看到的还是旧 IP。
        val notif = when (mode) {
            ServiceMode.Transfer -> NotificationHelper.build(
                context = this,
                title = "Flikky 服务运行中",
                text = "URL  http://$newIp:$port\nPIN  打开 APP 查看",
            )
            ServiceMode.Export -> {
                val armed = ServiceLocator.session.exportMode.value as? ExportMode.Armed
                NotificationHelper.build(
                    context = this,
                    title = ExportNotificationText.TITLE,
                    text = armed?.let { ExportNotificationText.body(it.snapshot) } ?: "正在提供导出",
                )
            }
        }
        val notifId = if (mode == ServiceMode.Export) EXPORT_NOTIFICATION_ID else NotificationHelper.NOTIFICATION_ID
        runCatching {
            (getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager)?.notify(notifId, notif)
        }
        ServiceLocator.session.updateNetworkStatus(
            NetworkStatus.Switched(newUrl = "http://$newIp:$port")
        )
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        if (ktor != null || currentMode != null) {
            stopActiveServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        ServiceLocator.reset()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Stable phone-side identity for recall authorization (D31).
     * `Settings.Secure.ANDROID_ID` is a public API and yields a per-app, per-user
     * 64-bit hex string that survives reinstalls of the same user profile but
     * resets on factory reset. Falls back to "unknown" if the system returns
     * null (rare; happens on some emulators before first boot completes).
     */
    private fun phoneSenderId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return "phone-${androidId ?: "unknown"}"
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
