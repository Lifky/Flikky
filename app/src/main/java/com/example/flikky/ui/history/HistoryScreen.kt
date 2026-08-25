package com.example.flikky.ui.history

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.di.ServiceLocator
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.MessageAction
import com.example.flikky.ui.components.MessageActionBar
import com.example.flikky.ui.components.MessageBubble
import com.example.flikky.ui.components.MessageFloatingToolbarOverlay
import com.example.flikky.ui.components.SessionTimeDivider
import com.example.flikky.ui.components.FlikkyFloatingToolbarLift
import com.example.flikky.ui.components.ImagePreviewDialog
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.components.openStoredFile
import com.example.flikky.ui.components.saveToGallery
import com.example.flikky.ui.components.sessionFile
import com.example.flikky.ui.components.setPlainText
import com.example.flikky.ui.favorites.FavoriteGroupPickerSheet
import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.FilesListBuilder
import com.example.flikky.ui.exporting.ExportDestinationSheet
import com.example.flikky.ui.home.HomeViewModel
import com.example.flikky.ui.home.MoveToGroupSheet
import com.example.flikky.export.ExportFileName
import com.example.flikky.export.ExportScope
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.SessionTimestamp
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onStartExport: () -> Unit = {},
    highlightMessageId: Long? = null,
) {
    val ctx = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(
            app = ctx.applicationContext as android.app.Application,
            sessionId = sessionId,
        ),
    )
    val homeViewModel: HomeViewModel = viewModel()
    val session by viewModel.session.collectAsState(initial = null)
    val messages by viewModel.messages.collectAsState()
    val groups by homeViewModel.groups.collectAsState(initial = emptyList())
    val settings by ServiceLocator.settingsRepository.settings.collectAsState(initial = FlikkySettings())
    val timestampDividerIndices = remember(messages) {
        SessionTimestamp.dividerIndices(messages.map { it.timestamp })
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showExportDestination by remember { mutableStateOf(false) }
    var showExportProgress by remember { mutableStateOf(false) }
    val inProgress = session?.endedAt == null && session != null
    var actionTarget by remember { mutableStateOf<Long?>(null) }
    var pendingFavoriteMsg by remember { mutableStateOf<Message?>(null) }
    var previewImage by remember { mutableStateOf<java.io.File?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val localExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            showExportProgress = true
            scope.launch {
                val message = try {
                    when (homeViewModel.saveExport(uri, listOf(sessionId))) {
                        HomeViewModel.ExportStartResult.Success ->
                            ctx.getString(R.string.home_export_saved)
                        HomeViewModel.ExportStartResult.NoValidSessions ->
                            ctx.getString(R.string.home_export_ineligible)
                        HomeViewModel.ExportStartResult.EmptySelection ->
                            ctx.getString(R.string.home_select_sessions_first)
                        HomeViewModel.ExportStartResult.TransferRunning ->
                            ctx.getString(R.string.home_save_failed)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ctx.getString(R.string.home_save_failed_location)
                } finally {
                    showExportProgress = false
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    val favoriteLabel = stringResource(R.string.history_favorite)
    val unfavoriteLabel = stringResource(R.string.history_unfavorite)
    val copyLabel = stringResource(R.string.history_copy)
    val openLabel = stringResource(R.string.history_open)
    val previewLabel = stringResource(R.string.files_action_preview)
    val galleryLabel = stringResource(R.string.files_action_gallery)
    val deleteLabel = stringResource(R.string.history_delete)
    val deletedMessage = stringResource(R.string.history_deleted)
    val undoLabel = stringResource(R.string.history_undo)
    val favoriteSourceMissingMessage = stringResource(R.string.history_favorite_source_missing)

    // v1.3 T20: scroll-to + flash-highlight when arriving from the home search.
    val listState = rememberLazyListState()
    var activeHighlight by remember { mutableStateOf<Long?>(highlightMessageId) }
    LaunchedEffect(highlightMessageId, messages) {
        val target = highlightMessageId ?: return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect
        val idx = messages.indexOfFirst { it.id == target }
        if (idx < 0) return@LaunchedEffect
        listState.animateScrollToItem(idx.coerceAtMost(messages.size - 1))
        // Keep the highlight visible briefly so the user can locate it,
        // then fade. 1.5s matches the v1.3 spec §3.1 sample.
        delay(1500L)
        activeHighlight = null
    }

    // Dismiss the action target whenever the list starts scrolling.
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) actionTarget = null }
    // System-back dismisses the action target before exiting the screen.
    androidx.activity.compose.BackHandler(enabled = actionTarget != null) { actionTarget = null }

    // Painters resolved once in composable scope (stable across recompositions),
    // shared by both the inline bar and the floating toolbar.
    val copyPainter = painterResource(R.drawable.ic_content_copy)
    val downloadPainter = painterResource(R.drawable.ic_file_download)
    val galleryPainter = painterResource(R.drawable.ic_visibility)
    val deletePainter = painterResource(R.drawable.ic_delete)
    val pinPainter = painterResource(R.drawable.ic_push_pin)
    val editPainter = painterResource(R.drawable.ic_edit)
    val movePainter = painterResource(R.drawable.ic_drive_file_move)
    val exportPainter = painterResource(R.drawable.ic_upload)
    val starPainter = painterResource(R.drawable.ic_star)
    val starBorderPainter = painterResource(R.drawable.ic_star_border)
    val favoriteGroups by if (settings.favoriteBetaEnabled) {
        ServiceLocator.favoritesRepository.observeGroups().collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val favoritedIds by if (settings.favoriteBetaEnabled) {
        ServiceLocator.favoritesRepository.observeFavoritedIds(sessionId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<Long>()) }
    }

    fun openOrPreview(msg: Message.File) {
        val file = sessionFile(sessionId, msg.fileId)
        if (msg.status == Message.File.Status.COMPLETED &&
            FilesListBuilder.categoryOf(msg.mime) == FileCategory.IMAGE && file.exists()
        ) {
            previewImage = file
        } else {
            openFile(ctx, sessionId, msg) {
                scope.launch { ServiceLocator.repository.markFileDeleted(msg.id) }
            }
        }
    }

    // Single source of truth for a message's available actions (History has no
    // recall): 复制（text）/ 打开（completed file）/ 删除. Each onClick clears the target.
    fun buildActionsFor(msg: Message): List<MessageAction> = buildList {
        if (settings.favoriteBetaEnabled &&
            (msg is Message.Text || (msg is Message.File && msg.status == Message.File.Status.COMPLETED))
        ) {
            val faved = msg.id in favoritedIds
            add(MessageAction(
                icon = if (faved) starPainter else starBorderPainter,
                label = if (faved) unfavoriteLabel else favoriteLabel,
                onClick = {
                    actionTarget = null
                    if (faved) {
                        scope.launch { ServiceLocator.favoritesRepository.unfavoriteBySource(sessionId, msg.id) }
                    } else {
                        pendingFavoriteMsg = msg
                    }
                },
            ))
        }
        if (msg is Message.Text) {
            add(MessageAction(
                icon = copyPainter,
                label = copyLabel,
                onClick = {
                    scope.launch { clipboard.setPlainText(msg.content) }
                    actionTarget = null
                },
            ))
        }
        // 媒体文件的图标语义（用户定死）：visibility=预览、file_download=存相册；
        // 非媒体维持 download 图标的「打开」。与 ServingScreen 保持一致。
        if (msg is Message.File && msg.status == Message.File.Status.COMPLETED) {
            val isMedia = FilesListBuilder.isMedia(msg.mime)
            add(MessageAction(
                icon = if (isMedia) galleryPainter else downloadPainter,
                label = if (isMedia) previewLabel else openLabel,
                onClick = {
                    openOrPreview(msg)
                    actionTarget = null
                },
            ))
        }
        if (msg is Message.File && msg.status == Message.File.Status.COMPLETED &&
            FilesListBuilder.isMedia(msg.mime)
        ) {
            add(MessageAction(
                icon = downloadPainter,
                label = galleryLabel,
                onClick = {
                    actionTarget = null
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            saveToGallery(
                                ctx,
                                sessionFile(sessionId, msg.fileId),
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
        add(MessageAction(
            icon = deletePainter,
            label = deleteLabel,
            danger = true,
            onClick = {
                val id = msg.id
                actionTarget = null
                viewModel.deleteLocalWithUndo(id)
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoLabel,
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

    // floating 浮动操作栏可见时，snackbar 与消息列表底部都要为它让位。
    val floatingToolbarShown = settings.messageActionStyle ==
        com.example.flikky.data.settings.MessageActionStyle.FLOATING &&
        messages.any { it.id == actionTarget }

    Scaffold(
        snackbarHost = {
            // floating 浮动操作栏与 snackbar 都锚在底部中央，会相互遮挡（snackbar 盖住操作栏挡点击）。
            // 浮动栏可见时把 snackbar 抬到栏上方（栏高 + 栏自身 bottom 间距 + 一档间隙），让 snackbar
            // 浮于栏之上、两者都可见且不挡操作；用 effects 平滑升降避免 snackbar 与栏同现时跳变。
            val snackbarLift by animateDpAsState(
                targetValue = if (floatingToolbarShown) FlikkyFloatingToolbarLift else 0.dp,
                animationSpec = Motion.effects(),
                label = "snackbarLift",
            )
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = snackbarLift),
            ) { Snackbar(it) }
        },
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: stringResource(R.string.history_session_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }, enabled = !inProgress) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val pinned = session?.pinned == true
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (pinned) R.string.history_unpin else R.string.history_pin))
                            },
                            onClick = { menuExpanded = false; viewModel.setPinned(!pinned) },
                            leadingIcon = { Icon(pinPainter, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_rename)) },
                            onClick = { menuExpanded = false; showRename = true },
                            leadingIcon = { Icon(editPainter, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_move_to_group)) },
                            onClick = { menuExpanded = false; showMoveSheet = true },
                            leadingIcon = { Icon(movePainter, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_export)) },
                            onClick = { menuExpanded = false; showExportDestination = true },
                            leadingIcon = { Icon(exportPainter, contentDescription = null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_delete)) },
                            onClick = { menuExpanded = false; showDelete = true },
                            leadingIcon = { Icon(deletePainter, contentDescription = null) },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                                leadingIconColor = MaterialTheme.colorScheme.error,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { pad ->
      Box(
          modifier = Modifier.padding(pad).fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
      ) {
        // 浮动操作栏悬浮在消息之上，抬升底部 padding 让末条消息可滚出栏上方。
        val listBottomLift by animateDpAsState(
            targetValue = if (floatingToolbarShown) FlikkyFloatingToolbarLift else 0.dp,
            animationSpec = Motion.effects(),
            label = "historyListBottomLift",
        )
        SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize().maxContentWidth().padding(horizontal = Spacing.md),
            contentPadding = PaddingValues(bottom = listBottomLift),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            state = listState,
        ) {
            itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
                val prevMsg = if (index > 0) messages[index - 1] else null
                val nextMsg = if (index < messages.size - 1) messages[index + 1] else null
                val showAvatar = when (settings.avatarGrouping) {
                    com.example.flikky.data.settings.AvatarGroupingMode.FIRST ->
                        prevMsg == null || prevMsg.origin != msg.origin
                    com.example.flikky.data.settings.AvatarGroupingMode.LAST ->
                        nextMsg == null || nextMsg.origin != msg.origin
                    com.example.flikky.data.settings.AvatarGroupingMode.EACH -> true
                }
                val isHighlighted = msg.id == activeHighlight
                val highlightColor by animateColorAsState(
                    targetValue = if (isHighlighted) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = Motion.durationSpec(durationMillis = Motion.Long4),
                    label = "search-highlight",
                )
                val isActionTarget = actionTarget == msg.id
                val floating = settings.messageActionStyle ==
                    com.example.flikky.data.settings.MessageActionStyle.FLOATING

                Box(
                    modifier = flikkyItemAnimation()
                        .fillMaxWidth()
                        .background(highlightColor),
                ) {
                    Column {
                        if (
                            settings.sessionTimestampEnabled &&
                            index in timestampDividerIndices
                        ) {
                            SessionTimeDivider(SessionTimestamp.format(msg.timestamp))
                        }
                        MessageBubble(
                            msg = msg,
                            onTap = {
                                // floating 必须最先判：已删除消息也要能召出工具栏（剩「删除」
                                // 可清掉残留记录），否则单击只会走 openFile 的 Toast 短路。
                                if (floating) {
                                    actionTarget = if (isActionTarget) null else msg.id
                                } else if (msg is Message.File) {
                                    // DELETED 的「文件已删除」Toast 由 openFile 内部兜底。
                                    openOrPreview(msg)
                                }
                            },
                            // 两种模式长按都让给 SelectionContainer 起划词选择：
                            // floating 单击召唤工具栏；inline 操作栏常驻显示，无需长按。
                            onLongPress = null,
                            tapOpensFile = !floating,
                            showAvatar = showAvatar,
                            avatarId = if (msg.origin == Origin.PHONE) settings.phoneAvatarId
                                       else (session?.peerAvatarId ?: 0),
                            avatarKey = if (msg.origin == Origin.PHONE) settings.phoneAvatarKey
                                        else session?.peerAvatarKey,
                            cornerRadius = settings.bubbleCornerRadius.dp,
                            selected = floating && isActionTarget,
                            thumbnailFile = (msg as? Message.File)
                                ?.takeIf {
                                    it.status == Message.File.Status.COMPLETED &&
                                        FilesListBuilder.isMedia(it.mime)
                                }
                                ?.let { fileMsg ->
                                    sessionFile(sessionId, fileMsg.fileId).takeIf { it.exists() }
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
                            androidx.compose.foundation.layout.Spacer(Modifier.height(Spacing.xs))
                        }
                    }
                }
            }
        }
        }

        // Floating action toolbar: one bottom-center bar for the selected message.
        // lastActions keeps content during the exit animation so it doesn't blank.
        val floating = settings.messageActionStyle ==
            com.example.flikky.data.settings.MessageActionStyle.FLOATING
        if (floating) {
            val target = messages.firstOrNull { it.id == actionTarget }
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
    }

    if (showRename) {
        var draft by remember { mutableStateOf(session?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.common_rename_session)) },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { showRename = false; viewModel.rename(draft) }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.history_delete_session_title)) },
            text = { Text(stringResource(R.string.history_delete_session_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false; viewModel.delete(); onBack()
                }) { Text(stringResource(R.string.history_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    if (showExportProgress) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.home_saving)) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
        )
    }
    if (showMoveSheet) {
        MoveToGroupSheet(
            groups = groups,
            onSelect = { targetGroupId ->
                showMoveSheet = false
                val targetName =
                    if (targetGroupId == null) ctx.getString(R.string.home_all_groups)
                    else groups.firstOrNull { it.id == targetGroupId }?.name
                        ?: ctx.getString(R.string.home_group_fallback)
                scope.launch {
                    homeViewModel.moveSessionToGroup(sessionId, targetGroupId)
                    snackbarHostState.showSnackbar(
                        ctx.resources.getQuantityString(
                            R.plurals.home_sessions_moved,
                            1,
                            1,
                            targetName,
                        ),
                    )
                }
            },
            onDismiss = { showMoveSheet = false },
        )
    }
    if (showExportDestination) {
        ExportDestinationSheet(
            onSaveLocal = {
                showExportDestination = false
                localExportLauncher.launch(
                    ExportFileName.build(ExportScope.SESSIONS, System.currentTimeMillis()),
                )
            },
            onDownloadToComputer = {
                showExportDestination = false
                scope.launch {
                    when (homeViewModel.startExport(listOf(sessionId))) {
                        HomeViewModel.ExportStartResult.Success -> onStartExport()
                        HomeViewModel.ExportStartResult.TransferRunning ->
                            snackbarHostState.showSnackbar(
                                ctx.getString(R.string.home_stop_service_first),
                            )
                        HomeViewModel.ExportStartResult.NoValidSessions ->
                            snackbarHostState.showSnackbar(
                                ctx.getString(R.string.home_export_ineligible),
                            )
                        HomeViewModel.ExportStartResult.EmptySelection ->
                            snackbarHostState.showSnackbar(
                                ctx.getString(R.string.home_select_sessions_first),
                            )
                    }
                }
            },
            onDismiss = { showExportDestination = false },
        )
    }
    if (settings.favoriteBetaEnabled) pendingFavoriteMsg?.let { msg ->
        FavoriteGroupPickerSheet(
            groups = favoriteGroups,
            onSelect = { groupId ->
                pendingFavoriteMsg = null
                scope.launch {
                    runCatching { favoriteHistoryMessage(sessionId, session?.name, msg, groupId) }
                        .onFailure { snackbarHostState.showSnackbar(favoriteSourceMissingMessage) }
                }
            },
            onCreateGroup = { name ->
                scope.launch {
                    val groupId = ServiceLocator.favoritesRepository.createGroup(name)
                    val target = pendingFavoriteMsg
                    pendingFavoriteMsg = null
                    if (target != null) {
                        runCatching { favoriteHistoryMessage(sessionId, session?.name, target, groupId) }
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

private suspend fun favoriteHistoryMessage(sessionId: Long, sessionName: String?, msg: Message, groupId: Long?) {
    when (msg) {
        is Message.Text -> ServiceLocator.favoritesRepository.favoriteText(sessionId, sessionName, msg, groupId)
        is Message.File -> ServiceLocator.favoritesRepository.favoriteFile(sessionId, sessionName, msg, groupId)
    }
}

private fun openFile(
    ctx: Context,
    sessionId: Long,
    msg: Message.File,
    onMissing: () -> Unit = {},
) {
    if (msg.status == Message.File.Status.DELETED) {
        Toast.makeText(ctx, R.string.file_deleted_hint, Toast.LENGTH_SHORT).show()
        return
    }
    if (msg.status != Message.File.Status.COMPLETED) return
    openStoredFile(ctx, sessionId, msg.fileId, msg.name, msg.mime, onMissing)
}
