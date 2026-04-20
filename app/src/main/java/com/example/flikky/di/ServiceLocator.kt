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

    fun reset() {
        session = SessionState(nowMs = System::currentTimeMillis)
        stats = TransferStats(nowMs = System::currentTimeMillis)
    }
}
