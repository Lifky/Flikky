package com.example.flikky.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.ui.theme.Spacing

data class GroupChipModel(
    val id: Long?,
    val label: String,
    val selected: Boolean,
    val showDelete: Boolean,
)

fun buildGroupChipModels(
    groups: List<GroupEntity>,
    activeGroupId: Long?,
    editing: Boolean,
): List<GroupChipModel> =
    listOf(
        GroupChipModel(
            id = null,
            label = "全部",
            selected = activeGroupId == null,
            showDelete = false,
        )
    ) + groups
        .sortedWith(compareBy<GroupEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        .map { group ->
            GroupChipModel(
                id = group.id,
                label = group.name,
                selected = activeGroupId == group.id,
                showDelete = editing,
            )
        }

@Composable
fun GroupChips(
    groups: List<GroupEntity>,
    activeGroupId: Long?,
    editing: Boolean,
    onSelect: (Long?) -> Unit,
    onAdd: () -> Unit,
    onEnterEdit: () -> Unit,
    onRename: (GroupEntity) -> Unit,
    onDelete: (GroupEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = groups.associateBy { it.id }
    val models = buildGroupChipModels(groups, activeGroupId, editing)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(models, key = { it.id ?: Long.MIN_VALUE }) { model ->
                val group = model.id?.let(byId::get)
                Box(
                    modifier = Modifier
                        .padding(end = Spacing.sm)
                        .heightIn(min = 32.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    GroupFilterChip(
                        model = model,
                        group = group,
                        editing = editing,
                        onSelect = onSelect,
                        onEnterEdit = onEnterEdit,
                        onRename = onRename,
                    )
                    if (model.showDelete && group != null) {
                        DeleteBadge(
                            group = group,
                            onDelete = onDelete,
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onAdd,
            modifier = Modifier.semantics { contentDescription = "新建分组" },
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建分组")
        }
    }
}

@Composable
private fun GroupFilterChip(
    model: GroupChipModel,
    group: GroupEntity?,
    editing: Boolean,
    onSelect: (Long?) -> Unit,
    onEnterEdit: () -> Unit,
    onRename: (GroupEntity) -> Unit,
) {
    FilterChip(
        selected = model.selected,
        onClick = {
            if (editing && group != null) onRename(group) else onSelect(model.id)
        },
        label = {
            Text(
                text = model.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = if (model.selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        // 长按进入编辑态。FilterChip 自带 onClick 在内层装了 selectable，会抢先消费按下事件，
        // 外层 combinedClickable 收不到长按（test1「长按无效果」根因）。改用低层手势：
        // awaitFirstDown(requireUnconsumed=false) 不消费 → 原生单击/ripple 照常；
        // awaitLongPressOrCancellation 仅在长按时触发，快速点击返回 null 不打扰单击；
        // 触发后在 Initial 段消费剩余事件，抢在内层 selectable 前，免得松手再补一次单击。
        modifier = Modifier.pointerInput(model.id, editing) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (awaitLongPressOrCancellation(down.id) != null) {
                    onEnterEdit()
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        pressed = event.changes.any { it.pressed }
                    }
                }
            }
        },
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun BoxScope.DeleteBadge(
    group: GroupEntity,
    onDelete: (GroupEntity) -> Unit,
) {
    IconButton(
        onClick = { onDelete(group) },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(24.dp)
            .clip(MaterialTheme.shapes.small)
            .semantics { contentDescription = "删除分组 ${group.name}" },
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "删除分组 ${group.name}",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
