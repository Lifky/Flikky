package com.example.flikky.ui.serving

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.di.ServiceLocator
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.ConnectionInfoCard
import com.example.flikky.ui.components.ConversationBackground
import com.example.flikky.ui.components.ConversationHeader
import com.example.flikky.ui.components.ConversationStatusRow
import com.example.flikky.ui.components.AvatarKey
import com.example.flikky.ui.components.MessageAction
import com.example.flikky.ui.components.MessageActionBar
import com.example.flikky.ui.components.MessageBubble
import com.example.flikky.ui.components.MessageFloatingToolbarOverlay
import com.example.flikky.ui.components.NetworkStatusBanner
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.components.setPlainText
import com.example.flikky.ui.favorites.FavoriteGroupPickerSheet
import com.example.flikky.ui.settings.sheets.AvatarPickerSheet
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
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
    val peerAvatarKey by viewModel.peerAvatarKey.collectAsState()
    var draft by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var recallTarget by remember { mutableStateOf<Long?>(null) }
    var actionTarget by remember { mutableStateOf<Long?>(null) }
    var pendingFavoriteMsg by remember { mutableStateOf<Message?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showFavoriteQuickSheet by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showPeerAvatarPicker by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
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
    val starPainter = painterResource(R.drawable.ic_star)
    val starBorderPainter = painterResource(R.drawable.ic_star_border)
    val currentSessionId = ServiceLocator.session.snapshot.collectAsState().value.currentSessionId
    val favoriteGroups by if (settings.favoriteBetaEnabled) {
        ServiceLocator.favoritesRepository.observeGroups().collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val favorites by if (settings.favoriteBetaEnabled) {
        ServiceLocator.favoritesRepository.observeFavorites().collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val favoritedIds by if (settings.favoriteBetaEnabled && currentSessionId != null) {
        ServiceLocator.favoritesRepository.observeFavoritedIds(currentSessionId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<Long>()) }
    }

    // Single source of truth for a message's available actions; used by both the
    // legacy inline bar and the floating toolbar so the logic never diverges.
    fun buildActionsFor(msg: Message): List<MessageAction> = buildList {
        val isFailed = msg is Message.File && msg.status == Message.File.Status.FAILED
        if (settings.favoriteBetaEnabled &&
            (msg is Message.Text || (msg is Message.File && msg.status == Message.File.Status.COMPLETED))
        ) {
            val sid = currentSessionId
            val faved = msg.id in favoritedIds
            if (sid != null) {
                add(MessageAction(
                    icon = if (faved) starPainter else starBorderPainter,
                    label = if (faved) "取消收藏" else "收藏",
                    onClick = {
                        actionTarget = null
                        if (faved) {
                            scope.launch { ServiceLocator.favoritesRepository.unfavoriteBySource(sid, msg.id) }
                        } else {
                            pendingFavoriteMsg = msg
                        }
                    },
                ))
            }
        }
        // 复制 — text only
        if (msg is Message.Text) {
            add(MessageAction(
                icon = copyPainter,
                label = "复制",
                onClick = {
                    scope.launch { clipboard.setPlainText(msg.content) }
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
    var previousAutoScrollMessageCount by remember { mutableStateOf(0) }
    var previousAutoScrollLastMessageId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(ui.messages.size, ui.messages.lastOrNull()?.id) {
        val currentMessageCount = ui.messages.size
        val currentLastMessageId = ui.messages.lastOrNull()?.id
        val shouldScroll = shouldAutoScrollToLatestMessage(
            previousMessageCount = previousAutoScrollMessageCount,
            currentMessageCount = currentMessageCount,
            previousLastMessageId = previousAutoScrollLastMessageId,
            currentLastMessageId = currentLastMessageId,
        )
        previousAutoScrollMessageCount = currentMessageCount
        previousAutoScrollLastMessageId = currentLastMessageId
        if (shouldScroll) {
            listState.animateScrollToItem(currentMessageCount - 1)
        }
    }
    // Dismiss the floating/inline action target whenever the list starts scrolling.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) actionTarget = null
    }
    // System-back dismisses the action target before exiting the screen.
    androidx.activity.compose.BackHandler(enabled = actionTarget != null) { actionTarget = null }
    // 会话进行中默认拦截返回，保护会话稳定（须点停止服务才离开）。开「允许会话中返回」后
    // 不拦截，系统返回正常弹回主页（服务仍运行，可从主页"继续服务"重进）。优先级低于上面关闭工具栏。
    androidx.activity.compose.BackHandler(
        enabled = actionTarget == null && !settings.allowBackDuringSession,
    ) {
        scope.launch { snackbarHostState.showSnackbar("会话进行中，请点右上角停止服务以退出") }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        // Edge-to-edge IME 标准写法（官方规范）：Scaffold 默认 innerPadding 含 systemBars（不含 ime）。
        // padding(innerPadding) 应用 systemBars → consumeWindowInsets 标记已消费 → imePadding() 再补 ime，
        // 三者配合保证 ime 只生效一次。配合 manifest 的 windowSoftInputMode=adjustResize。
        // 缺 consumeWindowInsets 或 adjustResize 都会导致键盘弹起时 pan/inset 叠加：输入行被顶到顶部、
        // 上方留出键盘高度空白（test2 §5 复盘）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier.fillMaxSize().maxContentWidth(),
        ) {
            NetworkStatusBanner(
                status = ui.networkStatus,
                onAcknowledge = { viewModel.acknowledgeNetworkSwitch() },
            )
            // 真·高度 morph 的 spec 在 composable 体内先取（AnimatedContent.transitionSpec 非 @Composable，
            // 与 NavTransitions 同套路：先取后闭包捕获）：容器高度走 spatial 弹簧（轻微回弹、受全局速度档统辖），
            // 内容仅快速淡入淡出换装。下方对话区随容器高度平滑顶起，不再因高度突变而跳动。
            val headerSizeSpec = Motion.spatial<IntSize>()
            val headerEnterFade = Motion.effects<Float>()
            val headerExitFade = Motion.effectsFast<Float>()
            AnimatedContent(
                targetState = ui.clientConnected,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(headerEnterFade),
                        initialContentExit = fadeOut(headerExitFade),
                        sizeTransform = SizeTransform { _, _ -> headerSizeSpec },
                    )
                },
                label = "ConnHeader",
            ) { connected ->
                if (connected) {
                    ConversationHeader(
                        peerAvatarId = peerAvatarId,
                        peerAvatarKey = peerAvatarKey,
                        peerName = "",
                        onAvatarClick = { showPeerAvatarPicker = true },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                // 快捷设置：会话期间「设置」tab 被锁，这里就近调气泡圆角 / 深色模式。
                                FilledTonalIconButton(onClick = { showQuickSettings = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings),
                                        contentDescription = "快捷设置",
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = { viewModel.stopService(); onStopped() },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_power),
                                        contentDescription = "停止服务",
                                    )
                                }
                            }
                        },
                    )
                } else {
                    Column(Modifier.padding(Spacing.sectionGap)) {
                        ConnectionInfoCard(url = ui.url, pin = ui.pin)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.padding(top = Spacing.lg).align(Alignment.CenterHorizontally),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                "等待浏览器连接…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        FilledTonalButton(
                            onClick = { viewModel.stopService(); onStopped() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
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
                        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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

                            Column(modifier = flikkyItemAnimation()) {
                                MessageBubble(
                                    msg = msg,
                                    onTap = {
                                        if (floating) {
                                            actionTarget = if (isActionTarget) null else msg.id
                                        } else if (msg is Message.File) {
                                            viewModel.openFile(msg)
                                        }
                                    },
                                    // 两种模式长按都让给 SelectionContainer 起划词选择：
                                    // floating 单击召唤工具栏；inline 操作栏常驻显示，无需长按。
                                    onLongPress = null,
                                    transferProgress = progressMap[msg.id],
                                    showAvatar = showAvatar,
                                    avatarId = if (msg.origin == Origin.PHONE) settings.phoneAvatarId
                                               else peerAvatarId,
                                    avatarKey = if (msg.origin == Origin.PHONE) settings.phoneAvatarKey
                                                else peerAvatarKey,
                                    cornerRadius = settings.bubbleCornerRadius.dp,
                                    selected = floating && isActionTarget,
                                )
                                if (!floating) {
                                    // 常驻模式：每条气泡下方固定显示操作栏，按 origin 与气泡同侧边缘对齐。
                                    val barAlignment = if (msg.origin == Origin.PHONE) Alignment.CenterEnd else Alignment.CenterStart
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                        contentAlignment = barAlignment,
                                    ) {
                                        MessageActionBar(
                                            visible = true,
                                            actions = buildActionsFor(msg),
                                        )
                                    }
                                    Spacer(Modifier.height(Spacing.xs))
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
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("输入消息") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    // 未连接时禁用输入框：不可点、不弹键盘，避免无连接时编辑/发送的边界态。
                    enabled = ui.clientConnected,
                )
                IconButton(
                    onClick = { showAttachSheet = true },
                    enabled = ui.clientConnected,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
                if (settings.favoriteBetaEnabled) {
                    IconButton(
                        onClick = { showFavoriteQuickSheet = true },
                        enabled = ui.clientConnected,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (showFavoriteQuickSheet) R.drawable.ic_star else R.drawable.ic_star_border
                            ),
                            contentDescription = "收藏",
                        )
                    }
                }
                // 圆形填充发送按钮（上箭头），与左侧 add 的线性按钮区分度更高。
                FilledIconButton(
                    onClick = { viewModel.sendText(draft); draft = "" },
                    enabled = draft.isNotBlank() && ui.clientConnected,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_upward),
                        contentDescription = "发送",
                    )
                }
            }

            // 统计行常显，键盘弹起时随整列上移、紧贴键盘上方（对齐底部）。这是预期布局，不要隐藏它。
            ConversationStatusRow(
                uptimeSeconds = ui.uptimeSeconds,
                fileCount = ui.fileCount,
                bytesPerSecond = ui.bytesPerSecond,
            )
        }
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

    if (showQuickSettings) {
        QuickSettingsSheet(
            bubbleCornerRadius = settings.bubbleCornerRadius,
            avatarGrouping = settings.avatarGrouping,
            darkMode = settings.darkMode,
            onSetBubbleCorner = { viewModel.setBubbleCornerRadius(it) },
            onSetAvatarGrouping = { viewModel.setAvatarGrouping(it) },
            onSetDarkMode = { viewModel.setDarkMode(it) },
            onDismiss = { showQuickSettings = false },
        )
    }

    if (showPeerAvatarPicker) {
        AvatarPickerSheet(
            title = "选择浏览器头像",
            currentKey = peerAvatarKey,
            fallbackKey = AvatarKey.DEFAULT_PEER,
            onSelect = { viewModel.setPeerAvatarKey(it); showPeerAvatarPicker = false },
            onDismiss = { showPeerAvatarPicker = false },
        )
    }

    if (settings.favoriteBetaEnabled && showFavoriteQuickSheet) {
        FavoriteQuickSheet(
            favorites = favorites,
            groups = favoriteGroups,
            recentFavoriteIds = settings.recentFavoriteIds,
            onSend = { favorite ->
                viewModel.sendFavorite(favorite)
                viewModel.recordRecentFavorite(favorite.id)
                scope.launch { snackbarHostState.showSnackbar("已发送收藏") }
            },
            onDismiss = { showFavoriteQuickSheet = false },
        )
    }

    if (settings.favoriteBetaEnabled) pendingFavoriteMsg?.let { msg ->
        FavoriteGroupPickerSheet(
            groups = favoriteGroups,
            onSelect = { groupId ->
                val sid = currentSessionId
                pendingFavoriteMsg = null
                if (sid == null) return@FavoriteGroupPickerSheet
                scope.launch {
                    runCatching { favoriteMessage(sid, "进行中会话", msg, groupId) }
                        .onFailure { snackbarHostState.showSnackbar("收藏失败：源文件不存在") }
                }
            },
            onCreateGroup = { name ->
                scope.launch {
                    val groupId = ServiceLocator.favoritesRepository.createGroup(name)
                    val sid = currentSessionId
                    val target = pendingFavoriteMsg
                    pendingFavoriteMsg = null
                    if (sid != null && target != null) {
                        runCatching { favoriteMessage(sid, "进行中会话", target, groupId) }
                            .onFailure { snackbarHostState.showSnackbar("收藏失败：源文件不存在") }
                    }
                }
            },
            onDismiss = { pendingFavoriteMsg = null },
        )
    }
}

private suspend fun favoriteMessage(sessionId: Long, sessionName: String?, msg: Message, groupId: Long?) {
    when (msg) {
        is Message.Text -> ServiceLocator.favoritesRepository.favoriteText(sessionId, sessionName, msg, groupId)
        is Message.File -> ServiceLocator.favoritesRepository.favoriteFile(sessionId, sessionName, msg, groupId)
    }
}
