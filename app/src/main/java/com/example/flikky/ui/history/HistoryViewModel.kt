package com.example.flikky.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.di.ServiceLocator
import com.example.flikky.session.Message
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class HistoryViewModel(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
    val sessionId: Long,
) : AndroidViewModel(app) {

    val session: Flow<SessionEntity?> = repository.observeSession(sessionId)
    val messages: Flow<List<Message>> = repository.observeMessages(sessionId)

    fun rename(newName: String): Job =
        viewModelScope.launch { repository.rename(sessionId, newName) }

    fun setPinned(pinned: Boolean): Job =
        viewModelScope.launch { repository.setPinned(sessionId, pinned) }

    fun delete(): Job =
        viewModelScope.launch { repository.deleteSession(sessionId) }

    companion object {
        fun factory(app: Application, sessionId: Long) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return HistoryViewModel(app, sessionId = sessionId) as T
                }
            }
    }
}
