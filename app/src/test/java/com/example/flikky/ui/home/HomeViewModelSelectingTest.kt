package com.example.flikky.ui.home

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.export.ExportMode
import com.example.flikky.export.ExportSession
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.SessionExport
import com.example.flikky.service.TransferService
import com.example.flikky.session.SessionState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HomeViewModelSelectingTest {

    private lateinit var app: Application
    private lateinit var repo: SessionRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var session: SessionState
    private var fakeNow: Long = 10_000L

    @Before fun setUp() {
        app = spyk(ApplicationProvider.getApplicationContext())
        // Swallow startForegroundService to avoid booting a real service in tests.
        every { app.startForegroundService(any()) } returns null

        repo = mockk()
        every { repo.observeSessions() } returns MutableStateFlow(emptyList<SessionEntity>())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        settingsRepo = mockk()
        every { settingsRepo.settings } returns MutableStateFlow(FlikkySettings())
        session = SessionState(nowMs = { fakeNow })
    }

    private fun buildVm(
        pin: String = "123456",
    ): HomeViewModel = HomeViewModel(
        app = app,
        repository = repo,
        sessionState = session,
        pinGenerator = { pin },
        now = { fakeNow },
        settingsRepository = settingsRepo,
    )

    @Test fun initial_state_is_not_selecting() {
        val vm = buildVm()
        assertNull(vm.selection.value)
        assertFalse(vm.selecting.value)
    }

    @Test fun enterSelecting_arms_empty_selection() {
        val vm = buildVm()
        vm.enterSelecting()
        assertEquals(emptySet<Long>(), vm.selection.value)
        assertTrue(vm.selecting.value)
    }

    @Test fun toggleSelection_adds_and_removes() {
        val vm = buildVm()
        vm.enterSelecting()
        vm.toggleSelection(1L)
        vm.toggleSelection(2L)
        assertEquals(setOf(1L, 2L), vm.selection.value)
        vm.toggleSelection(1L)
        assertEquals(setOf(2L), vm.selection.value)
    }

    @Test fun toggleSelection_from_null_creates_selection() {
        // Toggling from non-selecting mode implicitly enters selecting mode with that id.
        val vm = buildVm()
        vm.toggleSelection(7L)
        assertEquals(setOf(7L), vm.selection.value)
        assertTrue(vm.selecting.value)
    }

    @Test fun selectAll_replaces_selection() {
        val vm = buildVm()
        vm.enterSelecting()
        vm.toggleSelection(5L)
        vm.selectAll(listOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 2L, 3L), vm.selection.value)
    }

    @Test fun clearSelection_returns_to_null() {
        val vm = buildVm()
        vm.enterSelecting()
        vm.toggleSelection(1L)
        vm.clearSelection()
        assertNull(vm.selection.value)
        assertFalse(vm.selecting.value)
    }

    @Test fun exitSelecting_returns_to_null() {
        val vm = buildVm()
        vm.enterSelecting()
        vm.exitSelecting()
        assertNull(vm.selection.value)
    }

    @Test fun startExport_returns_EmptySelection_when_not_selecting() = runTest {
        val vm = buildVm()
        val result = vm.startExport()
        assertEquals(HomeViewModel.ExportStartResult.EmptySelection, result)
        coVerify(exactly = 0) { repo.exportSnapshot(any()) }
    }

    @Test fun startExport_returns_EmptySelection_when_selection_empty() = runTest {
        val vm = buildVm()
        vm.enterSelecting() // selection = emptySet(), still "empty"
        val result = vm.startExport()
        assertEquals(HomeViewModel.ExportStartResult.EmptySelection, result)
        coVerify(exactly = 0) { repo.exportSnapshot(any()) }
    }

    @Test fun startExport_returns_TransferRunning_when_transfer_active() = runTest {
        // Simulate a transfer already running: SessionState.startNew sets currentSessionId.
        session.startNew(sessionId = 42L)

        val vm = buildVm()
        vm.enterSelecting()
        vm.toggleSelection(1L)

        val result = vm.startExport()
        assertEquals(HomeViewModel.ExportStartResult.TransferRunning, result)
        // Selection is preserved so the UI can show a Snackbar/Toast without losing state.
        assertEquals(setOf(1L), vm.selection.value)
        coVerify(exactly = 0) { repo.exportSnapshot(any()) }
        verify(exactly = 0) { app.startForegroundService(any()) }
    }

    @Test fun startExport_returns_TransferRunning_when_export_already_armed() = runTest {
        val previouslyArmedSnapshot = ExportSnapshot(
            sessions = listOf(
                SessionExport(
                    id = 99L, name = "prev", startedAt = 1L, endedAt = 2L,
                    pinned = false, messages = emptyList(),
                ),
            ),
            exportedAt = 3L,
        )
        val previouslyArmedSession = ExportSession(
            sessionIds = listOf(99L), pin = "999999", createdAt = 3L,
        )
        session.armExport(previouslyArmedSession, previouslyArmedSnapshot)

        val vm = buildVm()
        vm.enterSelecting()
        vm.toggleSelection(1L)

        val result = vm.startExport()
        assertEquals(HomeViewModel.ExportStartResult.TransferRunning, result)
        coVerify(exactly = 0) { repo.exportSnapshot(any()) }
    }

    @Test fun startExport_returns_NoValidSessions_when_snapshot_is_empty() = runTest {
        coEvery { repo.exportSnapshot(listOf(1L)) } returns
            ExportSnapshot(sessions = emptyList(), exportedAt = fakeNow)

        val vm = buildVm()
        vm.enterSelecting()
        vm.toggleSelection(1L)

        val result = vm.startExport()
        assertEquals(HomeViewModel.ExportStartResult.NoValidSessions, result)
        // Nothing armed, no service started.
        assertTrue(session.exportMode.value is ExportMode.Idle)
        verify(exactly = 0) { app.startForegroundService(any()) }
        // Selection preserved so user can adjust.
        assertEquals(setOf(1L), vm.selection.value)
    }

    @Test fun startExport_Success_arms_state_starts_service_clears_selection() = runTest {
        val snap = ExportSnapshot(
            sessions = listOf(
                SessionExport(
                    id = 1L, name = "one", startedAt = 100L, endedAt = 200L,
                    pinned = false, messages = emptyList(),
                ),
                SessionExport(
                    id = 2L, name = "two", startedAt = 300L, endedAt = 400L,
                    pinned = false, messages = emptyList(),
                ),
            ),
            exportedAt = fakeNow,
        )
        coEvery { repo.exportSnapshot(any()) } returns snap

        val vm = buildVm(pin = "654321")
        vm.enterSelecting()
        vm.toggleSelection(1L)
        vm.toggleSelection(2L)

        val result = vm.startExport()
        assertEquals(HomeViewModel.ExportStartResult.Success, result)

        // SessionState armed with the fresh PIN and the repo snapshot.
        val mode = session.exportMode.value
        assertTrue("expected Armed but was $mode", mode is ExportMode.Armed)
        val armed = mode as ExportMode.Armed
        assertEquals("654321", armed.session.pin)
        assertEquals(listOf(1L, 2L), armed.session.sessionIds.sorted())
        assertEquals(fakeNow, armed.session.createdAt)
        assertTrue(armed.session.requirePin)
        assertEquals(snap, armed.snapshot)

        // Service kicked off with ACTION_EXPORT.
        val intentSlot = mutableListOf<Intent>()
        verify { app.startForegroundService(capture(intentSlot)) }
        val intent = intentSlot.single()
        assertEquals(TransferService.ACTION_EXPORT, intent.action)
        assertEquals(TransferService::class.java.name, intent.component?.className)

        // Selection cleared on success (exits selecting mode).
        assertNull(vm.selection.value)
        assertFalse(vm.selecting.value)
    }

    @Test fun startExport_snapshots_requirePin_setting() = runTest {
        every { settingsRepo.settings } returns MutableStateFlow(FlikkySettings(requirePin = false))
        val snap = ExportSnapshot(
            sessions = listOf(
                SessionExport(
                    id = 1L, name = "one", startedAt = 100L, endedAt = 200L,
                    pinned = false, messages = emptyList(),
                ),
            ),
            exportedAt = fakeNow,
        )
        coEvery { repo.exportSnapshot(any()) } returns snap

        val vm = buildVm(pin = "654321")
        vm.enterSelecting()
        vm.toggleSelection(1L)

        val result = vm.startExport()

        assertEquals(HomeViewModel.ExportStartResult.Success, result)
        val armed = session.exportMode.value as ExportMode.Armed
        assertFalse(armed.session.requirePin)
    }

    @Test fun deleteSessions_calls_repository_for_each_id() = runTest {
        coEvery { repo.deleteSession(any()) } just Runs

        val vm = buildVm()
        vm.deleteSessions(listOf(1L, 2L, 3L))

        coVerify(exactly = 1) { repo.deleteSession(1L) }
        coVerify(exactly = 1) { repo.deleteSession(2L) }
        coVerify(exactly = 1) { repo.deleteSession(3L) }
    }

    @Test fun pinSelected_sets_pinned_for_each_and_exits() = runTest {
        coEvery { repo.setPinned(any(), any()) } just Runs
        val vm = buildVm()
        vm.enterSelecting(); vm.toggleSelection(1L); vm.toggleSelection(2L)
        vm.pinSelected(true)
        coVerify(exactly = 1) { repo.setPinned(1L, true) }
        coVerify(exactly = 1) { repo.setPinned(2L, true) }
        assertNull(vm.selection.value)
    }

    @Test fun deleteSelected_deletes_each_and_exits() = runTest {
        coEvery { repo.deleteSession(any()) } just Runs
        val vm = buildVm()
        vm.enterSelecting(); vm.toggleSelection(3L); vm.toggleSelection(4L)
        vm.deleteSelected()
        coVerify(exactly = 1) { repo.deleteSession(3L) }
        coVerify(exactly = 1) { repo.deleteSession(4L) }
        assertNull(vm.selection.value)
    }

    @Test fun renameSelected_renames_single_and_exits() = runTest {
        coEvery { repo.rename(any(), any()) } just Runs
        val vm = buildVm()
        vm.enterSelecting(); vm.toggleSelection(5L)
        vm.renameSelected("新名字")
        coVerify(exactly = 1) { repo.rename(5L, "新名字") }
        assertNull(vm.selection.value)
    }

    @Test fun renameSelected_noop_when_not_single() = runTest {
        coEvery { repo.rename(any(), any()) } just Runs
        val vm = buildVm()
        vm.enterSelecting(); vm.toggleSelection(5L); vm.toggleSelection(6L)
        vm.renameSelected("x")
        coVerify(exactly = 0) { repo.rename(any(), any()) }
        assertEquals(setOf(5L, 6L), vm.selection.value)
    }

    @Test fun moveSelectedToGroup_binds_each_and_exits() = runTest {
        val captured = slot<List<Long>>()
        coEvery { repo.moveSessionsToGroup(capture(captured), 7L) } just Runs
        val vm = buildVm()
        vm.enterSelecting(); vm.toggleSelection(1L); vm.toggleSelection(2L)

        val count = vm.moveSelectedToGroup(7L)

        assertEquals(2, count)
        assertEquals(setOf(1L, 2L), captured.captured.toSet())
        coVerify(exactly = 1) { repo.moveSessionsToGroup(any(), 7L) }
        assertNull(vm.selection.value)
    }

    @Test fun moveSelectedToGroup_to_null_ungroups_and_exits() = runTest {
        coEvery { repo.moveSessionsToGroup(any(), null) } just Runs
        val vm = buildVm()
        vm.enterSelecting(); vm.toggleSelection(3L)

        val count = vm.moveSelectedToGroup(null)

        assertEquals(1, count)
        coVerify(exactly = 1) { repo.moveSessionsToGroup(listOf(3L), null) }
        assertNull(vm.selection.value)
    }

    @Test fun moveSelectedToGroup_noop_when_empty() = runTest {
        val vm = buildVm()
        val count = vm.moveSelectedToGroup(7L)
        assertEquals(0, count)
        coVerify(exactly = 0) { repo.moveSessionsToGroup(any(), any()) }
    }
}
