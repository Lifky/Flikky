package com.example.flikky.di

import android.content.Context
import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.network.NetworkInfo
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats

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
    fun reset() {
        session.reset()
        session.clearExport()
        stats.reset()
    }
}
