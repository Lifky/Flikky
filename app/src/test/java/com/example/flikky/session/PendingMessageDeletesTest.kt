package com.example.flikky.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMessageDeletesTest {

    private fun text(id: Long) = Message.Text(
        id = id, origin = Origin.PHONE, timestamp = id, content = "m$id",
    )

    @Test
    fun `drainIds returns staged ids and clears them`() {
        val p = PendingMessageDeletes()
        p.stage(1L, text(1L))
        p.stage(2L, text(2L))
        assertEquals(listOf(1L, 2L), p.drainIds())
        assertTrue(p.drainIds().isEmpty())
    }

    @Test
    fun `undoLatest returns latest snapshot and unstages only it`() {
        val p = PendingMessageDeletes()
        p.stage(1L, text(1L))
        p.stage(2L, text(2L))
        assertEquals(text(2L), p.undoLatest())
        // 撤销的那条不再待提交；更早的软删仍要落库。
        assertEquals(listOf(1L), p.drainIds())
    }

    @Test
    fun `undoLatest with nothing staged returns null`() {
        assertNull(PendingMessageDeletes().undoLatest())
    }

    @Test
    fun `undo only reaches the most recent stage once`() {
        val p = PendingMessageDeletes()
        p.stage(1L, text(1L))
        assertEquals(text(1L), p.undoLatest())
        assertNull(p.undoLatest())
        assertTrue(p.drainIds().isEmpty())
    }

    @Test
    fun `commit unstages the id`() {
        val p = PendingMessageDeletes()
        p.stage(1L, text(1L))
        p.stage(2L, text(2L))
        p.commit(1L)
        assertEquals(listOf(2L), p.drainIds())
    }

    @Test
    fun `committed id is no longer reachable by undo`() {
        val p = PendingMessageDeletes()
        p.stage(1L, text(1L))
        p.commit(1L)
        assertNull(p.undoLatest())
    }

    @Test
    fun `stage tolerates a missing snapshot`() {
        val p = PendingMessageDeletes()
        p.stage(7L, null)
        assertNull(p.undoLatest())
        // 快照缺失时 undo 已消费该条目，不再重复提交。
        assertTrue(p.drainIds().isEmpty())
    }

    @Test
    fun `restaging the same id replaces the snapshot`() {
        val p = PendingMessageDeletes()
        p.stage(1L, text(1L))
        val newer = text(1L).copy(content = "edited")
        p.stage(1L, newer)
        assertEquals(newer, p.undoLatest())
        assertTrue(p.drainIds().isEmpty())
    }
}
