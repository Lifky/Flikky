package com.example.flikky.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.db.entities.SessionEntity
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
    val selection by viewModel.selection.collectAsState()
    val selecting by viewModel.selecting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // SearchBar 展开态上提到这里：用于隐藏 FAB，并上报给 MainActivity 隐藏底栏 + 让主页铺满全屏。
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
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

    // 上报多选态给 MainActivity，用于多选时隐藏底部导航。
    LaunchedEffect(selecting) { onSelectingChange(selecting) }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectingTopBar(
                    selectedCount = selectedCount,
                    onClose = { viewModel.exitSelecting() },
                    onSelectAll = { viewModel.selectAll(validSessionIds) },
                    selectAllEnabled = validSessionIds.isNotEmpty(),
                )
            } else {
                HomeSearchBar(
                    sessions = sessions,
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    onOpenSession = onOpenSession,
                    onResume = { resumeNavigate() },
                    onOpenMessageHit = onOpenSearchHit,
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                )
            }
        },
        floatingActionButton = {
            if (!selecting && !searchExpanded) {
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
        bottomBar = {
            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                SelectingActionBar(
                    selectedCount = selectedCount,
                    allPinned = allSelectedPinned,
                    onPinToggle = { scope.launch { viewModel.pinSelected(!allSelectedPinned) } },
                    onRename = { showRenameDialog = true },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        if (sessions.isEmpty()) {
            EmptyHero(modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(sessions, key = { it.id }) { s ->
                    SessionRow(
                        s = s,
                        selecting = selecting,
                        checked = s.id in selectedIds,
                        onNormalClick = {
                            if (s.endedAt == null) resumeNavigate() else onOpenSession(s.id)
                        },
                        onEnterSelecting = { viewModel.toggleSelection(s.id) }, // 从 null 切换=进多选并选中该条
                        onToggleSelection = { viewModel.toggleSelection(s.id) },
                        onStopInProgress = { viewModel.stopService() },
                    )
                }
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
        var draft by remember(singleSelected.id) { mutableStateOf(singleSelected.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
            text = { OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    scope.launch { viewModel.renameSelected(draft) }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("取消") } },
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除 $selectedCount 个会话") },
            text = { Text("将删除所选会话的全部消息与文件。该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showBatchDeleteDialog = false
                    scope.launch { viewModel.deleteSelected() }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } },
        )
    }
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

@Composable
private fun SelectingActionBar(
    selectedCount: Int,
    allPinned: Boolean,
    onPinToggle: () -> Unit,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val enabled = selectedCount > 0
            ActionBarItem(
                iconRes = R.drawable.ic_push_pin,
                label = if (allPinned) "取消置顶" else "置顶",
                enabled = enabled, onClick = onPinToggle,
            )
            if (selectedCount == 1) {
                ActionBarItem(iconRes = R.drawable.ic_edit, label = "重命名", enabled = true, onClick = onRename)
            }
            ActionBarItem(iconRes = R.drawable.ic_upload, label = "导出", enabled = enabled, onClick = onExport)
            ActionBarItem(
                iconRes = R.drawable.ic_delete, label = "删除",
                enabled = enabled, onClick = onDelete, danger = true,
            )
        }
    }
}

@Composable
private fun ActionBarItem(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        danger   -> MaterialTheme.colorScheme.error
        else     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(painterResource(iconRes), contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
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
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "尚无历史会话。点右下「启动服务」开始第一次传输。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    s: SessionEntity,
    selecting: Boolean,
    checked: Boolean,
    onNormalClick: () -> Unit,
    onEnterSelecting: () -> Unit,
    onToggleSelection: () -> Unit,
    onStopInProgress: () -> Unit,
) {
    val inProgress = s.endedAt == null
    val dimmed = selecting && inProgress
    val haptic = LocalHapticFeedback.current

    // Scheme A 纯色三态：选中 primaryContainer / 多选未选 surfaceContainer / 普通 surfaceContainerLow（置顶 secondaryContainer）。
    val targetColor = when {
        selecting && checked && !inProgress -> MaterialTheme.colorScheme.primaryContainer
        selecting && !inProgress            -> MaterialTheme.colorScheme.surfaceContainer
        s.pinned                            -> MaterialTheme.colorScheme.secondaryContainer
        else                                -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val containerColor by animateColorAsState(targetColor, label = "rowSelColor")
    val selectedNow = selecting && checked && !inProgress
    val onContent = if (selectedNow) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface

    val cardModifier = if (selecting) {
        if (inProgress) Modifier // 进行中不可选
        else Modifier.combinedClickable(onClick = onToggleSelection)
    } else {
        Modifier.combinedClickable(
            onClick = onNormalClick,
            onLongClick = {
                if (!inProgress) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEnterSelecting()
                }
            },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(cardModifier)
            .then(if (dimmed) Modifier.alpha(0.5f) else Modifier)
            .then(
                if (selecting && !inProgress) Modifier.semantics {
                    selected = checked
                    stateDescription = if (checked) "已选中" else "未选中"
                } else Modifier
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.pinned) {
                        Icon(
                            painter = painterResource(R.drawable.ic_push_pin),
                            contentDescription = "已置顶",
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedNow) onContent else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(text = s.name, style = MaterialTheme.typography.titleMedium, color = onContent)
                }
                Text(
                    text = when {
                        selecting && inProgress -> "结束服务后可选择"
                        else -> s.previewText ?: "${s.messageCount} 条消息 · ${s.fileCount} 文件"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selectedNow) onContent.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (inProgress && !selecting) {
                TextButton(onClick = onStopInProgress) {
                    Text("停止", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private val DATE_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private fun formatRelative(ms: Long): String = DATE_FMT.format(Date(ms))
