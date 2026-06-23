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

        val models = buildGroupChipModels(groups, activeGroupId = null, editing = false)

        assertEquals(listOf(null, 1L, 2L), models.map { it.id })
        assertEquals(listOf("全部", "A", "B"), models.map { it.label })
        assertTrue(models.first().selected)
    }

    @Test fun models_mark_only_active_custom_group_selected() {
        val groups = listOf(
            GroupEntity(id = 1L, name = "A", sortOrder = 0, createdAt = 10L),
            GroupEntity(id = 2L, name = "B", sortOrder = 1, createdAt = 20L),
        )

        val models = buildGroupChipModels(groups, activeGroupId = 2L, editing = false)

        assertFalse(models[0].selected)
        assertFalse(models[1].selected)
        assertTrue(models[2].selected)
        assertEquals(listOf(false, false, true), models.map { it.showCheck })
    }

    @Test fun models_allow_delete_only_for_custom_groups_in_editing_mode() {
        val groups = listOf(GroupEntity(id = 1L, name = "A", sortOrder = 0, createdAt = 10L))

        val editing = buildGroupChipModels(groups, activeGroupId = null, editing = true)
        val normal = buildGroupChipModels(groups, activeGroupId = null, editing = false)

        assertNull(editing.first().id)
        assertFalse(editing.first().showDelete)
        assertTrue(editing.last().showDelete)
        assertFalse(normal.last().showDelete)
    }

    @Test fun models_allow_entering_edit_from_any_chip_without_making_all_deletable() {
        val groups = listOf(GroupEntity(id = 1L, name = "A", sortOrder = 0, createdAt = 10L))

        val models = buildGroupChipModels(groups, activeGroupId = null, editing = false)

        assertEquals(listOf(true, true), models.map { it.canEnterEdit })
        assertEquals(listOf(false, false), models.map { it.showDelete })
    }
}
