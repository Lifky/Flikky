package com.example.flikky.ui.serving

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.MessageBubble
import com.example.flikky.ui.components.NetworkStatusBanner
import com.example.flikky.ui.components.StatusBar

@Composable
fun ServingScreen(
    onStopped: () -> Unit,
    viewModel: ServingViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val progressMap by viewModel.fileTransferProgress.collectAsState()
    var draft by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var recallTarget by remember { mutableStateOf<Long?>(null) }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.offerFile(it) } }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            NetworkStatusBanner(
                status = ui.networkStatus,
                onAcknowledge = { viewModel.acknowledgeNetworkSwitch() },
            )
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("在电脑浏览器打开：", style = MaterialTheme.typography.bodyMedium)
                Text(ui.url, style = MaterialTheme.typography.headlineSmall)
                Text("PIN  ${ui.pin}", style = MaterialTheme.typography.displaySmall)
                Text(
                    if (ui.clientConnected) "已连接" else "等待连接…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(ui.messages, key = { it.id }) { msg ->
                    var menuOpen by remember(msg.id) { mutableStateOf(false) }
                    val canRecall = msg.origin == Origin.PHONE
                    val isFailed = msg is Message.File && msg.status == Message.File.Status.FAILED
                    Box {
                        MessageBubble(
                            msg = msg,
                            onClick = { if (msg is Message.File) viewModel.openFile(msg) },
                            onLongPress = if (canRecall && !isFailed) {
                                { menuOpen = true }
                            } else null,
                            transferProgress = progressMap[msg.id],
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("撤回") },
                                onClick = {
                                    menuOpen = false
                                    recallTarget = msg.id
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("输入消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { pickFile.launch("*/*") },
                    enabled = ui.clientConnected,
                ) { Text("附件") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.sendText(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank() && ui.clientConnected,
                ) { Text("发送") }
            }

            StatusBar(
                uptimeSeconds = ui.uptimeSeconds,
                fileCount = ui.fileCount,
                bytesPerSecond = ui.bytesPerSecond,
            )
            TextButton(
                onClick = { viewModel.stopService(); onStopped() },
                modifier = Modifier.padding(16.dp),
            ) { Text("停止服务") }
        }
    }

    // 撤回二次确认 AlertDialog。点错也能取消，避免误删（D26 修订）。
    recallTarget?.let { targetId ->
        AlertDialog(
            onDismissRequest = { recallTarget = null },
            title = { Text("撤回这条消息？") },
            text = { Text("撤回后两端都会消失，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    recallTarget = null
                    viewModel.recallMessage(targetId)
                }) { Text("撤回") }
            },
            dismissButton = {
                TextButton(onClick = { recallTarget = null }) { Text("取消") }
            },
        )
    }
}

