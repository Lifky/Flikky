package com.example.flikky.ui.serving

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.ConnectionInfoCard
import com.example.flikky.ui.components.ConversationBackground
import com.example.flikky.ui.components.ConversationHeader
import com.example.flikky.ui.components.MessageAction
import com.example.flikky.ui.components.MessageActionBar
import com.example.flikky.ui.components.MessageBubble
import com.example.flikky.ui.components.NetworkStatusBanner
import kotlinx.coroutines.launch

@Composable
fun ServingScreen(
    onStopped: () -> Unit,
    viewModel: ServingViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val progressMap by viewModel.fileTransferProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val peerAvatarId by viewModel.peerAvatarId.collectAsState()
    var draft by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var recallTarget by remember { mutableStateOf<Long?>(null) }
    var actionTarget by remember { mutableStateOf<Long?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.offerFile(it) } }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            NetworkStatusBanner(
                status = ui.networkStatus,
                onAcknowledge = { viewModel.acknowledgeNetworkSwitch() },
            )
            AnimatedContent(
                targetState = ui.clientConnected,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut()
                },
                label = "ConnHeader",
            ) { connected ->
                if (connected) {
                    ConversationHeader(
                        peerAvatarId = peerAvatarId,
                        peerName = "",
                        uptimeSeconds = ui.uptimeSeconds,
                        fileCount = ui.fileCount,
                        bytesPerSecond = ui.bytesPerSecond,
                    )
                } else {
                    Column(Modifier.padding(24.dp)) {
                        ConnectionInfoCard(url = ui.url, pin = ui.pin)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                ConversationBackground(
                    setting = settings.background,
                    connected = ui.clientConnected,
                    peerName = null,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(ui.messages, key = { _, m -> m.id }) { index, msg ->
                            val prevMsg = if (index > 0) ui.messages[index - 1] else null
                            val showAvatar = prevMsg == null || prevMsg.origin != msg.origin
                            val isFailed = msg is Message.File && msg.status == Message.File.Status.FAILED
                            val isActionTarget = actionTarget == msg.id

                            // Painters resolved in composable scope (stable across recompositions)
                            val undoPainter = painterResource(R.drawable.ic_undo)
                            val downloadPainter = painterResource(R.drawable.ic_file_download)
                            val copyPainter = painterResource(R.drawable.ic_content_copy)
                            val deletePainter = painterResource(R.drawable.ic_delete)

                            val msgActions = buildList<MessageAction> {
                                // 复制 — text only
                                if (msg is Message.Text) {
                                    add(MessageAction(
                                        icon = copyPainter,
                                        label = "复制",
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(msg.content))
                                            actionTarget = null
                                        },
                                    ))
                                }
                                // 打开 — file COMPLETED
                                if (msg is Message.File && msg.status == Message.File.Status.COMPLETED) {
                                    add(MessageAction(
                                        icon = downloadPainter,
                                        label = "打开",
                                        onClick = {
                                            viewModel.openFile(msg)
                                            actionTarget = null
                                        },
                                    ))
                                }
                                // 撤回 — phone origin, beta enabled, not failed
                                if (settings.recallBetaEnabled && msg.origin == Origin.PHONE && !isFailed) {
                                    add(MessageAction(
                                        icon = undoPainter,
                                        label = "撤回",
                                        onClick = {
                                            recallTarget = msg.id
                                            actionTarget = null
                                        },
                                    ))
                                }
                                // 删除 — always present
                                add(MessageAction(
                                    icon = deletePainter,
                                    label = "删除",
                                    danger = true,
                                    onClick = {
                                        val id = msg.id
                                        actionTarget = null
                                        viewModel.deleteLocalWithUndo(id)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "已删除",
                                                actionLabel = "撤销",
                                                duration = SnackbarDuration.Short,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.undoDelete()
                                            } else {
                                                viewModel.commitDelete(id)
                                            }
                                        }
                                    },
                                ))
                            }

                            Column {
                                MessageBubble(
                                    msg = msg,
                                    onClick = { if (msg is Message.File) viewModel.openFile(msg) },
                                    onLongPress = { actionTarget = if (isActionTarget) null else msg.id },
                                    transferProgress = progressMap[msg.id],
                                    showAvatar = showAvatar,
                                    avatarId = if (msg.origin == Origin.PHONE) settings.phoneAvatarId
                                               else peerAvatarId,
                                    cornerRadius = settings.bubbleCornerRadius.dp,
                                )
                                val barAlignment = if (msg.origin == Origin.PHONE) Alignment.CenterEnd else Alignment.CenterStart
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    contentAlignment = barAlignment,
                                ) {
                                    MessageActionBar(
                                        visible = isActionTarget,
                                        actions = msgActions,
                                    )
                                }
                                if (isActionTarget) Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                // Snackbar floats at the bottom of the conversation area,
                // directly above the input Row, overlaying messages rather
                // than displacing them.
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) { Snackbar(it) }
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
