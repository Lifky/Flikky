package com.example.flikky.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.data.db.entities.SessionEntity
import com.example.flikky.ui.components.ConfirmDialog
import com.example.flikky.ui.components.FlikkyFloatingToolbar
import com.example.flikky.ui.components.FlikkySelectingToolbarOverlay
import com.example.flikky.ui.components.RenameDialog
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenSession: (Long) -> Unit,
    onStartService: () -> Unit,
    onStartExport: () -> Unit = {},
    onOpenSearchHit: (Long, Long) -> Unit = { _, _ -> },
    onSelectingChange: (Boolean) -> Unit = {},
    onSearchExpandedChange: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val homeItems by viewModel.homeItems.collectAsState(initial = emptyList())
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val activeGroupId by viewModel.activeGroupId.collectAsState(initial = null)
    val searchEnabled by viewModel.searchEnabled.collectAsState(initial = true)
    val selection by viewModel.selection.collectAsState()
    val selecting by viewModel.selecting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // SearchBar 展开态上提到这里：用于隐藏 FAB，并上报给 MainActivity 隐藏底栏 + 让主页铺满全屏。
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(searchEnabled) {
        if (!searchEnabled && searchExpanded) searchExpanded = false
    }
    LaunchedEffect(searchExpanded) { onSearchExpandedChange(searchExpanded) }

    var showImportDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportDialog = true
            scope.launch {
                val result = viewModel.importFromZip(uri)
                showImportDialog = false
                val msg = buildString {
                    if (result.imported.isNotEmpty())
                        append("已导入 ${result.imported.size} 个会话")
                    if (result.skipped.isNotEmpty()) {
                        if (isNotEmpty()) append("，")
                        append("跳过 ${result.skipped.size} 个重复")
                    }
                    if (result.errors.isNotEmpty()) {
                        if (isNotEmpty()) append("，")
                        append("${result.errors.size} 个失败")
                    }
                    if (isEmpty()) append("未找到可导入的会话")
                }
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    // 是否有进行中会话——影响 FAB 文案、点击行为、会话项的 trailing 按钮。
    val hasInProgress = sessions.any { it.endedAt == null }

    fun startAndNavigate() {
        viewModel.startService()
        onStartService()
    }

    fun resumeNavigate() {
        // 已有进行中服务，只跳到 ServingScreen 不重新启服。
        onStartService()
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "通知权限被拒：服务仍运行，但通知栏不会显示状态", Toast.LENGTH_LONG).show()
        }
        startAndNavigate()
    }

    // Back gesture: in Selecting, exit selection instead of leaving the app.
    BackHandler(enabled = selecting) { viewModel.exitSelecting() }

    val selectedIds = selection ?: emptySet()
    val selectedCount = selectedIds.size
    val validSessionIds = remember(sessions) { sessions.filter { it.endedAt != null }.map { it.id } }

    val selectedSessions = remember(sessions, selectedIds) { sessions.filter { it.id in selectedIds } }
    val allSelectedPinned = selectedSessions.isNotEmpty() && selectedSessions.all { it.pinned }
    val singleSelected = selectedSessions.singleOrNull()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var managingGroup by remember { mutableStateOf<GroupEntity?>(null) }

    // 上报多选态给 MainActivity，用于多选时隐藏底部导航。
    LaunchedEffect(selecting) { onSelectingChange(selecting) }

    Scaffold(
        // 底部导航由 MainActivity 外层 Box(padding(bottom = NavBar inset)) 统一管，这里的内层
        // Scaffold 不能再消费底部 window inset——否则删掉 bottomBar 后 body 会自补一份系统手势条
        // inset，与外层叠成双重 inset，在 NavBar 上方多出一截空白条。只保留 top + horizontal。
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            if (selecting) {
                SelectingTopBar(
                    selectedCount = selectedCount,
                    onClose = { viewModel.exitSelecting() },
                    onSelectAll = { viewModel.selectAll(validSessionIds) },
                    selectAllEnabled = validSessionIds.isNotEmpty(),
                )
            } else if (searchEnabled) {
                HomeSearchBar(
                    sessions = sessions,
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    onOpenSession = onOpenSession,
                    onResume = { resumeNavigate() },
                    onOpenMessageHit = onOpenSearchHit,
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                )
            } else {
                LaunchedEffect(Unit) { onSearchExpandedChange(false) }
            }
        },
        floatingActionButton = {
            // FAB 显隐用 scale+fade 而非硬切。scale 走 spatial（弹簧 pop）：缩放过冲是短暂放大，
            // 不像底栏滑动那样会露缝，安全且更贴 MD3 FAB 入场。
            AnimatedVisibility(
                visible = !selecting && !searchExpanded,
                enter = scaleIn(Motion.spatial()) + fadeIn(Motion.effects()),
                exit = scaleOut(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (hasInProgress) {
                            // 已有正在进行的会话——FAB 是"继续服务"，直接进 ServingScreen。
                            resumeNavigate()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startAndNavigate()
                            else notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startAndNavigate()
                        }
                    },
                    text = { Text(if (hasInProgress) "继续服务" else "启动服务") },
                    icon = {
                        if (hasInProgress) Icon(Icons.Default.PlayArrow, contentDescription = "继续服务")
                        else Icon(Icons.Default.Add, contentDescription = "启动服务")
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (activeGroupId == null && sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    EmptyHero(modifier = Modifier.fillMaxSize().maxContentWidth())
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(modifier = Modifier.fillMaxSize().maxContentWidth()) {
                        // chip 行入场也播动画（与收藏页一致）：用 MutableTransitionState 从 false 起步，
                        // 首次组合即 false→true 播 expand 入场（往下展开），而非默认「首帧即可见、不动画」。
                        // selecting / 搜索展开切换时照常 expand/shrink 收放。
                        val chipVisible = remember { MutableTransitionState(false) }
                        chipVisible.targetState = !selecting && !searchExpanded
                        AnimatedVisibility(visibleState = chipVisible) {
                            GroupChips(
                                groups = groups,
                                activeGroupId = activeGroupId,
                                onSelect = { viewModel.setActiveGroup(it) },
                                onAdd = { showCreateGroupDialog = true },
                                onManage = { managingGroup = it },
                            )
                        }
                        if (homeItems.isEmpty() && activeGroupId != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(Spacing.screenEdge),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "该分组还没有会话",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // Per-position (indexInRun, runSize) for the segmented corner shapes:
                            // each date/status section is one connected segmented group.
                            val segPositions = remember(homeItems) {
                                HomeListBuilder.segmentPositions(homeItems)
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = Spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                itemsIndexed(
                                    homeItems,
                                    key = { _, item ->
                                        when (item) {
                                            is HomeListItem.Header -> "h:${item.label}"
                                            is HomeListItem.SessionItem -> "s:${item.session.id}"
                                        }
                                    },
                                ) { index, item ->
                                    when (item) {
                                        is HomeListItem.Header ->
                                            SectionHeader(item.label, modifier = flikkyItemAnimation())
                                        is HomeListItem.SessionItem -> {
                                            val s = item.session
                                            val (posInRun, runSize) = segPositions[index]
                                            SessionRow(
                                                s = s,
                                                selecting = selecting,
                                                checked = s.id in selectedIds,
                                                positionInRun = posInRun,
                                                runSize = runSize,
                                                onNormalClick = {
                                                    if (s.endedAt == null) resumeNavigate() else onOpenSession(s.id)
                                                },
                                                onEnterSelecting = { viewModel.toggleSelection(s.id) },
                                                onToggleSelection = { viewModel.toggleSelection(s.id) },
                                                onStopInProgress = { viewModel.stopService() },
                                                modifier = flikkyItemAnimation(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 多选悬浮操作栏：作为内容区 overlay 浮在列表之上（不占 bottomBar 槽位，避免预留空白挡内容）。
            FlikkySelectingToolbarOverlay(
                visible = selecting,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                SelectingFloatingToolbar(
                    selectedCount = selectedCount,
                    allPinned = allSelectedPinned,
                    onPinToggle = { scope.launch { viewModel.pinSelected(!allSelectedPinned) } },
                    onRename = { showRenameDialog = true },
                    onMove = { showMoveSheet = true },
                    onExport = {
                        scope.launch {
                            when (viewModel.startExport()) {
                                HomeViewModel.ExportStartResult.Success -> onStartExport()
                                HomeViewModel.ExportStartResult.TransferRunning ->
                                    snackbarHostState.showSnackbar("请先停止当前服务")
                                HomeViewModel.ExportStartResult.NoValidSessions ->
                                    snackbarHostState.showSnackbar("所选会话无法导出（可能都在进行中）")
                                HomeViewModel.ExportStartResult.EmptySelection ->
                                    snackbarHostState.showSnackbar("请先勾选会话")
                            }
                        }
                    },
                    onDelete = { if (selectedCount > 0) showBatchDeleteDialog = true },
                )
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("正在导入...") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
        )
    }

    if (showRenameDialog && singleSelected != null) {
        RenameDialog(
            initial = singleSelected.name,
            onConfirm = { name ->
                showRenameDialog = false
                scope.launch { viewModel.renameSelected(name) }
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showMoveSheet) {
        MoveToGroupSheet(
            groups = groups,
            onSelect = { targetGroupId ->
                showMoveSheet = false
                val targetName =
                    if (targetGroupId == null) "全部"
                    else groups.firstOrNull { it.id == targetGroupId }?.name ?: "分组"
                scope.launch {
                    val count = viewModel.moveSelectedToGroup(targetGroupId)
                    if (count > 0) {
                        snackbarHostState.showSnackbar("已移动 $count 个会话到「$targetName」")
                    }
                }
            },
            onDismiss = { showMoveSheet = false },
        )
    }

    if (showCreateGroupDialog) {
        GroupNameDialog(
            title = "新建分组",
            initial = "",
            onConfirm = { name ->
                showCreateGroupDialog = false
                viewModel.createGroup(name)
            },
            onDismiss = { showCreateGroupDialog = false },
        )
    }

    managingGroup?.let { group ->
        val ordered = groups
            .sortedWith(compareBy<GroupEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        val idx = ordered.indexOfFirst { it.id == group.id }
        GroupManageDialog(
            group = group,
            canMoveUp = idx > 0,
            canMoveDown = idx >= 0 && idx < ordered.lastIndex,
            onSave = { name -> viewModel.renameGroup(group.id, name) },
            onMoveUp = {
                if (idx > 0) {
                    val ids = ordered.map { it.id }.toMutableList()
                    val tmp = ids[idx]; ids[idx] = ids[idx - 1]; ids[idx - 1] = tmp
                    viewModel.reorderGroups(ids)
                }
            },
            onMoveDown = {
                if (idx in 0 until ordered.lastIndex) {
                    val ids = ordered.map { it.id }.toMutableList()
                    val tmp = ids[idx]; ids[idx] = ids[idx + 1]; ids[idx + 1] = tmp
                    viewModel.reorderGroups(ids)
                }
            },
            onDelete = {
                scope.launch {
                    val token = viewModel.deleteGroupWithUndo(group.id)
                    if (token != null) {
                        val result = snackbarHostState.showSnackbar(
                            message = "已删除分组「${group.name}」",
                            actionLabel = "撤销",
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreGroup(token.first, token.second)
                        }
                    }
                }
            },
            onDismiss = { managingGroup = null },
        )
    }

    if (showBatchDeleteDialog) {
        ConfirmDialog(
            title = "删除 $selectedCount 个会话",
            text = "将删除所选会话的全部消息与文件。该操作不可撤销。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                showBatchDeleteDialog = false
                scope.launch { viewModel.deleteSelected() }
            },
            onDismiss = { showBatchDeleteDialog = false },
        )
    }
}

@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm)
            .semantics { heading() },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SelectingTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    selectAllEnabled: Boolean,
) {
    TopAppBar(
        title = { Text("已选 $selectedCount 个") },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "关闭") }
        },
        actions = {
            TextButton(onClick = onSelectAll, enabled = selectAllEnabled) { Text("全选") }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

/**
 * 多选态的悬浮操作栏：容器走 [FlikkyFloatingToolbar]（已迁官方 `HorizontalFloatingToolbar`），
 * 内含纯图标按钮。
 */
@Composable
private fun SelectingFloatingToolbar(
    selectedCount: Int,
    allPinned: Boolean,
    onPinToggle: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled = selectedCount > 0
    FlikkyFloatingToolbar {
        IconButton(onClick = onPinToggle, enabled = enabled) {
            Icon(
                painterResource(R.drawable.ic_push_pin),
                contentDescription = if (allPinned) "取消置顶" else "置顶",
            )
        }
        if (selectedCount == 1) {
            IconButton(onClick = onRename) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "重命名")
            }
        }
        IconButton(onClick = onMove, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_drive_file_move), contentDescription = "移动到分组")
        }
        IconButton(onClick = onExport, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_upload), contentDescription = "导出")
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = "删除",
                tint = if (enabled) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        }
    }
}

@Composable
private fun EmptyHero(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Flikky", style = MaterialTheme.typography.displayMedium)
            Text(
                "局域网文件与消息传输",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Text(
                "尚无历史会话。点右下「启动服务」开始第一次传输。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.lg),
            )
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial.take(12)) }
    val trimmed = draft.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(12) },
                singleLine = true,
                label = { Text("分组名") },
                supportingText = { Text("${draft.length}/12") },
                isError = draft.isNotEmpty() && trimmed.isEmpty(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty(),
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SessionRow(
    s: SessionEntity,
    selecting: Boolean,
    checked: Boolean,
    positionInRun: Int,
    runSize: Int,
    onNormalClick: () -> Unit,
    onEnterSelecting: () -> Unit,
    onToggleSelection: () -> Unit,
    onStopInProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inProgress = s.endedAt == null
    val selectable = selecting && !inProgress
    val dimmed = selecting && inProgress
    val selectedNow = selectable && checked
    val haptic = LocalHapticFeedback.current

    // Content color matches the resolved container: onPrimaryContainer when selected, else onSurface.
    val onContent = if (selectedNow) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface

    val shapes = ListItemDefaults.segmentedShapes(index = positionInRun, count = runSize)
    // Three-state container, animated by the official InteractiveListItem selection spring:
    // selected -> primaryContainer, pinned (outside multi-select) -> secondaryContainer, else surfaceContainer.
    val colors = ListItemDefaults.segmentedColors(
        containerColor = if (!selecting && s.pinned) MaterialTheme.colorScheme.secondaryContainer
                         else MaterialTheme.colorScheme.surfaceContainer,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    val rowModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.screenEdge)
        .then(if (dimmed) Modifier.alpha(0.5f) else Modifier)
        .then(
            if (selectable) Modifier.semantics {
                selected = checked
                stateDescription = if (checked) "已选中" else "未选中"
            } else Modifier
        )

    val headline: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (s.pinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin),
                    contentDescription = "已置顶",
                    modifier = Modifier.size(16.dp),
                    tint = if (selectedNow) onContent else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(text = s.name, style = MaterialTheme.typography.titleMedium, color = onContent)
        }
    }
    val supporting: @Composable () -> Unit = {
        Column {
            Text(
                text = when {
                    selecting && inProgress -> "结束服务后可选择"
                    else -> s.previewText ?: "${s.messageCount} 条消息 · ${s.fileCount} 文件"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedNow) onContent.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = if (inProgress) "进行中" else formatRelative(s.startedAt),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    selectedNow -> onContent.copy(alpha = 0.8f)
                    inProgress  -> MaterialTheme.colorScheme.primary
                    else        -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
    val trailing: (@Composable () -> Unit)? = if (inProgress && !selecting) {
        {
            TextButton(onClick = onStopInProgress) {
                Text("停止", color = MaterialTheme.colorScheme.error)
            }
        }
    } else null

    if (selectable) {
        // Multi-select: the `selected` overload gives the built-in selection spring (shape + color morph).
        SegmentedListItem(
            selected = checked,
            onClick = onToggleSelection,
            shapes = shapes,
            modifier = rowModifier,
            colors = colors,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    } else {
        // Normal / in-progress: clickable overload. Long-press (on an ended session) enters multi-select.
        val clickAction: () -> Unit = if (selecting) {
            {}
        } else {
            onNormalClick
        }
        val longPress: (() -> Unit)? = if (selecting) null else {
            {
                if (!inProgress) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEnterSelecting()
                }
            }
        }
        SegmentedListItem(
            onClick = clickAction,
            shapes = shapes,
            modifier = rowModifier,
            colors = colors,
            onLongClick = longPress,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    }
}

private val DATE_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private fun formatRelative(ms: Long): String = DATE_FMT.format(Date(ms))
