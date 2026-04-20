package com.example.flikky.ui.history

import android.app.Application
import app.cash.turbine.test
import com.example.flikky.data.SessionRepository
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
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

class HistoryViewModelTest {
    @Test fun exposes_session_and_messages() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        every { repo.observeSession(42L) } returns MutableStateFlow(
            SessionEntity(id = 42L, startedAt = 1L, endedAt = 2L, name = "s")
        )
        every { repo.observeMessages(42L) } returns MutableStateFlow(
            listOf(Message.Text(id = 1, origin = Origin.PHONE, timestamp = 10, content = "yo"))
        )

        val vm = HistoryViewModel(app, repo, sessionId = 42L)
        vm.session.test {
            assertEquals("s", awaitItem()!!.name); cancelAndIgnoreRemainingEvents()
        }
        vm.messages.test {
            assertEquals(1, awaitItem().size); cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun rename_delegates() = runTest {
        val app = mockk<Application>(relaxed = true)
        val repo = mockk<SessionRepository>()
        every { repo.observeSession(any()) } returns MutableStateFlow(null)
        every { repo.observeMessages(any()) } returns MutableStateFlow(emptyList())
        coEvery { repo.rename(any(), any()) } just Runs

        val vm = HistoryViewModel(app, repo, sessionId = 7L)
        vm.rename("new").join()
        coVerify { repo.rename(7L, "new") }
    }
}
