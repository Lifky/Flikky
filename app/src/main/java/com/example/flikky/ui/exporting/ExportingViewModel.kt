package com.example.flikky.ui.exporting

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flikky.data.SessionRepository
import com.example.flikky.di.ServiceLocator
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.ExportScope
import com.example.flikky.network.NetworkInfo
import com.example.flikky.service.TransferService
import com.example.flikky.session.NetworkStatus
import com.example.flikky.session.SessionState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the exporting screen, derived off [SessionState.exportMode] +
 * the bound Ktor port + the Wi-Fi IPv4. Three live phases map to the export
 * state machine; a fourth [Phase.Gone] sentinel tells the screen to unwind
 * itself because the export was cleared (cancel / service died / APP reopened
 * after recovery) while the user was still on this route.
 */
data class ExportingUiState(
    val phase: Phase,
    val url: String = "",
    val pin: String = "",
    val sessionCount: Int = 0,
    val totalBytes: Long = 0L,
    val bytesSent: Long = 0L,
    val sessionIds: List<Long> = emptyList(),
    val networkStatus: NetworkStatus = NetworkStatus.Ok,
    val requirePin: Boolean = true,
    val scope: ExportScope = ExportScope.SESSIONS,
    val favoriteCount: Int = 0,
    val settingsIncluded: Boolean = false,
) {
    enum class Phase { Armed, Sending, Done, Gone }
}

class ExportingViewModel @JvmOverloads constructor(
    app: Application,
    private val sessionState: SessionState = ServiceLocator.session,
    private val networkInfo: NetworkInfo = ServiceLocator.networkInfo,
    private val repository: SessionRepository = ServiceLocator.repository,
) : AndroidViewModel(app) {

    val ui: StateFlow<ExportingUiState> = combine(
        sessionState.exportMode,
        sessionState.snapshot,
    ) { mode, snap -> mode.toUiState(snap.boundPort).copy(networkStatus = snap.networkStatus) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = sessionState.exportMode.value
                .toUiState(sessionState.snapshot.value.boundPort)
                .copy(networkStatus = sessionState.snapshot.value.networkStatus),
        )

    /** "我知道了" on the NetworkStatusBanner — fold Switched back to Ok. */
    fun acknowledgeNetworkSwitch() {
        sessionState.acknowledgeNetworkSwitch()
    }

    /**
     * Fire ACTION_STOP at the running TransferService. Same mechanism the
     * Serving screen's "停止服务" button uses — we deliberately don't clear
     * [SessionState.exportMode] here; the service teardown path does that on
     * its own (see TransferService.stopActiveServer), so the UI transitions
     * Armed/Sending → Gone via the normal flow.
     */
    fun cancelExport() {
        val ctx = getApplication<Application>()
        ctx.startService(
            Intent(ctx, TransferService::class.java).apply {
                action = TransferService.ACTION_STOP
            }
        )
    }

    /**
     * Batch-delete the listed sessions through the repository, same routine
     * HomeViewModel.deleteSessions uses. Called from the Done screen after the
     * D20 AlertDialog confirmation — this is the irreversible branch.
     */
    fun deleteLocal(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.deleteSession(it) }
        }
    }

    /**
     * Unwinds the export back to Idle. Called right after the user picks
     * "保留本地" or "删除本地" on the Done page so the ExportingScreen's
     * Gone-sentinel triggers popBackStack.
     */
    fun acknowledge() {
        sessionState.clearExport()
    }

    private fun ExportMode.toUiState(boundPort: Int): ExportingUiState {
        val ip = networkInfo.currentWifiIpv4() ?: "?"
        val urlBase = if (boundPort > 0) "http://$ip:$boundPort" else "http://$ip"
        return when (val mode = this) {
            is ExportMode.Idle -> ExportingUiState(phase = ExportingUiState.Phase.Gone)
            is ExportMode.Armed -> ExportingUiState(
                phase = ExportingUiState.Phase.Armed,
                url = urlBase,
                pin = mode.session.pin,
                sessionCount = mode.snapshot.sessions.size,
                totalBytes = aggregateBytes(mode.snapshot),
                bytesSent = 0L,
                sessionIds = mode.session.sessionIds,
                requirePin = mode.session.requirePin,
                scope = mode.snapshot.scope,
                favoriteCount = mode.snapshot.favorites.size,
                settingsIncluded = mode.snapshot.settings != null,
            )
            is ExportMode.Sending -> ExportingUiState(
                phase = ExportingUiState.Phase.Sending,
                url = urlBase,
                pin = mode.session.pin,
                sessionCount = mode.session.sessionIds.size,
                totalBytes = mode.totalBytes,
                bytesSent = mode.bytesSent,
                sessionIds = mode.session.sessionIds,
                requirePin = mode.session.requirePin,
                scope = mode.session.scope,
                favoriteCount = mode.session.favoriteCount,
                settingsIncluded = mode.session.settingsIncluded,
            )
            is ExportMode.Done -> ExportingUiState(
                phase = ExportingUiState.Phase.Done,
                url = urlBase,
                pin = mode.session.pin,
                sessionCount = mode.session.sessionIds.size,
                totalBytes = 0L,
                bytesSent = 0L,
                sessionIds = mode.session.sessionIds,
                requirePin = mode.session.requirePin,
                scope = mode.session.scope,
                favoriteCount = mode.session.favoriteCount,
                settingsIncluded = mode.session.settingsIncluded,
            )
        }
    }

    private fun aggregateBytes(snapshot: ExportSnapshot): Long {
        val sessionBytes = snapshot.sessions.sumOf { s ->
            s.messages.filterIsInstance<MessageExport.File>().sumOf { it.sizeBytes }
        }
        val favoriteBytes = snapshot.favorites
            .filter { it.kind == "FILE" }
            .sumOf { it.fileSize ?: 0L }
        return sessionBytes + favoriteBytes
    }
}
