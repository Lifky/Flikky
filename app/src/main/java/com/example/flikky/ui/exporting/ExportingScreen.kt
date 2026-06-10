package com.example.flikky.ui.exporting

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.ui.components.ConnectionInfoCard
import com.example.flikky.ui.components.NetworkStatusBanner

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportingScreen(
    onBack: () -> Unit,
    viewModel: ExportingViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()

    // Block the system back gesture from escaping while an export is live —
    // users must route through the explicit "取消导出" / "保留 / 删除" flows so
    // SessionState.exportMode stays in sync with what's on screen.
    BackHandler(enabled = ui.phase != ExportingUiState.Phase.Gone) { /* no-op */ }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(topBarTitleFor(ui.phase)) },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
                NetworkStatusBanner(
                    status = ui.networkStatus,
                    onAcknowledge = { viewModel.acknowledgeNetworkSwitch() },
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = ui.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "ExportingPhase",
        ) { phase ->
            when (phase) {
                ExportingUiState.Phase.Armed -> ArmedContent(
                    url = ui.url,
                    pin = ui.pin,
                    onCancel = {
                        viewModel.cancelExport()
                        onBack()
                    },
                )
                ExportingUiState.Phase.Sending -> SendingContent(
                    bytesSent = ui.bytesSent,
                    totalBytes = ui.totalBytes,
                    onCancel = {
                        viewModel.cancelExport()
                        onBack()
                    },
                )
                ExportingUiState.Phase.Done -> DoneContent(
                    sessionCount = ui.sessionCount,
                    sessionIds = ui.sessionIds,
                    onKeep = {
                        viewModel.acknowledge()
                        onBack()
                    },
                    onConfirmDelete = { ids ->
                        viewModel.deleteLocal(ids)
                        viewModel.acknowledge()
                        onBack()
                    },
                )
                ExportingUiState.Phase.Gone -> {
                    // Export was cleared out from under us (cancel / crash-recovery
                    // / service stopped). Pop immediately instead of showing a dead
                    // screen with stale URL/PIN.
                    LaunchedEffect(Unit) { onBack() }
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun topBarTitleFor(phase: ExportingUiState.Phase): String = when (phase) {
    ExportingUiState.Phase.Armed -> "导出就绪"
    ExportingUiState.Phase.Sending -> "正在发送"
    ExportingUiState.Phase.Done -> "导出完成"
    ExportingUiState.Phase.Gone -> ""
}

@Composable
private fun ArmedContent(
    url: String,
    pin: String,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectionInfoCard(url = url, pin = pin)

        Text(
            text = "在电脑浏览器打开上方地址，输入 PIN 后下载 zip。\n下载完成本页会自动跳转。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) { Text("取消导出") }
    }
}

@Composable
private fun SendingContent(
    bytesSent: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
) {
    val progress = if (totalBytes > 0L) {
        (bytesSent.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "浏览器正在下载 zip …",
            style = MaterialTheme.typography.titleMedium,
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "已发送 ${formatSize(bytesSent)} / ${formatSize(totalBytes)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onCancel) {
            Text(
                text = "取消导出",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneContent(
    sessionCount: Int,
    sessionIds: List<Long>,
    onKeep: () -> Unit,
    onConfirmDelete: (List<Long>) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "已导出 $sessionCount 个会话到 PC",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "zip 已保存在电脑下载目录。现在可以选择保留本地副本，或在确认 zip 无误后删除本地。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onKeep,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保留本地") }

        TextButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) { Text("删除本地") }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除本地 $sessionCount 条会话？") },
            text = {
                Text(
                    "将删除 $sessionCount 条会话与对应的所有文件，此操作不可撤销。" +
                        "请先确认 zip 已保存到 PC 安全位置。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onConfirmDelete(sessionIds)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes >= 1024L * 1024L) return "%.1f MB".format(bytes / 1048576.0)
    if (bytes >= 1024L) return "%.1f KB".format(bytes / 1024.0)
    return "$bytes B"
}
