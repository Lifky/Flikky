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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.di.ServiceLocator
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.ConversationBackground
import com.example.flikky.ui.components.ConversationStatusRow
import com.example.flikky.ui.components.ImagePreviewDialog
import com.example.flikky.ui.components.installApk
import com.example.flikky.ui.components.MessageAction
import com.example.flikky.ui.components.MessageActionBar
import com.example.flikky.ui.components.MessageBubble
import com.example.flikky.ui.components.MessageFloatingToolbarOverlay
import com.example.flikky.ui.components.SessionTimeDivider
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.saveToGallery
import com.example.flikky.ui.components.sessionFile
import com.example.flikky.ui.components.setPlainText
import com.example.flikky.ui.favorites.FavoriteGroupPickerSheet
import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.FilesListBuilder
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.SessionTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 会话页「会话」tab 的全部内容：消息列表、浮动操作栏、输入坞、统计行，
 * 以及只由这三者触发的 sheet / dialog。
 *
 * 输入区的加号统一承载添加附件与发送已有文件。
 *
 * `actionTarget` 提在 `ServingScreen`：两级 `BackHandler` 要读它，
 * 且切 tab 时要清它（否则浮动工具栏会浮在文件列表上、指向一条看不见的消息）。
 * 这里只通过 [onActionTargetChange] 写，不自己持有。
 *
 * 拆分时新增了一层 `Column` 容器：原先这三段是 `ServingScreen` 外层 Column 的直接子项。
 * 外层把 `weight(1f)` 给这个容器，容器内列表再取 `weight(1f)`，布局结果等价，只多一个节点。
 */
