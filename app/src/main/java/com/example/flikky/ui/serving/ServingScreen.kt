package com.example.flikky.ui.serving

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.flikky.ui.components.ConversationStatusRow
import com.example.flikky.ui.components.MessageAction
import com.example.flikky.ui.components.MessageActionBar
import com.example.flikky.ui.components.MessageBubble
import com.example.flikky.ui.components.MessageFloatingToolbarOverlay
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
    var showAttachSheet by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.offerFile(it) } }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.offerFile(it) } }

    // Painters resolved once in composable scope (stable across recompositions),
    // shared by both inline and floating action paths.
    val undoPainter = painterResource(R.drawable.ic_undo)
    val downloadPainter = painterResource(R.drawable.ic_file_download)
    val copyPainter = painterResource(R.drawable.ic_content_copy)
    val deletePainter = painterResource(R.drawable.ic_delete)

    // Single source of truth for a message's available actions; used by both the
    // legacy inline bar and the floating toolbar so the logic never diverges.
    fun buildActionsFor(msg: Message): List<MessageAction> = buildList {
        val isFailed = msg is Message.File && msg.status == Message.File.Status.FAILED
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

    val listState = rememberLazyListState()
    // Dismiss the floating/inline action target whenever the list starts scrolling.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) actionTarget = null
    }
    // System-back dismisses the action target before exiting the screen.
    androidx.activity.compose.BackHandler(enabled = actionTarget != null) { actionTarget = null }

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
                        trailing = {
                            IconButton(onClick = { viewModel.stopService(); onStopped() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_stop),
                                    contentDescription = "停止服务",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                } else {
                    Column(Modifier.padding(24.dp)) {
                        ConnectionInfoCard(url = ui.url, pin = ui.pin)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "等待浏览器连接…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.stopService(); onStopped() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("停止服务") }
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
                  androidx.compose.foundation.text.selection.SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(ui.messages, key = { _, m -> m.id }) { index, msg ->
                            val prevMsg = if (index > 0) ui.messages[index - 1] else null
                            val nextMsg = ui.messages.getOrNull(index + 1)
                            val showAvatar = when (settings.avatarGrouping) {
                                com.example.flikky.data.settings.AvatarGroupingMode.FIRST ->
                                    prevMsg == null || prevMsg.origin != msg.origin
                                com.example.flikky.data.settings.AvatarGroupingMode.LAST ->
                                    nextMsg == null || nextMsg.origin != msg.origin
                                com.example.flikky.data.settings.AvatarGroupingMode.EACH -> true
                            }
                            val isActionTarget = actionTarget == msg.id
                            val floating = settings.messageActionStyle ==
                                com.example.flikky.data.settings.MessageActionStyle.FLOATING

                            Column {
                                MessageBubble(
                                    msg = msg,
                                    onTap = {
                                        if (floating) {
                                            actionTarget = if (isActionTarget) null else msg.id
                                        } else if (msg is Message.File) {
                                            viewModel.openFile(msg)
                                        }
                                    },
                                    onLongPress = if (floating) null
                                                  else { { actionTarget = if (isActionTarget) null else msg.id } },
                                    transferProgress = progressMap[msg.id],
                                    showAvatar = showAvatar,
                                    avatarId = if (msg.origin == Origin.PHONE) settings.phoneAvatarId
                                               else peerAvatarId,
                                    cornerRadius = settings.bubbleCornerRadius.dp,
                                    selected = floating && isActionTarget,
                                )
                                if (!floating) {
                                    val barAlignment = if (msg.origin == Origin.PHONE) Alignment.CenterEnd else Alignment.CenterStart
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        contentAlignment = barAlignment,
                                    ) {
                                        MessageActionBar(
                                            visible = isActionTarget,
                                            actions = buildActionsFor(msg),
                                        )
                                    }
                                    if (isActionTarget) Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                  }
                }

                // Floating action toolbar: one bottom-center bar for the selected
                // message. lastActions keeps content during the exit animation so
                // the bar doesn't go blank while fading out.
                if (settings.messageActionStyle ==
                    com.example.flikky.data.settings.MessageActionStyle.FLOATING) {
                    val target = ui.messages.firstOrNull { it.id == actionTarget }
                    var lastActions by remember { mutableStateOf<List<MessageAction>>(emptyList()) }
                    if (target != null) lastActions = buildActionsFor(target)
                    MessageFloatingToolbarOverlay(
                        visible = target != null,
                        actions = lastActions,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                    .navigationBarsPadding().imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("输入消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                IconButton(
                    onClick = { showAttachSheet = true },
                    enabled = ui.clientConnected,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
                IconButton(
                    onClick = { viewModel.sendText(draft); draft = "" },
                    enabled = draft.isNotBlank() && ui.clientConnected,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_send),
                        contentDescription = "发送",
                        tint = if (draft.isNotBlank() && ui.clientConnected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ConversationStatusRow(
                uptimeSeconds = ui.uptimeSeconds,
                fileCount = ui.fileCount,
                bytesPerSecond = ui.bytesPerSecond,
            )
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

    if (showAttachSheet) {
        AttachBottomSheet(
            onPickFile = { showAttachSheet = false; pickFile.launch("*/*") },
            onPickImage = { showAttachSheet = false; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { showAttachSheet = false },
        )
    }
}
