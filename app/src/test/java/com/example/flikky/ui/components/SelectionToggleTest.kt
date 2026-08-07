package com.example.flikky.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionToggleTest {
    @Test
    fun partialSelection_targetsAllAvailableItems() {
        val toggle = selectionToggle(
            availableIds = listOf(1L, 2L, 3L),
            selectedIds = setOf(2L),
        )

        assertFalse(toggle.allSelected)
        assertEquals(setOf(1L, 2L, 3L), toggle.targetIds)
    }

    @Test
    fun completeSelection_targetsEmptySelection() {
        val toggle = selectionToggle(
            availableIds = listOf(1L, 2L, 3L),
            selectedIds = setOf(1L, 2L, 3L),
        )

        assertTrue(toggle.allSelected)
        assertEquals(emptySet<Long>(), toggle.targetIds)
    }

    @Test
    fun emptyAvailability_isNotAllSelected() {
        val toggle = selectionToggle(
            availableIds = emptyList(),
            selectedIds = emptySet(),
        )

        assertFalse(toggle.allSelected)
        assertEquals(emptySet<Long>(), toggle.targetIds)
    }
}
