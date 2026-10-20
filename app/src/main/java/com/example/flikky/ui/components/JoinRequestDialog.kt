package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.DialogProperties
import com.example.flikky.R
import com.example.flikky.server.JoinRequests
import com.example.flikky.ui.theme.Spacing

/**
 * 新设备想加入本次会话（v1.23 多设备，用户裁决）。点外面不关：放不放人必须明确选一个；
 * 一分钟没选，请求自己过期，对方页面会提示「手机没有响应」。
 *
 * 比对码（D82）放在说明之前、居中加大：设备名可以伪造，码才是用户核对的依据。
 */
@Composable
fun JoinRequestDialog(request: JoinRequests.View, onAllow: () -> Unit, onDeny: () -> Unit) {
    val name = request.label ?: stringResource(R.string.device_unknown_browser)
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = false),
        icon = { Icon(painterResource(R.drawable.ic_desktop_windows), contentDescription = null) },
        title = { Text(stringResource(R.string.join_request_title, name)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // 读屏逐位念（「4 8 2 1」），而不是「四千八百二十一」。
                val spoken = stringResource(R.string.join_request_code_description, request.code.toList().joinToString(" "))
                Text(
                    text = request.code,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("join-code")
                        .semantics { contentDescription = spoken },
                )
                Spacer(Modifier.height(Spacing.lg))
                Text(stringResource(R.string.join_request_body))
            }
        },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.join_request_allow)) } },
        dismissButton = { TextButton(onClick = onDeny) { Text(stringResource(R.string.join_request_deny)) } },
    )
}
