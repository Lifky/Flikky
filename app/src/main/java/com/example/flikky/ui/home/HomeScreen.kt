package com.example.flikky.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val selection by viewModel.selection.collectAsState()
    val selecting by viewModel.selecting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun startAndNavigate() {
        viewModel.startService()
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
                NormalTopBar(onEnterSelecting = { viewModel.enterSelecting() })
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startAndNavigate()
                            else notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startAndNavigate()
                        }
                    },
                    text = { Text("启动服务") },
                    icon = { Text("＋") },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                SelectingBottomBar(
                    selectedCount = selectedCount,
                    totalCount = sessions.size,
                    onStartExport = {
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
                        onNormalClick = { onOpenSession(s.id) },
                        onToggleSelection = { viewModel.toggleSelection(s.id) },
                        onRename = { name -> viewModel.rename(s.id, name) },
                        onTogglePin = { viewModel.setPinned(s.id, !s.pinned) },
                        onDelete = { viewModel.deleteSession(s.id) },
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NormalTopBar(onEnterSelecting: () -> Unit) {
    TopAppBar(
        title = { Text("Flikky") },
        actions = {
            TextButton(onClick = onEnterSelecting) { Text("导出") }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
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
            IconButton(onClick = onClose) { Text("✕") }
        },
        actions = {
            TextButton(onClick = onSelectAll, enabled = selectAllEnabled) { Text("全选") }
        },
        colors = TopAppBarDefaults.topAppBarColors(),
    )
}

@Composable
private fun SelectingBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onStartExport: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "已选 $selectedCount 个 / 共 $totalCount 个",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStartExport,
                enabled = selectedCount > 0,
            ) {
                Text("开始导出 ($selectedCount)")
            }
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
    onToggleSelection: () -> Unit,
    onRename: (String) -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val inProgress = s.endedAt == null
    val dimmed = selecting && inProgress

    // Click behavior:
    //   Normal mode      -> open session (long-press = context menu, unless in-progress).
    //   Selecting mode   -> toggle selection (no-op when in-progress; long-press ignored).
    val cardModifier = if (selecting) {
        if (inProgress) {
            Modifier // no click — in-progress sessions cannot be selected.
        } else {
            Modifier.clickable(onClick = onToggleSelection)
        }
    } else {
        Modifier.combinedClickable(
            onClick = onNormalClick,
            onLongClick = { if (!inProgress) menuExpanded = true },
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(cardModifier)
            .then(if (dimmed) Modifier.alpha(0.5f) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (s.pinned) MaterialTheme.colorScheme.secondaryContainer
                             else MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Checkbox(
                    checked = checked && !inProgress,
                    onCheckedChange = { if (!inProgress) onToggleSelection() },
                    enabled = !inProgress,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = (if (s.pinned) "📌 " else "") + s.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        selecting && inProgress -> "结束服务后可导出"
                        else -> s.previewText ?: "${s.messageCount} 条消息 · ${s.fileCount} 文件"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = if (inProgress) "进行中" else formatRelative(s.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(if (s.pinned) "取消置顶" else "置顶") },
                onClick = { menuExpanded = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = { menuExpanded = false; showRename = true },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = { menuExpanded = false; showDeleteConfirm = true },
            )
        }
    }

    if (showRename) {
        var draft by remember { mutableStateOf(s.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { showRename = false; onRename(draft) }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除会话") },
            text = { Text("将删除此会话的所有消息与文件。该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

private val DATE_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private fun formatRelative(ms: Long): String = DATE_FMT.format(Date(ms))
