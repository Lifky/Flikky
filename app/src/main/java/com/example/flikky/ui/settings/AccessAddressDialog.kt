package com.example.flikky.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import com.example.flikky.R
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.LocalHostName

/** 访问地址对话框的草稿态。纯 Kotlin，校验规则全部来自 [LocalHostName]。 */
data class AddressDraft(val enabled: Boolean, val numberText: String, val portText: String) {
    val number: Int? get() = LocalHostName.parseNumber(numberText)
    val port: Int? get() = LocalHostName.parsePort(portText)
    /** 名称关闭时编号不参与保存，也就不报错。 */
    val numberError: Boolean get() = enabled && number == null
    val portError: Boolean get() = port == null
    /** 在范围内、但浏览器拒绝打开（`ERR_UNSAFE_PORT`）：单独提示，别让用户以为是范围写错了。 */
    val portBlockedByBrowsers: Boolean get() = port == null && LocalHostName.parsePortInRange(portText) != null
    val canSave: Boolean get() = !numberError && !portError
    fun preview(): String? {
        val n = number ?: return null
        val p = port ?: return null
        return if (enabled) LocalHostName.url(LocalHostName.fqdn(n), p) else null
    }
}

@Composable
fun AccessAddressDialog(
    initial: AddressDraft,
    onConfirm: (AddressDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_access_address)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_local_name))
                        Text(
                            text = stringResource(R.string.settings_local_name_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
                OutlinedTextField(
                    value = draft.numberText,
                    onValueChange = { draft = draft.copy(numberText = it) },
                    label = { Text(stringResource(R.string.settings_host_number)) },
                    enabled = draft.enabled,
                    isError = draft.numberError,
                    supportingText = if (draft.numberError) {
                        { Text(stringResource(R.string.settings_host_number_error)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.portText,
                    onValueChange = { draft = draft.copy(portText = it) },
                    label = { Text(stringResource(R.string.settings_port)) },
                    isError = draft.portError,
                    supportingText = if (draft.portError) {
                        {
                            Text(
                                stringResource(
                                    if (draft.portBlockedByBrowsers) R.string.settings_port_blocked
                                    else R.string.settings_port_error,
                                ),
                            )
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                // 只预览能保存的结果：字段有错时错误文案已在字段下方，这里不再给「端口 0」之类的假地址。
                val previewText = when {
                    !draft.canSave -> null
                    draft.enabled -> draft.preview()
                    else -> draft.port?.let { stringResource(R.string.settings_access_address_ip_only, it) }
                }
                Column {
                    previewText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_access_address_applies_next),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }, enabled = draft.canSave) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
