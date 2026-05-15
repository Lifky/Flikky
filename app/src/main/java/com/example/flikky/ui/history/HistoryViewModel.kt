package com.example.flikky.ui.history

import android.app.Application
import android.provider.Settings
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
     * v1.3 T21 撤回入口。两条路径：
     *  - 服务运行中（[ServiceLocator.currentController] 不为 null）→ 走 controller，
     *    它会同时调 repository 写 DB + broadcast WS 让浏览器同步。
     *  - 服务已结束（看历史会话撤回）→ 直接调 repository，仅本地 DB 写入，无广播。
     *    历史撤回也有意义：让以后再开服务时浏览器 loadHistory 不显示这条。
     *
     * 鉴权 senderId 与 TransferService 使用相同的 D31 规则：phone-{ANDROID_ID}。
     * 同一台设备任何时候都能撤自己的历史消息。
     *
     * UI 不需要 outcome —— observeMessages flow 会在 DB 触发器写 recalledAt 后
     * 重新发 list，HistoryScreen 看到的就是已撤回的消息（recalledAt != null）。
     */
    fun recallMessage(messageId: Long): Job = viewModelScope.launch {
        val controller = ServiceLocator.currentController
        if (controller != null) {
            controller.recallMessage(messageId)
        } else {
            repository.recallMessage(messageId, phoneSenderId())
        }
    }

    private fun phoneSenderId(): String {
        val ctx = getApplication<Application>()
        val androidId = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return "phone-${androidId ?: "unknown"}"
    }

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
