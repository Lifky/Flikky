package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.GroupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChipsTest {
    @Test fun models_place_all_first_then_groups_in_sort_order() {
        val groups = listOf(
            GroupEntity(id = 2L, name = "B", sortOrder = 1, createdAt = 20L),
            GroupEntity(id = 1L, name = "A", sortOrder = 0, createdAt = 10L),
        )

        val models = buildGroupChipModels(groups, activeGroupId = null)

        assertEquals(listOf(null, 1L, 2L), models.map { it.id })
        assertEquals(listOf("全部", "A", "B"), models.map { it.label })
        assertTrue(models.first().selected)
    }

    @Test fun models_mark_only_active_custom_group_selected() {
        val groups = listOf(
            GroupEntity(id = 1L, name = "A", sortOrder = 0, createdAt = 10L),
            GroupEntity(id = 2L, name = "B", sortOrder = 1, createdAt = 20L),
        )

        val models = buildGroupChipModels(groups, activeGroupId = 2L)

        assertFalse(models[0].selected)
        assertFalse(models[1].selected)
        assertTrue(models[2].selected)
    }

    @Test fun models_with_no_custom_groups_show_only_all_selected() {
        val models = buildGroupChipModels(emptyList(), activeGroupId = null)

        assertEquals(1, models.size)
        assertNull(models.first().id)
        assertTrue(models.first().selected)
    }
}
