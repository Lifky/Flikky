package com.example.flikky.ui.home

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.di.ServiceLocator
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSession
import com.example.flikky.service.TransferService
import com.example.flikky.session.SessionState
import com.example.flikky.util.IdGen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel @JvmOverloads constructor(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
    private val sessionState: SessionState = ServiceLocator.session,
    private val pinGenerator: () -> String = { IdGen.newPin() },
    private val now: () -> Long = { System.currentTimeMillis() },
) : AndroidViewModel(app) {

    val sessions: Flow<List<SessionEntity>> = repository.observeSessions()

    private val _selection = MutableStateFlow<Set<Long>?>(null)
    val selection: StateFlow<Set<Long>?> = _selection.asStateFlow()

    val selecting: StateFlow<Boolean> = _selection
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    // --- Selection mode -----------------------------------------------------

    fun enterSelecting() {
        if (_selection.value == null) _selection.value = emptySet()
    }

    fun exitSelecting() {
        _selection.value = null
    }

    fun toggleSelection(sessionId: Long) {
        val current = _selection.value ?: emptySet()
        _selection.value = if (sessionId in current) current - sessionId else current + sessionId
    }

    fun selectAll(ids: List<Long>) {
        _selection.value = ids.toSet()
    }

    fun clearSelection() {
        _selection.value = null
    }

    // --- Export kickoff -----------------------------------------------------

    sealed class ExportStartResult {
        object Success : ExportStartResult()
        object TransferRunning : ExportStartResult()
        object EmptySelection : ExportStartResult()
        object NoValidSessions : ExportStartResult()
    }

    /**
     * Kick off a batch export.
     *
     * 1. Require selection to be non-null and non-empty.
     * 2. Refuse if transfer or another export is active (both collapse into TransferRunning;
     *    error copy "please stop the running service" works for both cases).
     * 3. Build an [com.example.flikky.export.ExportSnapshot] via [SessionRepository.exportSnapshot];
     *    if the repo filtered everything out (all in-progress / unknown ids), bail with NoValidSessions.
     * 4. Arm [SessionState] with a fresh PIN so the browser can authenticate.
     * 5. Start [TransferService] with [TransferService.ACTION_EXPORT].
     * 6. Clear selection on success so a return-to-home after export doesn't leave the UI
     *    stuck in selecting mode.
     */
    suspend fun startExport(): ExportStartResult {
        val sel = _selection.value
        if (sel.isNullOrEmpty()) return ExportStartResult.EmptySelection
        if (isTransferOrExportRunning()) return ExportStartResult.TransferRunning

        val snapshot = repository.exportSnapshot(sel.toList())
        if (snapshot.sessions.isEmpty()) return ExportStartResult.NoValidSessions

        val exportSession = ExportSession(
            sessionIds = snapshot.sessions.map { it.id },
            pin = pinGenerator(),
            createdAt = now(),
        )
        sessionState.armExport(exportSession, snapshot)

        val ctx = getApplication<Application>()
        ctx.startForegroundService(
            Intent(ctx, TransferService::class.java).apply {
                action = TransferService.ACTION_EXPORT
            },
        )
        // Drop out of selecting mode — the exporting screen owns the UX from here.
        _selection.value = null
        return ExportStartResult.Success
    }

    private fun isTransferOrExportRunning(): Boolean {
        // `serviceStartedAt` is seeded to nowMs() at SessionState construction/reset so the
        // raw timestamp isn't a clean "transfer running" signal. The signal that a transfer
        // session is in flight is `currentSessionId != null` — TransferService.startTransfer
        // calls SessionState.startNew(sid) and onDestroy calls ServiceLocator.reset() which
        // replaces the SessionState with a fresh one whose currentSessionId is null.
        val snap = sessionState.snapshot.value
        if (snap.currentSessionId != null) return true
        // Likewise block when an export is already armed/sending/done (not yet cleared).
        return sessionState.exportMode.value !is ExportMode.Idle
    }

    /** Batch delete for the post-export "delete local copies" flow. */
    suspend fun deleteSessions(ids: List<Long>) {
        ids.forEach { repository.deleteSession(it) }
    }
}
