package com.example.flikky.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.data.settings.SortMode
import com.example.flikky.ui.theme.Spacing

@Composable
fun SortGroupChips(
    sort: SortMode,
    group: GroupMode,
    onSortChange: (SortMode) -> Unit,
    onGroupChange: (GroupMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ChipMenu(
            label = "排序：" + when (sort) {
                SortMode.TIME -> "时间"
                SortMode.NAME -> "名称"
            },
            options = listOf(
                SortMode.TIME to "时间（新到旧）",
                SortMode.NAME to "名称（A 到 Z）",
            ),
            onPick = onSortChange,
        )
        ChipMenu(
            label = "分组：" + when (group) {
                GroupMode.NONE -> "无"
                GroupMode.STATUS -> "状态"
                GroupMode.DATE -> "日期"
            },
            options = listOf(
                GroupMode.NONE to "不分组",
                GroupMode.STATUS to "按状态",
                GroupMode.DATE to "按日期",
            ),
            onPick = onGroupChange,
        )
    }
}

@Composable
private fun <T> ChipMenu(
    label: String,
    options: List<Pair<T, String>>,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            modifier = Modifier.semantics { contentDescription = label },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onPick(value)
                    },
                )
            }
        }
    }
}
