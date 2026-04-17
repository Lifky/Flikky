package com.example.flikky.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.example.flikky.service.TransferService

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    fun startService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TransferService::class.java).apply {
            action = TransferService.ACTION_START
        }
        ctx.startForegroundService(intent)
    }
}
