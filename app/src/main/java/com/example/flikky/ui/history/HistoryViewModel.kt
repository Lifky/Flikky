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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
    val sessionId: Long,
) : AndroidViewModel(app) {

    val session: Flow<SessionEntity?> = repository.observeSession(sessionId)

    /** IDs that have been soft-deleted but not yet committed to DB. */
    private val _pendingDeleteIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Messages with pending-deleted IDs filtered out.
     *
     * Backed by Room's observable query combined with a local exclusion set so
     * the item disappears from the UI immediately on delete, before the DB
     * write completes. Exposed as StateFlow so [collectAsState] works without
     * an initial value argument in the screen.
     */
    val messages: StateFlow<List<Message>> =
        combine(repository.observeMessages(sessionId), _pendingDeleteIds) { msgs, excluded ->
            msgs.filterNot { it.id in excluded }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Soft-delete + undo ──────────────────────────────────────────────────

    /**
     * The message captured just before soft-delete, used for undo.
     * Holds the most recent target; cleared on commit or undo.
     */
    private var pendingDeleteMsg: Message? = null

    /**
     * Soft-delete: hide the message immediately in the UI by adding its ID to
     * the exclusion set. Does NOT write to DB yet. Captures the message for undo.
     */
    fun deleteLocalWithUndo(id: Long) {
        pendingDeleteMsg = messages.value.firstOrNull { it.id == id }
        _pendingDeleteIds.value = _pendingDeleteIds.value + id
    }

    /**
     * Undo: remove the ID from the exclusion set so the message reappears.
     * The Room flow still has it — no DB insert needed.
     */
    fun undoDelete() {
        val msg = pendingDeleteMsg ?: return
        _pendingDeleteIds.value = _pendingDeleteIds.value - msg.id
        pendingDeleteMsg = null
    }

    /**
     * Commit: remove the exclusion (no longer needed once DB row is gone) and
     * delete from DB. Room flow re-emits without the row.
     */
    fun commitDelete(id: Long) {
        pendingDeleteMsg = null
        _pendingDeleteIds.value = _pendingDeleteIds.value - id
        viewModelScope.launch { runCatching { repository.deleteMessage(id) } }
    }

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
     *
     * v1.5 M8: prefer deleteLocalWithUndo / commitDelete for undo support.
     * This entry point is kept for backward compatibility.
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
