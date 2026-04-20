package com.example.flikky.ui.home

import android.app.Application
import app.cash.turbine.test
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test fun sessions_flow_is_forwarded_from_repository() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        val flow = MutableStateFlow(
            listOf(SessionEntity(id = 1L, startedAt = 1L, endedAt = 2L, name = "x"))
        )
        every { repo.observeSessions() } returns flow

        val vm = HomeViewModel(app, repo)
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
        coEvery { repo.rename(any(), any()) } just Runs

        val vm = HomeViewModel(app, repo)
        vm.rename(sessionId = 42L, newName = "hi").join()
        coVerify { repo.rename(42L, "hi") }
    }
}
