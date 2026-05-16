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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3 D26 修订：TransferController.recallMessage 的 minimal 回归保护。
 *
 *  - Success 路径必须广播 message_recalled，否则浏览器要等下次轮询才更新。
 *  - 非 Success 路径（Denied/NotFound）不能广播，否则浏览器会错误地从 UI 中
 *    移除别人的正常消息。
 *
 * 不重复覆盖 repository 的撤回语义本身——`SessionRepositoryRecallTest` 已覆盖。
 */
class TransferControllerRecallTest {

    private fun controllerWith(
        repository: SessionRepository,
        hub: WsHub,
    ): TransferController {
        val session = SessionState(nowMs = { 1_000L }).apply { startNew(sessionId = 42L) }
        val stats = TransferStats(nowMs = { 1_000L })
        val fileStore = mockk<SessionFileStore>(relaxed = true)
        return TransferController(
            session = session,
            stats = stats,
            fileStore = fileStore,
            repository = repository,
            wsHub = { hub },
            nowMs = { 1_000L },
            senderId = "phone-test",
        )
    }

    @Test
    fun `recallMessage Success triggers broadcast with sessionId and messageId`() = runTest {
        val hub = mockk<WsHub>(relaxed = true)
        val repository = mockk<SessionRepository>()
        coEvery { repository.recallMessage(123L, "phone-test") } returns
            SessionRepository.RecallOutcome.Success(messageId = 123L, sessionId = 42L)

        val controller = controllerWith(repository, hub)
        val out = controller.recallMessage(123L)

        assertTrue("outcome should be Success but was $out", out is SessionRepository.RecallOutcome.Success)
        coVerify(exactly = 1) { hub.broadcastRecall(42L, 123L) }
    }

    @Test
    fun `recallMessage Denied does not broadcast`() = runTest {
        val hub = mockk<WsHub>(relaxed = true)
        val repository = mockk<SessionRepository>()
        coEvery { repository.recallMessage(123L, "phone-test") } returns SessionRepository.RecallOutcome.Denied

        val controller = controllerWith(repository, hub)
        val out = controller.recallMessage(123L)

        assertTrue("outcome should be Denied but was $out", out is SessionRepository.RecallOutcome.Denied)
        coVerify(exactly = 0) { hub.broadcastRecall(any(), any()) }
        coVerify(exactly = 0) { hub.broadcast(any(), any()) }
    }

    @Test
    fun `recallMessage NotFound does not broadcast`() = runTest {
        val hub = mockk<WsHub>(relaxed = true)
        val repository = mockk<SessionRepository>()
        coEvery { repository.recallMessage(999L, "phone-test") } returns SessionRepository.RecallOutcome.NotFound

        val controller = controllerWith(repository, hub)
        val out = controller.recallMessage(999L)

        assertTrue("outcome should be NotFound but was $out", out is SessionRepository.RecallOutcome.NotFound)
        coVerify(exactly = 0) { hub.broadcastRecall(any(), any()) }
        coVerify(exactly = 0) { hub.broadcast(any(), any()) }
    }

    // AlreadyRecalled 分支已随 v1.3 D26 修订（撤回 = 真删）一并移除：
    // 真删后再次撤回是 NotFound，已有的 NotFound 测试覆盖。
}
