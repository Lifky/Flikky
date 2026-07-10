package com.example.flikky.ui.exporting

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.SessionRepository
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.SessionExport
import com.example.flikky.export.ExportScope
import com.example.flikky.export.FavoriteExport
import com.example.flikky.network.NetworkInfo
import com.example.flikky.service.TransferService
import com.example.flikky.session.Origin
import com.example.flikky.session.SessionState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportingViewModelTest {

    private lateinit var app: Application
    private lateinit var session: SessionState
    private lateinit var networkInfo: NetworkInfo
    private lateinit var repository: SessionRepository

    @Before fun setUp() {
        app = spyk(ApplicationProvider.getApplicationContext())
        every { app.startService(any()) } returns null

        session = SessionState(nowMs = { 0L })
        networkInfo = mockk()
        every { networkInfo.currentWifiIpv4() } returns "192.168.1.5"
        repository = mockk()
    }

    private fun buildVm(): ExportingViewModel =
        ExportingViewModel(app, session, networkInfo, repository)

    private fun sampleSnapshot(
        sessions: List<SessionExport> = listOf(
            SessionExport(
                id = 1L, name = "one", startedAt = 100L, endedAt = 200L,
                pinned = false,
                messages = listOf(
                    MessageExport.File(
                        ts = 150L, origin = Origin.PHONE,
                        fileId = "f1", name = "a.bin", mime = "application/octet-stream",
                        sizeBytes = 1_000L,
                    ),
                ),
            ),
            SessionExport(
                id = 2L, name = "two", startedAt = 300L, endedAt = 400L,
                pinned = false,
                messages = listOf(
                    MessageExport.File(
                        ts = 350L, origin = Origin.BROWSER,
                        fileId = "f2", name = "b.bin", mime = "application/octet-stream",
                        sizeBytes = 2_500L,
                    ),
                ),
            ),
        ),
    ): ExportSnapshot = ExportSnapshot(sessions = sessions, exportedAt = 500L)

    private fun sampleExportSession(ids: List<Long> = listOf(1L, 2L), pin: String = "654321") =
        ExportSession(sessionIds = ids, pin = pin, createdAt = 1_000L)

    @Test fun idle_maps_to_Gone_phase() {
        val vm = buildVm()
        val state = vm.ui.value
        assertEquals(ExportingUiState.Phase.Gone, state.phase)
    }

    @Test fun armed_maps_to_Armed_with_url_pin_and_aggregated_bytes() {
        session.updateBoundPort(8083)
        session.armExport(sampleExportSession(), sampleSnapshot())

        val vm = buildVm()
        val state = vm.ui.value

        assertEquals(ExportingUiState.Phase.Armed, state.phase)
        assertEquals("http://192.168.1.5:8083", state.url)
        assertEquals("654321", state.pin)
        assertEquals(2, state.sessionCount)
        assertEquals(3_500L, state.totalBytes)
        assertEquals(0L, state.bytesSent)
        assertEquals(listOf(1L, 2L), state.sessionIds)
    }

    @Test fun armed_with_unset_port_falls_back_to_ip_only_url() {
        session.armExport(sampleExportSession(), sampleSnapshot())

        val vm = buildVm()
        val state = vm.ui.value

        assertEquals(ExportingUiState.Phase.Armed, state.phase)
        assertEquals("http://192.168.1.5", state.url)
    }

    @Test fun favorites_export_exposes_scope_count_and_file_bytes() {
        val snapshot = ExportSnapshot(
            exportedAt = 1L,
            scope = ExportScope.FAVORITES,
            favorites = listOf(
                FavoriteExport(
                    id = 1L,
                    sourceSessionId = 2L,
                    sourceMessageId = 3L,
                    kind = "FILE",
                    fileId = "favorite-file",
                    fileName = "a.bin",
                    fileSize = 2_048L,
                    fileMime = "application/octet-stream",
                    createdAt = 4L,
                )
            ),
        )
        session.armExport(
            ExportSession(
                sessionIds = emptyList(),
                pin = "123456",
                createdAt = 1L,
                scope = ExportScope.FAVORITES,
                favoriteCount = 1,
            ),
            snapshot,
        )

        val state = buildVm().ui.value

        assertEquals(ExportScope.FAVORITES, state.scope)
        assertEquals(1, state.favoriteCount)
        assertEquals(2_048L, state.totalBytes)
        assertTrue(state.sessionIds.isEmpty())
    }

    @Test fun sending_maps_to_Sending_with_progress_bytes() {
        session.updateBoundPort(8080)
        session.armExport(sampleExportSession(), sampleSnapshot())
        session.updateExportProgress(bytesSent = 1_024L, totalBytes = 4_096L)

        val vm = buildVm()
        val state = vm.ui.value

        assertEquals(ExportingUiState.Phase.Sending, state.phase)
        assertEquals(1_024L, state.bytesSent)
        assertEquals(4_096L, state.totalBytes)
        assertEquals("http://192.168.1.5:8080", state.url)
        assertEquals(listOf(1L, 2L), state.sessionIds)
    }

    @Test fun done_maps_to_Done_with_session_ids() {
        session.armExport(sampleExportSession(), sampleSnapshot())
        session.updateExportProgress(4_096L, 4_096L)
        session.markExportDone()

        val vm = buildVm()
        val state = vm.ui.value

        assertEquals(ExportingUiState.Phase.Done, state.phase)
        assertEquals(2, state.sessionCount)
        assertEquals(listOf(1L, 2L), state.sessionIds)
    }

    @Test fun cancelExport_fires_ACTION_STOP_intent_at_TransferService() {
        val vm = buildVm()
        vm.cancelExport()

        val intents = mutableListOf<Intent>()
        verify { app.startService(capture(intents)) }
        val intent = intents.single()
        assertEquals(TransferService.ACTION_STOP, intent.action)
        assertEquals(TransferService::class.java.name, intent.component?.className)
    }

    @Test fun deleteLocal_calls_repository_for_each_id() = runTest {
        coEvery { repository.deleteSession(any()) } just Runs

        val vm = buildVm()
        vm.deleteLocal(listOf(10L, 20L, 30L))

        coVerify(exactly = 1) { repository.deleteSession(10L) }
        coVerify(exactly = 1) { repository.deleteSession(20L) }
        coVerify(exactly = 1) { repository.deleteSession(30L) }
    }

    @Test fun deleteLocal_with_empty_ids_is_noop() = runTest {
        val vm = buildVm()
        vm.deleteLocal(emptyList())
        coVerify(exactly = 0) { repository.deleteSession(any()) }
    }

    @Test fun acknowledge_clears_exportMode_back_to_Idle() {
        session.armExport(sampleExportSession(), sampleSnapshot())
        session.updateExportProgress(1L, 1L)
        session.markExportDone()

        val vm = buildVm()
        vm.acknowledge()

        assertTrue(session.exportMode.value is com.example.flikky.export.ExportMode.Idle)
    }
}
