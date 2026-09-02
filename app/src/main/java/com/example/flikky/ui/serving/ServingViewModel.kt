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
import com.example.flikky.R
import com.example.flikky.ui.serving.storage.LocalStorageBrowser
import com.example.flikky.ui.serving.storage.LocalStorageState
import com.example.flikky.ui.serving.storage.StorageChunk
import com.example.flikky.ui.serving.storage.StorageNavigation
import com.example.flikky.ui.serving.storage.StorageSelectionSummary
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.di.ServiceLocator
import com.example.flikky.service.TransferController
import com.example.flikky.service.TransferService
import com.example.flikky.session.Message
import com.example.flikky.session.NetworkStatus
import com.example.flikky.session.Origin
import com.example.flikky.session.PendingMessageDeletes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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
    val requirePin: Boolean = true,
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
                    requirePin = r?.requirePin ?: true,
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
        ServiceLocator.recallNotifications.onEach {
            _events.trySend(ctx.getString(R.string.serving_peer_recalled))
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
                requirePin = _ui.value.requirePin,
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

    // ── v1.20.0 本机存储浏览（会话页「文件」tab）
    //
    // 路径与选择集合放这里而不是 `remember`：发送要用它（controller 在 ViewModel 手里），
    // 且它必须跨配置变更存活——转屏后选择集合清空是明显的体验缺陷。
    // 纯逻辑全在 LocalStorageBrowser，这里只持有状态并转发。
    private val storageBrowser by lazy {
        LocalStorageBrowser(android.os.Environment.getExternalStorageDirectory())
    }
    private val _storageState = MutableStateFlow(LocalStorageState(path = "", entries = emptyList()))
    val storageState: StateFlow<LocalStorageState> = _storageState

    /**
     * 正在跑的列举任务。**每次导航都先取消上一个**。
     *
     * 少了这一步，大目录的旧结果会在用户已经离开之后才返回并落地，
     * 把界面拽回他刚刚离开的目录——装机验收里「点了没反应，又点别的，
     * 过一会儿自己跳回刚才那个大文件夹」就是这个（v1.20.0 首版是同步调用，
     * 症状是主线程冻住 + 点击排队，同一个根因的另一种表现）。
     */
    private var storageJob: Job? = null

    /** 最后一次**成功**的列举结果。失败时退回它，见 [StorageNavigation.settle]。 */
    private var lastGoodStorage = LocalStorageState(path = "", entries = emptyList())

    /**
     * 重新读当前目录。授权完成后、以及回到前台时调用。
     *
     * 选择集合**跨刷新保留**：用户可能在别处删了文件，但那不是清空整个选择的理由——
     * 真正发送时 `resolveExisting` 会跳过不存在的并报数。
     */
    fun refreshStorage() {
        openStorageDir(_storageState.value.path)
    }

    /**
     * 进入某个目录。
     *
     * 三件事一起做（成熟文件管理器的通用做法）：路径**立即**前进并置 loading（点击有反应）、
     * 列举在 [Dispatchers.IO] 上跑（不冻主线程）、发起前**取消上一次**（旧结果绝不后到）。
     * 迁移规则见 [StorageNavigation]，那三条容易写错的分支在 `test/` 里穷举。
     */
    fun openStorageDir(relative: String) {
        storageJob?.cancel()
        _storageState.value = StorageNavigation.begin(_storageState.value, relative)
        storageJob = viewModelScope.launch {
            var sawHead = false
            storageBrowser.listStream(relative)
                // 枚举与每批的 childCount 都是阻塞 IO，整条流都在 IO 上跑。
                // 状态写回发生在 collect 里，也就是 viewModelScope 的 Main 上——
                // 这正是要的：UI 状态只在主线程改。
                .flowOn(Dispatchers.IO)
                .collect { chunk ->
                    when (chunk) {
                        is StorageChunk.Head -> {
                            sawHead = true
                            _storageState.value =
                                StorageNavigation.head(_storageState.value, chunk.path)
                        }
                        is StorageChunk.Batch -> {
                            _storageState.value =
                                StorageNavigation.append(_storageState.value, chunk.entries)
                        }
                        StorageChunk.Done -> {
                            val finished = StorageNavigation.complete(_storageState.value)
                            _storageState.value = finished
                            // 只有完整走完的列举才配当「最后一次成功」——被取消的流
                            // 走不到这里，于是失败退回时不会退到一份残缺列表上。
                            lastGoodStorage = finished
                        }
                        StorageChunk.Failed -> {
                            _storageState.value = StorageNavigation.settle(
                                _storageState.value,
                                listed = null,
                                fallback = lastGoodStorage,
                            )
                        }
                    }
                }
            // 流被上游正常结束但没给过 Head（不该发生），也要把进度条收掉，
            // 否则进度条会一直转着而列表空空。
            if (!sawHead && _storageState.value.loading) {
                _storageState.value = StorageNavigation.complete(_storageState.value)
            }
        }
    }

    /** 返回上一级。根目录无上一级时什么都不做——由 UI 侧的 `storageCanGoUp` 先拦。 */
    fun storageGoUp() {
        val parent = storageBrowser.parentOf(_storageState.value.path) ?: return
        openStorageDir(parent)
    }

    fun toggleStorageSelection(relativePath: String) {
        val current = _storageState.value
        _storageState.value = current.copy(
            selected = storageBrowser.toggle(current.selected, relativePath),
        )
    }

    fun clearStorageSelection() {
        _storageState.value = _storageState.value.copy(selected = emptySet())
    }

    /**
     * 操作条那一行的数据。与 [sendStorageSelection] 实际发出的量同源，见 selectionSummary 的 KDoc。
     *
     * **必须是派生 flow，不能做成 composable 里直接调的函数**：`selectionSummary` 要 stat 每个
     * 选中文件（canonicalFile + length），而 `ServingScreen` 每秒都会因 `uptimeSeconds` 重组一次
     * ——那就变成主线程上每秒 stat 一遍所有选中文件。这里按选择集合去重，只在真正变化时重算。
     */
    val storageSelectionSummary: StateFlow<StorageSelectionSummary> = _storageState
        .map { it.selected }
        .distinctUntilChanged()
        .map { storageBrowser.selectionSummary(it) }
        // selectionSummary 要 stat 每个选中文件。viewModelScope 的默认上下文是 Main，
        // 不加这行就是在主线程上做 I/O —— 选了 50 个文件时用户能感觉到卡一下。
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            StorageSelectionSummary(0, 0L, 0),
        )

    /**
     * 把选中的文件逐个发进当前会话。
     *
     * 走 [TransferController.offerStoredFile] —— 收藏发送与文件总览快发用的是同一个入口。
     * 另开一条发送路径迟早会在状态机或落盘路径上与它们不一致。
     *
     * 发完**清空选择并留在文件 tab**：跳回会话 tab 会打断「继续挑下一批」的动线，
     * 而消息已经在会话里、用户想看随时可以自己切过去。
     * 跳过数如实报出（与 v1.17.1 收藏批量操作一致），不静默丢弃。
     */
    fun sendStorageSelection() {
        val selection = _storageState.value.selected
        if (selection.isEmpty()) return
        viewModelScope.launch {
            // resolveExisting 逐个 canonicalFile + isFile，同样是 I/O。
            val (files, skipped) = withContext(Dispatchers.IO) {
                storageBrowser.resolveExisting(selection)
            }
            var sent = 0
            for (f in files) {
                val mime = java.net.URLConnection.guessContentTypeFromName(f.name)
                    ?: "application/octet-stream"
                val ok = controller?.offerStoredFile(f, f.name, f.length(), mime) == true
                if (ok) sent++
            }
            clearStorageSelection()
            val app = getApplication<Application>()
            _events.trySend(
                when {
                    sent == 0 -> app.getString(R.string.serving_storage_send_none)
                    skipped > 0 -> app.getString(R.string.serving_storage_sent_skipped, sent, skipped)
                    else -> app.getString(R.string.serving_storage_sent, sent)
                },
            )
        }
    }

    fun sendStoredFile(row: FileOverviewRow) {
        val source = ServiceLocator.fileStore.messageFile(row.sessionId, row.fileId)
        val name = row.fileName ?: "unnamed"
        val mime = row.fileMime ?: "application/octet-stream"
        viewModelScope.launch {
            val sent = controller?.offerStoredFile(
                source,
                name,
                row.fileSize ?: source.length(),
                mime,
            ) == true
            if (!sent) {
                _events.trySend(
                    getApplication<Application>().getString(R.string.files_quick_missing)
                )
            }
        }
    }

    // 进行中会话的快捷设置：会话期间「设置」tab 被锁，用户改不了这些常调项。
    // 写的是与设置页同一份 settings —— 一处改动，App 气泡 + 已连浏览器气泡 + 设置页全同步。
    fun setBubbleCornerRadius(dp: Int) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setBubbleCornerRadius(dp) }
    }

    fun setAvatarGrouping(mode: AvatarGroupingMode) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setAvatarGrouping(mode) }
    }

    fun setDarkMode(mode: DarkMode) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setDarkMode(mode) }
    }

    // 以下这些也都是「双端同步」项——都进了 PeerInfoDto，改动经 settings_changed
    // 广播给已连接的浏览器。会话期间设置页锁着，不在快捷设置里就等于整场会话
    // 都无法调整，双端同步这件事也就发挥不出来。
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setThemeMode(mode) }
    }

    fun setPresetTheme(theme: PresetTheme) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setPresetTheme(theme) }
    }

    fun setCustomThemeSeed(argb: Long) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setCustomThemeSeed(argb) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setAmoled(enabled) }
    }

    fun setDeviceName(name: String) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setDeviceName(name) }
    }

    fun setPhoneAvatarKey(key: String) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setPhoneAvatarKey(key) }
    }

    fun setBackground(background: BackgroundSetting) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setBackground(background) }
    }

    fun setSessionTimestampEnabled(enabled: Boolean) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setSessionTimestampEnabled(enabled) }
    }

    fun setMessageActionStyle(style: MessageActionStyle) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setMessageActionStyle(style) }
    }

    fun setRecallBeta(enabled: Boolean) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setRecallBeta(enabled) }
    }

    fun setAllowPeerRecall(enabled: Boolean) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setAllowPeerRecall(enabled) }
    }

    fun setFavoriteBeta(enabled: Boolean) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setFavoriteBeta(enabled) }
    }

    fun setStorageBrowsingEnabled(enabled: Boolean) {
        viewModelScope.launch { ServiceLocator.settingsRepository.setStorageBrowsingEnabled(enabled) }
    }

    fun setPeerAvatarKey(key: String) {
        viewModelScope.launch {
            val ctrl = controller
            if (ctrl != null) {
                ctrl.setPeerAvatarKey(key)
            } else {
                ServiceLocator.session.setPeerAvatarKey(key)
            }
            // Persist last: session is already updated above, so the settings collector
            // in TransferService sees no diff and does not re-broadcast.
            ServiceLocator.settingsRepository.setBrowserAvatarKey(key)
        }
    }

    private fun sendFavoriteFile(favorite: FavoriteEntity) {
        val depotId = favorite.fileId ?: return
        val source = ServiceLocator.favoriteFileStore.resolve(depotId)
        val name = favorite.fileName ?: "unnamed"
        val size = favorite.fileSize ?: source.length()
        val mime = favorite.fileMime ?: "application/octet-stream"
        viewModelScope.launch {
            val sent = controller?.offerStoredFile(source, name, size, mime) == true
            if (!sent) {
                _events.trySend(
                    getApplication<Application>().getString(R.string.serving_favorite_file_missing)
                )
            }
        }
    }

    /**
     * v1.3 D26 修订：撤回入口（仅 ServingScreen 提供）。
     * controller 为 null 不该发生（ServingScreen 只在服务运行中可见），但加防御。
     */
    fun recallMessage(messageId: Long) {
        val ctrl = controller ?: run {
            _events.trySend(
                getApplication<Application>().getString(R.string.serving_recall_service_stopped)
            )
            return
        }
        viewModelScope.launch {
            when (ctrl.recallMessage(messageId)) {
                is SessionRepository.RecallOutcome.Success ->
                    _events.trySend(
                        getApplication<Application>().getString(R.string.serving_recall_success)
                    )
                is SessionRepository.RecallOutcome.NotFound ->
                    _events.trySend(
                        getApplication<Application>().getString(R.string.serving_recall_success)
                    ) // 真删后再撤等价 idempotent 成功
                is SessionRepository.RecallOutcome.Denied -> // unreachable since v1.5.0 — recallMessage never returns Denied
                    _events.trySend(
                        getApplication<Application>().getString(R.string.serving_recall_denied)
                    )
            }
        }
    }

    /**
     * 打开浏览器上传的文件。
     * 只对 origin=BROWSER & status=COMPLETED 的 Message.File 有效：
     * 文件路径由 SessionFileStore.messageFile 解析（唯一事实源），通过 FileProvider 以 msg.name 暴露给第三方 APP。
     * 手机自己发送的文件（origin=PHONE）不走这条路径——那是用户从手机选出去的，自己已有。
     */
    fun openFile(msg: Message.File) {
        if (msg.status != Message.File.Status.COMPLETED) return
        val ctx = getApplication<Application>()
        val sid = ServiceLocator.session.snapshot.value.currentSessionId ?: run {
            Toast.makeText(ctx, R.string.serving_no_session_context, Toast.LENGTH_SHORT).show()
            return
        }
        val f = ServiceLocator.fileStore.messageFile(sid, msg.fileId)
        if (!f.exists()) {
            Toast.makeText(ctx, R.string.serving_uploaded_file_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${ctx.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(ctx, authority, f, msg.name)
        } catch (e: IllegalArgumentException) {
            Toast.makeText(ctx, R.string.file_provider_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, msg.mime.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.file_open_chooser)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.file_no_handler, Toast.LENGTH_SHORT).show()
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
     * 本地软删除：从内存消息列表移除消息，快照入台账用于撤销。
     * 不广播浏览器，不写 DB（提交由 [commitDelete] 或 [onCleared] 兜底完成）。
     */
    private val pendingDeletes = PendingMessageDeletes()

    fun deleteLocalWithUndo(id: Long) {
        pendingDeletes.stage(
            id,
            ServiceLocator.session.snapshot.value.messages.firstOrNull { it.id == id },
        )
        ServiceLocator.session.removeMessage(id)
    }

    /**
     * 撤销软删除：将消息恢复到原始位置。
     * addMessage 按 timestamp 插入，消息会回到列表中时间顺序正确的位置。
     * LazyColumn key={it.id} 保证动画平滑。
     */
    fun undoDelete() {
        pendingDeletes.undoLatest()?.let { ServiceLocator.session.addMessage(it) }
    }

    /**
     * 提交软删除到 DB。若消息是文件，repository.deleteMessage 会同时删盘。
     * 走 appScope 而非 viewModelScope：提交一旦决定就必须执行到底，不随页面销毁取消。
     * runCatching 保证 DB 写失败不会崩溃。
     */
    fun commitDelete(id: Long) {
        pendingDeletes.commit(id)
        ServiceLocator.appScope.launch {
            runCatching { ServiceLocator.repository.deleteMessage(id) }
        }
    }

    /** "我知道了" on the NetworkStatusBanner — fold Switched back to Ok. */
    fun acknowledgeNetworkSwitch() {
        ServiceLocator.session.acknowledgeNetworkSwitch()
    }

    override fun onCleared() {
        // Snackbar 撤销窗口的等待协程随 Screen composition 取消，超时提交可能永远
        // 不来；撤销窗口内离开页面 = 视同确认删除，这里把台账里剩余的全部落库。
        // 修复 v1.16 验收缺陷：删除不落库 → History 复活 + 磁盘泄漏。
        val ids = pendingDeletes.drainIds()
        if (ids.isNotEmpty()) {
            ServiceLocator.appScope.launch {
                ids.forEach { runCatching { ServiceLocator.repository.deleteMessage(it) } }
            }
        }
        runCatching { getApplication<Application>().unbindService(conn) }
    }
}
