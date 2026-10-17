package com.example.flikky.ui.serving

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.example.flikky.R
import com.example.flikky.server.JoinRequests

/**
 * 新设备想加入本次会话（v1.23 多设备，用户裁决）。点外面不关：放不放人必须明确选一个；
 * 一分钟没选，请求自己过期，对方页面会提示「手机没有响应」。
 */
@Composable
fun JoinRequestDialog(request: JoinRequests.View, onAllow: () -> Unit, onDeny: () -> Unit) {
    val name = request.label ?: stringResource(R.string.device_unknown_browser)
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        icon = { Icon(painterResource(R.drawable.ic_desktop_windows), contentDescription = null) },
        title = { Text(stringResource(R.string.join_request_title, name)) },
        text = { Text(stringResource(R.string.join_request_body)) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.join_request_allow)) } },
        dismissButton = { TextButton(onClick = onDeny) { Text(stringResource(R.string.join_request_deny)) } },
    )
}
