package com.example.flikky.data.db.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupEntityTest {
    @Test
    fun groupEntityDefaultsToNewRowIdAndSessionDefaultsToUngrouped() {
        val group = GroupEntity(name = "Work", sortOrder = 2, createdAt = 123L)
        val session = SessionEntity(startedAt = 10L, endedAt = null, name = "Session")

        assertEquals(0L, group.id)
        assertNull(session.groupId)
    }
}
