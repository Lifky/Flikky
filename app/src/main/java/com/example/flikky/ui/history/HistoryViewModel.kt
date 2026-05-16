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

    /**
     * v1.3 D26 修订：History 单条消息删除。撤回入口已搬到 ServingScreen
     * （只有进行中服务才能撤回），History 只提供本地清理能力——不鉴权、不广播。
     * 文件消息同样删盘 + 更新会话聚合。
     */
    fun deleteMessage(messageId: Long): Job =
        viewModelScope.launch { repository.deleteMessage(messageId) }

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
