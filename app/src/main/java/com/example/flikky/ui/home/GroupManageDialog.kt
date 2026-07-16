package com.example.flikky.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.ui.theme.Spacing

/**
 * 单个分组的统一管理框：改名（输入框）/ 排序（上移·下移，即时生效）/ 删除（关框后走撤销 snackbar）。
 * 长按分组 chip 弹出。改名在「保存」时应用；排序按钮即时应用并保持本框打开。
 */
@Composable
fun GroupManageDialog(
    group: GroupEntity,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSave: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(group.id) { mutableStateOf(group.name.take(12)) }
    val trimmed = draft.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_manage_group)) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(12) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.home_group_name)) },
                    supportingText = { Text("${draft.length}/12") },
                    isError = draft.isNotEmpty() && trimmed.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.home_group_order), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(Spacing.sm))
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.home_move_up),
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.home_move_down),
                        )
                    }
                }
                TextButton(
                    onClick = { onDelete(); onDismiss() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.home_delete_group)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trimmed.isNotEmpty()) onSave(trimmed)
                    onDismiss()
                },
                enabled = trimmed.isNotEmpty(),
            ) { Text(stringResource(R.string.home_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
