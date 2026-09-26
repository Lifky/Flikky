package com.example.flikky.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R

/**
 * ⓘ 按钮 + 说明弹窗。设置行与连接卡片共用这一份，保证两处看起来、点起来完全一样。
 */
@Composable
fun InfoIconButton(title: String, text: String) {
    var showInfo by remember { mutableStateOf(false) }
    IconButton(onClick = { showInfo = true }) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = stringResource(R.string.settings_show_info),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.common_got_it))
                }
            },
        )
    }
}
