package com.example.flikky.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val showCheck: Boolean,
    val canEnterEdit: Boolean,
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
            showCheck = activeGroupId == null,
            canEnterEdit = true,
        )
    ) + groups
        .sortedWith(compareBy<GroupEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        .map { group ->
            GroupChipModel(
                id = group.id,
                label = group.name,
                selected = activeGroupId == group.id,
                showDelete = editing,
                showCheck = activeGroupId == group.id,
                canEnterEdit = true,
            )
        }

@OptIn(ExperimentalFoundationApi::class)
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
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .combinedClickable(
                                onClick = {
                                    if (editing && group != null) onRename(group) else onSelect(model.id)
                                },
                                onLongClick = {
                                    if (model.canEnterEdit) onEnterEdit()
                                },
                            ),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupFilterChip(
    model: GroupChipModel,
) {
    FilterChip(
        selected = model.selected,
        onClick = {},
        label = {
            Text(
                text = model.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = if (model.showCheck) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
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
