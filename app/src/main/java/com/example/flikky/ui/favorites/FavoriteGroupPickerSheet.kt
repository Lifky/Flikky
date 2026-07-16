package com.example.flikky.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteGroupPickerSheet(
    groups: List<FavoriteGroupEntity>,
    onSelect: (Long?) -> Unit,
    onCreateGroup: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreate by remember { mutableStateOf(false) }
    val ordered = remember(groups) {
        groups.sortedWith(
            compareBy<FavoriteGroupEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id }
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
                text = stringResource(R.string.favorites_choose_group),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            )
            PickerRow(
                label = stringResource(R.string.favorites_all_ungrouped),
                iconRes = R.drawable.ic_swap_vert,
                onClick = { onSelect(null) },
            )
            ordered.forEach { group ->
                PickerRow(
                    label = group.name,
                    iconRes = R.drawable.ic_drive_file_move,
                    onClick = { onSelect(group.id) },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreate = true }
                    .defaultMinSize(minHeight = Sizes.rowMinH)
                    .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(Spacing.lg))
                Text(stringResource(R.string.favorites_new_group), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (showCreate) {
        GroupNameDialog(
            title = stringResource(R.string.favorites_new_group),
            onConfirm = { name ->
                showCreate = false
                onCreateGroup(name)
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun PickerRow(
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

@Composable
internal fun GroupNameDialog(
    title: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial.take(12)) }
    val trimmed = draft.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(12) },
                singleLine = true,
                label = { Text(stringResource(R.string.favorites_group_name)) },
                supportingText = { Text("${draft.length}/12") },
                isError = draft.isNotEmpty() && trimmed.isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
