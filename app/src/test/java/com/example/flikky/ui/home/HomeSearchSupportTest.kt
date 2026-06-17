package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSearchSupportTest {
    private fun s(id: Long, name: String) =
        SessionEntity(id = id, startedAt = 0L, endedAt = 1L, name = name)

    @Test fun empty_or_blank_query_returns_empty() {
        val list = listOf(s(1, "会议"), s(2, "项目"))
        assertEquals(emptyList<SessionEntity>(), matchSessionsByName(list, "   "))
    }

    @Test fun matches_case_insensitive_substring() {
        val list = listOf(s(1, "Project Alpha"), s(2, "会议记录"), s(3, "alpha-beta"))
        assertEquals(listOf(1L, 3L), matchSessionsByName(list, "alpha").map { it.id })
    }

    @Test fun matches_cjk_substring() {
        val list = listOf(s(1, "会议记录"), s(2, "项目文档"))
        assertEquals(listOf(1L), matchSessionsByName(list, "会议").map { it.id })
    }
}
