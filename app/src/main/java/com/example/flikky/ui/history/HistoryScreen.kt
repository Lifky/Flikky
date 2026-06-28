package com.example.flikky.ui.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.favorites.FavoriteGroupPickerSheet
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessionId: Long,
    onBack: () -> Unit,
    highlightMessageId: Long? = null,
) {
    val ctx = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(
            app = ctx.applicationContext as android.app.Application,
            sessionId = sessionId,
        ),
    )
    val session by viewModel.session.collectAsState(initial = null)
    val messages by viewModel.messages.collectAsState()
    val settings by ServiceLocator.settingsRepository.settings.collectAsState(initial = FlikkySettings())
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val inProgress = session?.endedAt == null && session != null
    var actionTarget by remember { mutableStateOf<Long?>(null) }
    var pendingFavoriteMsg by remember { mutableStateOf<Message?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
    val deletePainter = painterResource(R.drawable.ic_delete)
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

    // Single source of truth for a message's available actions (History has no
    // recall): 复制（text）/ 打开（completed file）/ 删除. Each onClick clears the target.
    fun buildActionsFor(msg: Message): List<MessageAction> = buildList {
        if (settings.favoriteBetaEnabled &&
            (msg is Message.Text || (msg is Message.File && msg.status == Message.File.Status.COMPLETED))
        ) {
            val faved = msg.id in favoritedIds
            add(MessageAction(
                icon = if (faved) starPainter else starBorderPainter,
                label = if (faved) "取消收藏" else "收藏",
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
                label = "复制",
                onClick = {
                    clipboardManager.setText(AnnotatedString(msg.content))
                    actionTarget = null
                },
            ))
        }
        if (msg is Message.File && msg.status == Message.File.Status.COMPLETED) {
            add(MessageAction(
                icon = downloadPainter,
                label = "打开",
                onClick = {
                    openFile(ctx, sessionId, msg)
                    actionTarget = null
                },
            ))
        }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "会话") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }, enabled = !inProgress) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val pinned = session?.pinned == true
                        DropdownMenuItem(
                            text = { Text(if (pinned) "取消置顶" else "置顶") },
                            onClick = { menuExpanded = false; viewModel.setPinned(!pinned) },
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = { menuExpanded = false; showRename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = { menuExpanded = false; showDelete = true },
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
        SelectionContainer {
        LazyColumn(
            modifier = Modifier.fillMaxSize().maxContentWidth().padding(horizontal = Spacing.md),
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
                        MessageBubble(
                            msg = msg,
                            onTap = {
                                if (floating) {
                                    actionTarget = if (isActionTarget) null else msg.id
                                } else if (msg is Message.File) {
                                    openFile(ctx, sessionId, msg)
                                }
                            },
                            // 两种模式长按都让给 SelectionContainer 起划词选择：
                            // floating 单击召唤工具栏；inline 操作栏常驻显示，无需长按。
                            onLongPress = null,
                            showAvatar = showAvatar,
                            avatarId = if (msg.origin == Origin.PHONE) settings.phoneAvatarId
                                       else (session?.peerAvatarId ?: 0),
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
            MessageFloatingToolbarOverlay(
                visible = target != null,
                actions = lastActions,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.md),
            )
        }
      }
    }

    if (showRename) {
        var draft by remember { mutableStateOf(session?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { showRename = false; viewModel.rename(draft) }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除会话") },
            text = { Text("将删除此会话的所有消息与文件。该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false; viewModel.delete(); onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            },
        )
    }
    if (settings.favoriteBetaEnabled) pendingFavoriteMsg?.let { msg ->
        FavoriteGroupPickerSheet(
            groups = favoriteGroups,
            onSelect = { groupId ->
                pendingFavoriteMsg = null
                scope.launch {
                    runCatching { favoriteHistoryMessage(sessionId, session?.name, msg, groupId) }
                        .onFailure { snackbarHostState.showSnackbar("收藏失败：源文件不存在") }
                }
            },
            onCreateGroup = { name ->
                scope.launch {
                    val groupId = ServiceLocator.favoritesRepository.createGroup(name)
                    val target = pendingFavoriteMsg
                    pendingFavoriteMsg = null
                    if (target != null) {
                        runCatching { favoriteHistoryMessage(sessionId, session?.name, target, groupId) }
                            .onFailure { snackbarHostState.showSnackbar("收藏失败：源文件不存在") }
                    }
                }
            },
            onDismiss = { pendingFavoriteMsg = null },
        )
    }
}

private suspend fun favoriteHistoryMessage(sessionId: Long, sessionName: String?, msg: Message, groupId: Long?) {
    when (msg) {
        is Message.Text -> ServiceLocator.favoritesRepository.favoriteText(sessionId, sessionName, msg, groupId)
        is Message.File -> ServiceLocator.favoritesRepository.favoriteFile(sessionId, sessionName, msg, groupId)
    }
}

private fun openFile(ctx: Context, sessionId: Long, msg: Message.File) {
    if (msg.status != Message.File.Status.COMPLETED) return
    val f = File(File(File(ctx.filesDir, "sessions/$sessionId"), "files"), msg.fileId)
    if (!f.exists()) {
        Toast.makeText(ctx, "文件不存在", Toast.LENGTH_SHORT).show(); return
    }
    val authority = "${ctx.packageName}.fileprovider"
    val uri = try {
        FileProvider.getUriForFile(ctx, authority, f, msg.name)
    } catch (e: IllegalArgumentException) {
        Toast.makeText(ctx, "无法暴露此文件（FileProvider 路径未配置）", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, msg.mime.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        ctx.startActivity(Intent.createChooser(intent, "打开文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "没有可以打开此类型文件的应用", Toast.LENGTH_SHORT).show()
    }
}
