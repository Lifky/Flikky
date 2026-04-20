package com.example.flikky.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.di.ServiceLocator
import com.example.flikky.service.TransferService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HomeViewModel(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
) : AndroidViewModel(app) {

    val sessions: Flow<List<SessionEntity>> = repository.observeSessions()

    fun startService() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, TransferService::class.java).apply {
            action = TransferService.ACTION_START
        }
        ctx.startForegroundService(intent)
    }

    fun rename(sessionId: Long, newName: String): Job =
        viewModelScope.launch { repository.rename(sessionId, newName) }

    fun setPinned(sessionId: Long, pinned: Boolean): Job =
        viewModelScope.launch { repository.setPinned(sessionId, pinned) }

    fun deleteSession(sessionId: Long): Job =
        viewModelScope.launch { repository.deleteSession(sessionId) }
}
