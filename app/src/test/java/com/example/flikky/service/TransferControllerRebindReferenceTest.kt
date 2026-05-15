package com.example.flikky.service

import com.example.flikky.data.SessionFileStore
import com.example.flikky.data.SessionRepository
import com.example.flikky.server.routes.WsHub
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Regression guard for the v1.2 "closure capture dead reference" bug class.
 * See `docs/others/notes/retrospective.md` §"最深的设计教训：闭包捕获死引用".
 *
 * TransferController must receive `wsHub` as `() -> WsHub?` so a Wi-Fi rebind
 * (which builds a fresh KtorServer with a fresh WsHub) is reflected on the
 * next broadcast. If anyone reverts the parameter back to a direct WsHub
 * field, this test fails — broadcasts after rebind would keep hitting the
 * discarded hub and the browser would silently miss every APP→browser message.
 */
class TransferControllerRebindReferenceTest {

    @Test
    fun `wsHub lambda resolves the current hub holder at each call`() {
        var currentHub: WsHub? = WsHub()
        val initialHub = currentHub!!
        val provider: () -> WsHub? = { currentHub }

        assertSame(initialHub, provider())

        val replacement = WsHub()
        currentHub = replacement

        assertNotSame(initialHub, provider())
        assertSame(replacement, provider())
    }

    @Test
    fun `sendText broadcasts to the current hub even after rebind`() = runTest {
        val oldHub = mockk<WsHub>(relaxed = true)
        val newHub = mockk<WsHub>(relaxed = true)
        coEvery { oldHub.broadcast(any(), any()) } returns Unit
        coEvery { newHub.broadcast(any(), any()) } returns Unit

        var currentHub: WsHub? = oldHub

        val session = SessionState(nowMs = { 1_000L }).apply { startNew(sessionId = 42L) }
        val stats = TransferStats(nowMs = { 1_000L })
        val fileStore = mockk<SessionFileStore>(relaxed = true)
        val repository = mockk<SessionRepository>(relaxed = true)

        val controller = TransferController(
            session = session,
            stats = stats,
            fileStore = fileStore,
            repository = repository,
            wsHub = { currentHub },
            nowMs = { 1_000L },
            senderId = "phone-test",
        )

        controller.sendText("before rebind")
        coVerify(exactly = 1) { oldHub.broadcast("text_added", any()) }
        coVerify(exactly = 0) { newHub.broadcast(any(), any()) }

        // Simulate Wi-Fi rebind — the KtorServer field swap on TransferService
        // replaces the WsHub. The same controller instance must follow.
        currentHub = newHub

        controller.sendText("after rebind")
        coVerify(exactly = 1) { oldHub.broadcast(any(), any()) }   // still 1
        coVerify(exactly = 1) { newHub.broadcast("text_added", any()) }
    }
}
