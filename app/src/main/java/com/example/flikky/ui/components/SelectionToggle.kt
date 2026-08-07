package com.example.flikky.ui.components

internal data class SelectionToggle(
    val allSelected: Boolean,
    val targetIds: Set<Long>,
)

internal fun selectionToggle(
    availableIds: Collection<Long>,
    selectedIds: Set<Long>,
): SelectionToggle {
    val available = availableIds.toSet()
    val allSelected = available.isNotEmpty() && available.all { it in selectedIds }
    return SelectionToggle(
        allSelected = allSelected,
        targetIds = if (allSelected) emptySet() else available,
    )
}
