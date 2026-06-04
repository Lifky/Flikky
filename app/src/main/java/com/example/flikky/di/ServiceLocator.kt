package com.example.flikky.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.network.NetworkInfo
import com.example.flikky.service.TransferController
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private val Context.settingsDataStore by preferencesDataStore(name = "flikky_settings")

object ServiceLocator {
    private lateinit var appContext: Context
    lateinit var session: SessionState
        private set
    lateinit var stats: TransferStats
        private set
    lateinit var fileStore: SessionFileStore
        private set
    lateinit var networkInfo: NetworkInfo
        private set
    lateinit var database: FlikkyDatabase
        private set
    lateinit var repository: SessionRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    /**
     * 当前运行中的 [TransferController] 引用，由 [com.example.flikky.service.TransferService]
     * 在 startTransfer 设置、stopActiveServer 清空。v1.3 撤回流程下，HistoryViewModel
     * 不通过 service binding 而是直接从这里拿——撤回操作是单次调用且只读，
     * binding 完整生命周期管理开销过大。
     *
     * 跨 Wi-Fi rebind 时 controller 实例不变（service 内部 field 复用），所以这里
     * 不需要 lambda 间接访问（CLAUDE.md 跨-rebind 规范针对的是 KtorServer 内部
     * 成员，TransferController 本身跨 rebind 同一实例）。
     */
    @Volatile var currentController: TransferController? = null

    /**
     * v1.3 对端撤回通知渠道。TransferService 在 onRecallMessage 桥接
     * 成功撤回时 emit；ServingViewModel 监听后弹 snackbar。
     * extraBufferCapacity 避免 tryEmit 在无 collector 时丢弃。
     */
    private val _recallNotifications = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val recallNotifications: SharedFlow<String> = _recallNotifications.asSharedFlow()
    fun notifyRecall(message: String) { _recallNotifications.tryEmit(message) }

    fun init(app: Context) {
        appContext = app.applicationContext
        session = SessionState(nowMs = System::currentTimeMillis)
        stats = TransferStats(nowMs = System::currentTimeMillis)
        fileStore = SessionFileStore(filesDir = appContext.filesDir)
        networkInfo = NetworkInfo(appContext)
        database = FlikkyDatabase.build(appContext)
        repository = SessionRepository(
            sessionDao = database.sessionDao(),
            messageDao = database.messageDao(),
            fileStore = fileStore,
            now = System::currentTimeMillis,
        )
        settingsRepository = SettingsRepository(appContext.settingsDataStore)
    }

    fun context(): Context = appContext

    /**
     * 复用同一 SessionState / TransferStats 实例，仅清零内部状态。
     *
     * v1.1 时这里替换实例，但 HomeViewModel / ExportingViewModel 等都在构造
     * 时缓存了 ServiceLocator.session 引用——reset 后它们就指向"死实例"，
     * 导致 v1.2 出现：HomeViewModel.armExport 写旧实例，TransferService 读
     * 新实例发现 Idle 直接 stopSelf 没调 startForeground → 5 秒后崩。
     *
     * 同样原因导致停服后 HomeViewModel.isTransferOrExportRunning 仍误报
     * "正在运行"（旧实例的 currentSessionId 残留）。
     */
    /**
     * 注意：不在这里调 session.clearExport()。当服务因 zip 发完而 stopSelf 时，
     * exportMode 必须保持 Done(session) 让 ExportingScreen 渲染 "保留 / 删除"
     * 二择屏；只有用户 acknowledge（acknowledge() 或新一轮 startExport 兜底）
     * 才清。`isTransferOrExportRunning()` 把 Done 视为已结束，下次启动不被阻塞。
     */
    fun reset() {
        session.reset()
        stats.reset()
        currentController = null
    }
}