@Composable
fun ServingChatTab(
    viewModel: ServingViewModel,
    ui: ServingUiState,
    settings: FlikkySettings,
    progressMap: Map<Long, Float>,
    peerAvatarId: Int,
    peerAvatarKey: String,
    actionTarget: Long?,
    onActionTargetChange: (Long?) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    existingFiles: List<FileOverviewRow>,
    onSendExistingFile: (FileOverviewRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val timestampDividerIndices = remember(ui.messages) {
        SessionTimestamp.dividerIndices(ui.messages.map { it.timestamp })
    }
    var draft by remember { mutableStateOf("") }
    var recallTarget by remember { mutableStateOf<Long?>(null) }
    var pendingFavoriteMsg by remember { mutableStateOf<Message?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showFavoriteQuickSheet by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<java.io.File?>(null) }
    val clipboard = LocalClipboard.current
    val favoriteLabel = stringResource(R.string.serving_favorite)
    val unfavoriteLabel = stringResource(R.string.serving_unfavorite)
    val copyLabel = stringResource(R.string.serving_copy)
    val openLabel = stringResource(R.string.serving_open)
    val previewLabel = stringResource(R.string.files_action_preview)
    val galleryLabel = stringResource(R.string.files_action_gallery)
    val installLabel = stringResource(R.string.files_action_install)
    val recallLabel = stringResource(R.string.serving_recall)
    val deleteLabel = stringResource(R.string.serving_delete)
    val deletedMessage = stringResource(R.string.serving_deleted)
    val undoLabel = stringResource(R.string.serving_undo)
    val favoriteSentMessage = stringResource(R.string.serving_favorite_sent)
    val favoriteSourceMissingMessage = stringResource(R.string.serving_favorite_source_missing)
    val activeSessionName = stringResource(R.string.serving_active_session)
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
    val galleryPainter = painterResource(R.drawable.ic_visibility)
    val copyPainter = painterResource(R.drawable.ic_content_copy)
    val deletePainter = painterResource(R.drawable.ic_delete)
    val starPainter = painterResource(R.drawable.ic_star)
    val starBorderPainter = painterResource(R.drawable.ic_star_border)
    val installPainter = painterResource(R.drawable.ic_apk_install)
    val currentSessionId = ServiceLocator.session.snapshot.collectAsState().value.currentSessionId
    val installedApps by viewModel.installedApps.collectAsState()
    val appsLoading by viewModel.appsLoading.collectAsState()

    fun openOrPreview(msg: Message.File) {
        val file = currentSessionId?.let { sessionFile(it, msg.fileId) }
        if (msg.status == Message.File.Status.COMPLETED &&
            FilesListBuilder.categoryOf(msg.mime) == FileCategory.IMAGE && file?.exists() == true
        ) {
            previewImage = file
        } else {
            viewModel.openFile(msg)
        }
    }

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
        if (msg is Message.File && msg.status == Message.File.Status.COMPLETED &&
            FilesListBuilder.categoryOf(msg.mime) == FileCategory.APK
        ) {
            currentSessionId?.let { sid ->
                add(MessageAction(
                    icon = installPainter,
                    label = installLabel,
                    onClick = {
                        onActionTargetChange(null)
                        installApk(ctx, sessionFile(sid, msg.fileId), msg.name)
                    },
                ))
            }
        }
        if (settings.favoriteBetaEnabled &&
            (msg is Message.Text || (msg is Message.File && msg.status == Message.File.Status.COMPLETED))
        ) {
            val sid = currentSessionId
            val faved = msg.id in favoritedIds
            if (sid != null) {
                add(MessageAction(
                    icon = if (faved) starPainter else starBorderPainter,
                    label = if (faved) unfavoriteLabel else favoriteLabel,
                    onClick = {
                        onActionTargetChange(null)
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
                label = copyLabel,
                onClick = {
                    scope.launch { clipboard.setPlainText(msg.content) }
                    onActionTargetChange(null)
                },
            ))
        }
        // 打开/预览 — file COMPLETED。媒体文件的图标语义（用户定死）：
        // visibility=预览、file_download=存相册；非媒体维持 download 图标的「打开」。
        if (msg is Message.File && msg.status == Message.File.Status.COMPLETED) {
            val isMedia = FilesListBuilder.isMedia(msg.mime)
            add(MessageAction(
                icon = if (isMedia) galleryPainter else downloadPainter,
                label = if (isMedia) previewLabel else openLabel,
                onClick = {
                    openOrPreview(msg)
                    onActionTargetChange(null)
                },
            ))
        }
        if (msg is Message.File && msg.status == Message.File.Status.COMPLETED &&
            FilesListBuilder.isMedia(msg.mime)
        ) {
            val sid = currentSessionId
            if (sid != null) {
                add(MessageAction(
                    icon = downloadPainter,
                    label = galleryLabel,
                    onClick = {
                        onActionTargetChange(null)
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                saveToGallery(
                                    ctx,
                                    sessionFile(sid, msg.fileId),
                                    msg.name,
                                    msg.mime,
                                )
                            }
                            snackbarHostState.showSnackbar(
                                ctx.getString(
                                    if (saved) R.string.files_gallery_done
                                    else R.string.files_gallery_failed,
                                ),
                            )
                        }
                    },
                ))
            }
        }
        // 撤回 — 自己发送，或设置允许撤回对端消息；失败文件不提供撤回。
        if (canShowServingRecallAction(settings, msg)) {
            add(MessageAction(
                icon = undoPainter,
                label = recallLabel,
                onClick = {
                    recallTarget = msg.id
                    onActionTargetChange(null)
                },
            ))
        }
        // 删除 — always present
        add(MessageAction(
            icon = deletePainter,
            label = deleteLabel,
            danger = true,
            onClick = {
                val id = msg.id
                onActionTargetChange(null)
                viewModel.deleteLocalWithUndo(id)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoLabel,
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
    val currentMessages = rememberUpdatedState(ui.messages)
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
    // Keep a bottom-anchored conversation pinned through every frame of an IME resize.
    LaunchedEffect(Unit) {
        var previousViewportHeight = 0
        var wasAtBottomBeforeResize = true
        snapshotFlow {
            listState.layoutInfo.viewportSize.height to !listState.canScrollForward
        }
            .distinctUntilChanged()
            .collect { (currentViewportHeight, isAtBottom) ->
                val messages = currentMessages.value
                val shouldScroll = shouldKeepLatestMessageVisibleAfterViewportResize(
                    previousViewportHeight = previousViewportHeight,
                    currentViewportHeight = currentViewportHeight,
                    wasAtBottomBeforeResize = wasAtBottomBeforeResize,
                    currentMessageCount = messages.size,
                )
                if (shouldScroll) {
                    listState.scrollToItem(messages.lastIndex)
                }
                previousViewportHeight = currentViewportHeight
                wasAtBottomBeforeResize = if (shouldScroll) true else isAtBottom
            }
    }
    // Dismiss the floating/inline action target whenever the list starts scrolling.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) onActionTargetChange(null)
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                            if (
                                settings.sessionTimestampEnabled &&
                                index in timestampDividerIndices
                            ) {
                                SessionTimeDivider(SessionTimestamp.format(msg.timestamp))
                            }
                            MessageBubble(
                                msg = msg,
                                onTap = {
                                    if (floating) {
                                        onActionTargetChange(if (isActionTarget) null else msg.id)
                                    } else if (msg is Message.File) {
                                        openOrPreview(msg)
                                    }
                                },
                                // 两种模式长按都让给 SelectionContainer 起划词选择：
                                // floating 单击召唤工具栏；inline 操作栏常驻显示，无需长按。
                                onLongPress = null,
                                tapOpensFile = !floating,
                                transferProgress = progressMap[msg.id],
                                showAvatar = showAvatar,
                                avatarId = if (msg.origin == Origin.PHONE) settings.phoneAvatarId
                                           else peerAvatarId,
                                avatarKey = if (msg.origin == Origin.PHONE) settings.phoneAvatarKey
                                            else peerAvatarKey,
                                cornerRadius = settings.bubbleCornerRadius.dp,
                                selected = floating && isActionTarget,
                                thumbnailFile = (msg as? Message.File)
                                    ?.takeIf {
                                        it.status == Message.File.Status.COMPLETED &&
                                            FilesListBuilder.isMedia(it.mime)
                                    }
                                    ?.let { fileMsg ->
                                        currentSessionId?.let { sid ->
                                            sessionFile(sid, fileMsg.fileId).takeIf { it.exists() }
                                        }
                                    },
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
                // bottom 间距由 overlay 内部的阴影内衬承担，这里不再叠加。
                MessageFloatingToolbarOverlay(
                    visible = target != null,
                    actions = lastActions,
                    modifier = Modifier.align(Alignment.BottomCenter),
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
                placeholder = { Text(stringResource(R.string.serving_input_message)) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                // 未连接时禁用输入框：不可点、不弹键盘，避免无连接时编辑/发送的边界态。
                enabled = ui.clientConnected,
            )
            IconButton(
                onClick = { viewModel.ensureInstalledAppsLoaded(); showAttachSheet = true },
                enabled = ui.clientConnected,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.serving_add))
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
                        contentDescription = stringResource(R.string.serving_favorite),
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
                    contentDescription = stringResource(R.string.serving_send),
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

    // 撤回二次确认 AlertDialog。点错也能取消，避免误删（D26 修订）。
    recallTarget?.let { targetId ->
        AlertDialog(
            onDismissRequest = { recallTarget = null },
            title = { Text(stringResource(R.string.serving_recall_title)) },
            text = { Text(stringResource(R.string.serving_recall_text)) },
            confirmButton = {
                TextButton(onClick = {
                    recallTarget = null
                    viewModel.recallMessage(targetId)
                }) { Text(stringResource(R.string.serving_recall)) }
            },
            dismissButton = {
                TextButton(onClick = { recallTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showAttachSheet) {
        AttachBottomSheet(
            installedApps = installedApps,
            appsLoading = appsLoading,
            onSendApp = { app -> showAttachSheet = false; viewModel.sendInstalledApp(app) },
            existingFiles = existingFiles,
            onSendExistingFile = onSendExistingFile,
            onPickFile = { showAttachSheet = false; pickFile.launch("*/*") },
            onPickImage = { showAttachSheet = false; pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onDismiss = { showAttachSheet = false },
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
                scope.launch { snackbarHostState.showSnackbar(favoriteSentMessage) }
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
                    runCatching { favoriteMessage(sid, activeSessionName, msg, groupId) }
                        .onFailure { snackbarHostState.showSnackbar(favoriteSourceMissingMessage) }
                }
            },
            onCreateGroup = { name ->
                scope.launch {
                    val groupId = ServiceLocator.favoritesRepository.createGroup(name)
                    val sid = currentSessionId
                    val target = pendingFavoriteMsg
                    pendingFavoriteMsg = null
                    if (sid != null && target != null) {
                        runCatching { favoriteMessage(sid, activeSessionName, target, groupId) }
                            .onFailure { snackbarHostState.showSnackbar(favoriteSourceMissingMessage) }
                    }
                }
            },
            onDismiss = { pendingFavoriteMsg = null },
        )
    }

    previewImage?.let { file ->
        ImagePreviewDialog(file = file, onDismiss = { previewImage = null })
    }
}


private suspend fun favoriteMessage(sessionId: Long, sessionName: String?, msg: Message, groupId: Long?) {
    when (msg) {
        is Message.Text -> ServiceLocator.favoritesRepository.favoriteText(sessionId, sessionName, msg, groupId)
        is Message.File -> ServiceLocator.favoritesRepository.favoriteFile(sessionId, sessionName, msg, groupId)
    }
}
