package com.example.flikky.ui.home

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.example.flikky.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.data.settings.SortMode
import com.example.flikky.di.ServiceLocator
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.service.TransferService
import com.example.flikky.session.SessionState
import com.example.flikky.ui.exporting.LocalExportWriter
import com.example.flikky.util.IdGen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel @JvmOverloads constructor(
    app: Application,
    private val repository: SessionRepository = ServiceLocator.repository,
    private val sessionState: SessionState = ServiceLocator.session,
    private val pinGenerator: () -> String = { IdGen.newPin() },
    private val now: () -> Long = { System.currentTimeMillis() },
    private val settingsRepository: SettingsRepository = ServiceLocator.settingsRepository,
    private val localExportWriter: suspend (Uri, ExportSnapshot) -> Unit = { uri, snapshot ->
        LocalExportWriter.write(
            context = app,
            uri = uri,
            snapshot = snapshot,
            sessionFileResolver = { sessionId, fileId ->
                ServiceLocator.fileStore.fileDir(sessionId).resolve(fileId)
                    .takeIf { it.exists() && it.isFile }
            },
            favoriteFileResolver = { fileId ->
                ServiceLocator.favoriteFileStore.resolve(fileId)
                    .takeIf { it.exists() && it.isFile }
            },
        )
    },
) : AndroidViewModel(app) {

    val sessions: Flow<List<SessionEntity>> = repository.observeSessions()
    val groups: Flow<List<GroupEntity>> = repository.observeGroups()
    val activeGroupId: Flow<Long?> = settingsRepository.settings.map { it.activeGroupId }
    val searchEnabled: Flow<Boolean> = settingsRepository.settings.map { it.historyRetainLimit != 0 }

    val homeItems: Flow<List<HomeListItem>> = combine(
        repository.observeSessions(),
        settingsRepository.settings,
    ) { sessions, settings ->
        HomeListBuilder.build(
            sessions = HomeListBuilder.filterByGroup(sessions, settings.activeGroupId),
            sort = SortMode.TIME,
            group = GroupMode.DATE,
            today = LocalDate.now(),
            zone = ZoneId.systemDefault(),
        )
    }

    val sortMode: Flow<SortMode> = settingsRepository.settings.map { it.sortMode }
    val groupMode: Flow<GroupMode> = settingsRepository.settings.map { it.groupMode }

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

    /** 主页直接停止当前进行中传输（点击会话项右侧的停止按钮触发）。 */
    fun stopService() {
        val ctx = getApplication<Application>()
        ctx.startService(Intent(ctx, TransferService::class.java).apply {
            action = TransferService.ACTION_STOP
        })
    }

    fun rename(sessionId: Long, newName: String): Job =
        viewModelScope.launch { repository.rename(sessionId, newName) }

    fun setPinned(sessionId: Long, pinned: Boolean): Job =
        viewModelScope.launch { repository.setPinned(sessionId, pinned) }

    fun deleteSession(sessionId: Long): Job =
        viewModelScope.launch { repository.deleteSession(sessionId) }

    fun setSortMode(value: SortMode): Job =
        viewModelScope.launch { settingsRepository.setSortMode(value) }

    fun setGroupMode(value: GroupMode): Job =
        viewModelScope.launch { settingsRepository.setGroupMode(value) }

    fun setActiveGroup(id: Long?): Job =
        viewModelScope.launch { settingsRepository.setActiveGroup(id) }

    fun createGroup(name: String): Job =
        viewModelScope.launch {
            val validName = normalizeGroupName(name) ?: return@launch
            val id = repository.createGroup(validName)
            settingsRepository.setActiveGroup(id)
        }

    fun renameGroup(id: Long, name: String): Job =
        viewModelScope.launch {
            val validName = normalizeGroupName(name) ?: return@launch
            repository.renameGroup(id, validName)
        }

    suspend fun deleteGroupWithUndo(id: Long): Pair<GroupEntity, List<Long>>? {
        val active = settingsRepository.settings.first().activeGroupId
        val token = repository.deleteGroup(id) ?: return null
        if (active == id) settingsRepository.setActiveGroup(null)
        return token
    }

    suspend fun restoreGroup(group: GroupEntity, members: List<Long>) {
        val restoredId = repository.restoreGroup(group, members)
        settingsRepository.setActiveGroup(restoredId)
    }

    fun reorderGroups(orderedIds: List<Long>): Job =
        viewModelScope.launch { repository.reorderGroups(orderedIds) }

    private fun normalizeGroupName(name: String): String? =
        name.trim().take(12).ifEmpty { null }

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

    /** 对当前选中的所有会话设置置顶态（pinned 由 UI 先算"是否全部已置顶"后传入），完成后退出多选。 */
    suspend fun pinSelected(pinned: Boolean) {
        val ids = _selection.value ?: return
        ids.forEach { repository.setPinned(it, pinned) }
        _selection.value = null
    }

    /** 批量删除当前选中的所有会话，完成后退出多选。 */
    suspend fun deleteSelected() {
        val ids = _selection.value ?: return
        ids.forEach { repository.deleteSession(it) }
        _selection.value = null
    }

    /** 重命名当前唯一选中的会话（UI 仅在恰好选中 1 条时调用），完成后退出多选；非单选则 no-op 且不退出。 */
    suspend fun renameSelected(newName: String) {
        val id = _selection.value?.singleOrNull() ?: return
        repository.rename(id, newName)
        _selection.value = null
    }

    /**
     * 把当前选中的会话整体移动到 [groupId]（null = 移出分组，回到「全部」），
     * 完成后退出多选并返回移动的数量；空选则 no-op 返回 0。
     */
    suspend fun moveSelectedToGroup(groupId: Long?): Int {
        val ids = _selection.value?.toList().orEmpty()
        if (ids.isEmpty()) return 0
        repository.moveSessionsToGroup(ids, groupId)
        _selection.value = null
        return ids.size
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

        val settings = settingsRepository.settings.first()
        val exportSession = ExportSession(
            sessionIds = snapshot.sessions.map { it.id },
            pin = pinGenerator(),
            createdAt = now(),
            requirePin = settings.requirePin,
        )
        // 上一轮导出若停在 Done（用户回主页前没 acknowledge）会让 armExport 抛
        // IllegalStateException——兜底先清干净，再 arm。
        sessionState.clearExport()
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

    suspend fun saveExport(uri: Uri): ExportStartResult {
        val selectedIds = _selection.value
        if (selectedIds.isNullOrEmpty()) return ExportStartResult.EmptySelection

        val snapshot = repository.exportSnapshot(selectedIds.toList())
        if (snapshot.sessions.isEmpty()) return ExportStartResult.NoValidSessions

        localExportWriter(uri, snapshot)
        _selection.value = null
        return ExportStartResult.Success
    }

    private fun isTransferOrExportRunning(): Boolean {
        val snap = sessionState.snapshot.value
        if (snap.currentSessionId != null) return true
        // Block when an active export is Armed or Sending. Done is "finished, waiting
        // for the user to dismiss" — not a running export, so the next startExport
        // should proceed (and clearExport() is called in startExport as a safety net).
        return when (sessionState.exportMode.value) {
            is ExportMode.Armed, is ExportMode.Sending -> true
            else -> false
        }
    }

    /** Batch delete for the post-export "delete local copies" flow. */
    suspend fun deleteSessions(ids: List<Long>) {
        ids.forEach { repository.deleteSession(it) }
    }

    suspend fun importFromZip(uri: Uri): SessionRepository.ImportResult {
        val ctx = getApplication<Application>()
        val tempFile = java.io.File(ctx.filesDir, "import_temp.zip")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { out -> input.copyTo(out) }
            } ?: return SessionRepository.ImportResult(
                emptyList(), emptyList(),
                listOf(
                    SessionRepository.ImportError(
                        "zip",
                        ctx.getString(R.string.archive_read_failed),
                    )
                ),
            )
            return repository.importSessions(tempFile)
        } finally {
            tempFile.delete()
        }
    }
}
