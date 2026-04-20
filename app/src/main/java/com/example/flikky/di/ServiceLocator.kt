package com.example.flikky.di

import android.content.Context
import com.example.flikky.data.SessionFileStore
import com.example.flikky.network.NetworkInfo
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import java.io.File

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

    fun init(app: Context) {
        appContext = app.applicationContext
        session = SessionState(nowMs = System::currentTimeMillis)
        stats = TransferStats(nowMs = System::currentTimeMillis)
        fileStore = SessionFileStore(filesDir = app.filesDir)
        networkInfo = NetworkInfo(appContext)
    }

    fun context(): Context = appContext

    fun reset() {
        session = SessionState(nowMs = System::currentTimeMillis)
        stats = TransferStats(nowMs = System::currentTimeMillis)
    }
}
