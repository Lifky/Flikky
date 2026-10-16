package com.example.flikky.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.data.settings.DEVICE_NAME_MAX

/**
 * 设备名对话框。设置页（本机名）、快捷设置（本机名）、设备面板（对端改名）三处共用 ——
 * 原先前两处逐字重复，抽出后三处视觉零差异。
 * 预填「当前生效的名字」（空则是默认名）；保存时把「改回默认名」归一化成空串。
 */
@Composable
fun DeviceNameDialog(
    title: String,
    currentName: String,
    defaultName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(currentName.ifBlank { defaultName }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= DEVICE_NAME_MAX) draft = it },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_device_name_limit)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(normalizeDeviceNameDraft(draft, defaultName)) }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** 否则默认名会被当成用户自定义值存下来，之后默认名变了（比如换了语言）它也不跟着变。 */
internal fun normalizeDeviceNameDraft(draft: String, defaultName: String): String =
    draft.trim().takeUnless { it == defaultName }.orEmpty()
