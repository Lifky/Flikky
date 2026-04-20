package com.example.flikky

import android.app.Application
import com.example.flikky.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlikkyApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Crash recovery for unfinished sessions left over from a killed process.
        // Non-blocking; failures are logged-only and don't impact app usability.
        scope.launch {
            runCatching { ServiceLocator.repository.finalizeOrphans() }
        }
    }
}
