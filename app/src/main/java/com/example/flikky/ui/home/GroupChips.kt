package com.example.flikky.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.ui.theme.Spacing

data class GroupChipModel(
    val id: Long?,
    val label: String,
    val selected: Boolean,
)

fun buildGroupChipModels(
    groups: List<GroupEntity>,
    activeGroupId: Long?,
): List<GroupChipModel> =
    listOf(
        GroupChipModel(id = null, label = "全部", selected = activeGroupId == null),
    ) + groups
        .sortedWith(compareBy<GroupEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        .map { group ->
            GroupChipModel(
                id = group.id,
                label = group.name,
                selected = activeGroupId == group.id,
            )
        }

/**
 * 主页分组单选行：「全部」+ 自定义分组 FilterChip（横滑）+ 右侧固定「新建」。
 * 单击切换分组；长按自定义分组弹「管理分组」框（重命名/排序/删除统一在那里）。「全部」不可管理。
 */
@Composable
fun GroupChips(
    groups: List<GroupEntity>,
    activeGroupId: Long?,
    onSelect: (Long?) -> Unit,
    onAdd: () -> Unit,
    onManage: (GroupEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = groups.associateBy { it.id }
    val models = buildGroupChipModels(groups, activeGroupId)

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
                GroupFilterChip(
                    model = model,
                    group = model.id?.let(byId::get),
                    onSelect = onSelect,
                    onManage = onManage,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
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
    onSelect: (Long?) -> Unit,
    onManage: (GroupEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    FilterChip(
        selected = model.selected,
        onClick = { onSelect(model.id) },
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
        // 长按自定义分组 → 弹「管理分组」框；「全部」(group==null) 无长按。
        // FilterChip 内层 selectable 会先消费按下，外层 combinedClickable 收不到长按，故用低层手势：
        // awaitFirstDown(requireUnconsumed=false) 不消费 → 原生单击/ripple 照常；
        // 长按触发时震动 + 回调，再于 Initial 段消费收尾，避免松手补一次单击。
        modifier = if (group != null) {
            modifier.pointerInput(group.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (awaitLongPressOrCancellation(down.id) != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onManage(group)
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            pressed = event.changes.any { it.pressed }
                        }
                    }
                }
            }
        } else {
            modifier
        },
        shape = MaterialTheme.shapes.small,
    )
}
