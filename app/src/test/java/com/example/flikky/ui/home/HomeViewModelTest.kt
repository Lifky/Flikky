package com.example.flikky.ui.home

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.session.SessionState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    private fun stubSession(): SessionState = SessionState(nowMs = { 0L })
    private fun preferences(): Preferences = mockk(relaxed = true)

    private fun stubSettings(): SettingsRepository {
        val settings = mockk<SettingsRepository>()
        every { settings.settings } returns MutableStateFlow(FlikkySettings())
        return settings
    }

    @Test fun sessions_flow_is_forwarded_from_repository() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val flow = MutableStateFlow(
            listOf(SessionEntity(id = 1L, startedAt = 1L, endedAt = 2L, name = "x"))
        )
        every { repo.observeSessions() } returns flow
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = stubSettings())
        vm.sessions.test {
            val got = awaitItem()
            assertEquals(1, got.size)
            assertEquals("x", got.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun rename_delegates_to_repository() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        coEvery { repo.rename(any(), any()) } just Runs

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = stubSettings())
        vm.rename(sessionId = 42L, newName = "hi").join()
        coVerify { repo.rename(42L, "hi") }
    }

    @Test fun homeItems_filters_by_activeGroup_and_uses_date_buckets() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val sessions = MutableStateFlow(
            listOf(
                SessionEntity(id = 1L, startedAt = todayStart + 1_000L, endedAt = todayStart + 2_000L, name = "A", groupId = 7L),
                SessionEntity(id = 2L, startedAt = todayStart + 3_000L, endedAt = todayStart + 4_000L, name = "B", groupId = 8L),
                SessionEntity(id = 3L, startedAt = todayStart + 5_000L, endedAt = todayStart + 6_000L, name = "C"),
            )
        )
        val settingsFlow = MutableStateFlow(FlikkySettings(activeGroupId = 7L))
        val settings = mockk<SettingsRepository>()
        every { repo.observeSessions() } returns sessions
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { settings.settings } returns settingsFlow

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = settings)

        val out = vm.homeItems.first()
        assertEquals(listOf(HomeListBuilder.BUCKET_TODAY), out.headers())
        assertEquals(listOf(1L), out.sessionIds())
    }

    @Test fun search_visible_only_when_history_is_saved() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val settingsFlow = MutableStateFlow(FlikkySettings(historyRetainLimit = 20))
        val settings = mockk<SettingsRepository>()
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { settings.settings } returns settingsFlow

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = settings)

        assertEquals(true, vm.searchEnabled.first())
        settingsFlow.value = FlikkySettings(historyRetainLimit = 0)
        assertEquals(false, vm.searchEnabled.first())
        settingsFlow.value = FlikkySettings(historyRetainLimit = -1)
        assertEquals(true, vm.searchEnabled.first())
    }

    @Test fun groups_flow_is_forwarded_from_repository() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val groups = MutableStateFlow(listOf(GroupEntity(id = 7L, name = "Work", sortOrder = 0, createdAt = 1L)))
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns groups

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = stubSettings())

        assertEquals(listOf("Work"), vm.groups.first().map { it.name })
    }

    @Test fun createGroup_selects_new_group() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val settings = mockk<SettingsRepository>()
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { settings.settings } returns MutableStateFlow(FlikkySettings())
        coEvery { repo.createGroup("Work") } returns 9L
        coEvery { settings.setActiveGroup(any()) } returns preferences()

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = settings)
        vm.createGroup("Work").join()

        coVerify { repo.createGroup("Work") }
        coVerify { settings.setActiveGroup(9L) }
    }

    @Test fun createGroup_ignores_blank_names_and_truncates_to_twelve_chars() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val settings = mockk<SettingsRepository>()
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { settings.settings } returns MutableStateFlow(FlikkySettings())
        coEvery { repo.createGroup(any()) } returns 9L
        coEvery { settings.setActiveGroup(any()) } returns preferences()

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = settings)
        vm.createGroup("   ").join()
        vm.createGroup("123456789012345").join()

        coVerify(exactly = 0) { repo.createGroup("   ") }
        coVerify { repo.createGroup("123456789012") }
    }

    @Test fun deleteGroupWithUndo_clears_active_group_only_when_current_group_is_deleted() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val settings = mockk<SettingsRepository>()
        val group = GroupEntity(id = 7L, name = "Work", sortOrder = 0, createdAt = 1L)
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { settings.settings } returns MutableStateFlow(FlikkySettings(activeGroupId = 7L))
        coEvery { repo.deleteGroup(7L) } returns (group to listOf(1L, 2L))
        coEvery { settings.setActiveGroup(any()) } returns preferences()

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = settings)
        val token = vm.deleteGroupWithUndo(7L)

        assertEquals(group to listOf(1L, 2L), token)
        coVerify { repo.deleteGroup(7L) }
        coVerify { settings.setActiveGroup(null) }
    }

    @Test fun restoreGroup_selects_restored_group_id() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val settings = mockk<SettingsRepository>()
        val group = GroupEntity(id = 7L, name = "Work", sortOrder = 0, createdAt = 1L)
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        every { settings.settings } returns MutableStateFlow(FlikkySettings())
        coEvery { repo.restoreGroup(group, listOf(1L, 2L)) } returns 11L
        coEvery { settings.setActiveGroup(any()) } returns preferences()

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = settings)
        vm.restoreGroup(group, listOf(1L, 2L))

        coVerify { repo.restoreGroup(group, listOf(1L, 2L)) }
        coVerify { settings.setActiveGroup(11L) }
    }

    @Test fun renameGroup_ignores_blank_names_and_truncates_to_twelve_chars() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        every { repo.observeSessions() } returns MutableStateFlow(emptyList())
        every { repo.observeGroups() } returns MutableStateFlow(emptyList())
        coEvery { repo.renameGroup(any(), any()) } just Runs

        val vm = HomeViewModel(app, repo, stubSession(), settingsRepository = stubSettings())
        vm.renameGroup(7L, "   ").join()
        vm.renameGroup(7L, "123456789012345").join()

        coVerify(exactly = 0) { repo.renameGroup(7L, "   ") }
        coVerify { repo.renameGroup(7L, "123456789012") }
    }

    private fun List<HomeListItem>.headers() =
        filterIsInstance<HomeListItem.Header>().map { it.label }

    private fun List<HomeListItem>.sessionIds() =
        filterIsInstance<HomeListItem.SessionItem>().map { it.session.id }
}
