package com.example.flikky.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.flikky.R
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing

/**
 * 批量把所选会话移动到某分组的底部面板。
 *
 * 顶部固定一项「全部（移出分组）」(回调 null)，下面按 sortOrder 列出全部自定义分组。
 * 无自定义分组时只剩「全部（移出分组）」一项。
 *
 * @param onSelect 选中目标后回调；null = 移出分组（回到「全部」），非 null = 目标分组 id。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToGroupSheet(
    groups: List<GroupEntity>,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ordered = remember(groups) {
        groups.sortedWith(
            compareBy<GroupEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = "移动到分组",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenEdge,
                    vertical = Spacing.sm,
                ),
            )
            MoveTargetRow(
                label = "全部（移出分组）",
                iconRes = R.drawable.ic_swap_vert,
                onClick = { onSelect(null) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs)
            )
            ordered.forEach { group ->
                MoveTargetRow(
                    label = group.name,
                    iconRes = R.drawable.ic_drive_file_move,
                    onClick = { onSelect(group.id) },
                )
            }
        }
    }
}

@Composable
private fun MoveTargetRow(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Sizes.rowMinH)
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.lg))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
