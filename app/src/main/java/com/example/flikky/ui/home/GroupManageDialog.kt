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
        title = { Text("管理分组") },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(12) },
                    singleLine = true,
                    label = { Text("分组名") },
                    supportingText = { Text("${draft.length}/12") },
                    isError = draft.isNotEmpty() && trimmed.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("顺序", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(Spacing.sm))
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                    }
                }
                TextButton(
                    onClick = { onDelete(); onDismiss() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("删除分组") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trimmed.isNotEmpty()) onSave(trimmed)
                    onDismiss()
                },
                enabled = trimmed.isNotEmpty(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
