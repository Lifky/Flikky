package com.example.flikky.ui.favorites

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.di.ServiceLocator
import com.example.flikky.ui.components.ConfirmDialog
import com.example.flikky.ui.components.FlikkySelectingToolbarOverlay
import com.example.flikky.ui.components.MAX_CONTENT_WIDTH_DP
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.home.GroupChips
import com.example.flikky.ui.home.GroupManageDialog
import com.example.flikky.ui.home.MoveToGroupSheet
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onSelectingChange: (Boolean) -> Unit = {},
    onSearchExpandedChange: (Boolean) -> Unit = {},
    viewModel: FavoritesViewModel = viewModel(),
) {
    val items by viewModel.items.collectAsState(initial = emptyList())
    val groups by viewModel.groups.collectAsState(initial = emptyList())
    val activeGroupId by viewModel.activeGroupId.collectAsState(initial = null)
    val hasFavorites by viewModel.hasFavorites.collectAsState(initial = false)
    val selection by viewModel.selection.collectAsState()
    val selecting by viewModel.selecting.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val sessionSnap by ServiceLocator.session.snapshot.collectAsState()
    val selectedIds = selection ?: emptySet()
    val canSendFavorites = sessionSnap.clientConnected
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    var showCreateGroup by remember { mutableStateOf(false) }
    var managingGroup by remember { mutableStateOf<FavoriteGroupEntity?>(null) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(selecting) { onSelectingChange(selecting) }
    LaunchedEffect(query) { onSearchExpandedChange(query.isNotBlank()) }
    BackHandler(enabled = selecting) { viewModel.exitSelecting() }

    Scaffold(
        // 同 HomeScreen：底部 inset 由 MainActivity 外层统一管，内层 Scaffold 不再消费底部
        // window inset，避免删 bottomBar 后与外层叠成双重 inset（NavBar 上方多一截空白条）。
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("已选 ${selectedIds.size} 个") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelecting() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { viewModel.selectAll(items.map { it.id }) },
                            enabled = items.isNotEmpty(),
                        ) { Text("全选") }
                    },
                )
            } else {
                // 与传输页视觉完全一致：同一个 M3 SearchBar 组件、同样放在 topBar 槽位、
                // 同样的 16dp 侧边距与限宽居中。仅逻辑不同（这里就地过滤收藏，不展开全屏）。
                SearchBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = MAX_CONTENT_WIDTH_DP.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenEdge),
                    expanded = false,
                    onExpandedChange = {},
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = viewModel::setQuery,
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text("搜索收藏") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                            trailingIcon = {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = viewModel::clearQuery) {
                                        Icon(Icons.Default.Close, contentDescription = "清空搜索")
                                    }
                                }
                            },
                        )
                    },
                ) {}
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.fillMaxSize().maxContentWidth()) {
                    // 搜索框已上移到 Scaffold topBar（与传输页同槽位、同组件）。
                    // 合集 chip 行：整库为空时不显示（与传输页一致——无内容不显示）；
                    // 选中某个合集或库里已有收藏时才显示。
                    if (!selecting && (activeGroupId != null || hasFavorites)) {
                        GroupChips(
                            groups = groups.toGroupEntities(),
                            activeGroupId = activeGroupId,
                            onSelect = { viewModel.setActiveGroup(it) },
                            onAdd = { showCreateGroup = true },
                            onManage = { group -> managingGroup = group.toFavoriteGroup(groups) },
                        )
                    }
                    if (items.isEmpty()) {
                        EmptyFavorites(
                            text = when {
                                query.isNotBlank() -> "没有匹配的收藏"
                                activeGroupId != null -> "该合集还没有收藏"
                                else -> "在消息或文件上点 ☆ 即可收藏到这里"
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            items(items, key = { it.id }) { favorite ->
                                FavoriteRow(
                                    favorite = favorite,
                                    selecting = selecting,
                                    selected = favorite.id in selectedIds,
                                    sendEnabled = canSendFavorites,
                                    onClick = {
                                        if (selecting) {
                                            viewModel.toggleSelection(favorite.id)
                                        } else if (favorite.kind == "TEXT") {
                                            clipboard.setText(AnnotatedString(favorite.textContent.orEmpty()))
                                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.openFavoriteFile(favorite)
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelection(favorite.id) },
                                    onSend = {
                                        scope.launch {
                                            val sent = viewModel.sendFavorite(favorite)
                                            snackbarHostState.showSnackbar(
                                                if (sent) "已发送收藏" else "请先连接浏览器"
                                            )
                                        }
                                    },
                                )
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
                FavoritesSelectingToolbar(
                    selectedCount = selectedIds.size,
                    onMove = { showMoveSheet = true },
                    onDelete = { if (selectedIds.isNotEmpty()) showDeleteConfirm = true },
                )
            }
        }
    }

    if (showCreateGroup) {
        GroupNameDialog(
            title = "新建合集",
            onConfirm = {
                showCreateGroup = false
                viewModel.createGroup(it)
            },
            onDismiss = { showCreateGroup = false },
        )
    }

    managingGroup?.let { group ->
        val groupEntities = groups.toGroupEntities()
        val ordered = groupEntities.sortedWith(compareBy { it.sortOrder })
        val idx = ordered.indexOfFirst { it.id == group.id }
        GroupManageDialog(
            group = group.toGroupEntity(),
            canMoveUp = idx > 0,
            canMoveDown = idx >= 0 && idx < ordered.lastIndex,
            onSave = { viewModel.renameGroup(group.id, it) },
            onMoveUp = {
                if (idx > 0) {
                    val ids = ordered.map { it.id }.toMutableList()
                    val current = ids[idx]
                    ids[idx] = ids[idx - 1]
                    ids[idx - 1] = current
                    viewModel.reorderGroups(ids)
                }
            },
            onMoveDown = {
                if (idx in 0 until ordered.lastIndex) {
                    val ids = ordered.map { it.id }.toMutableList()
                    val current = ids[idx]
                    ids[idx] = ids[idx + 1]
                    ids[idx + 1] = current
                    viewModel.reorderGroups(ids)
                }
            },
            onDelete = {
                scope.launch {
                    val token = viewModel.deleteGroupWithUndo(group.id)
                    if (token != null) {
                        val result = snackbarHostState.showSnackbar(
                            message = "已删除合集「${group.name}」",
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

    if (showMoveSheet) {
        MoveToGroupSheet(
            groups = groups.toGroupEntities(),
            onSelect = { targetGroup ->
                showMoveSheet = false
                scope.launch {
                    val count = viewModel.moveSelectedToGroup(targetGroup)
                    if (count > 0) snackbarHostState.showSnackbar("已移动 $count 个收藏")
                }
            },
            onDismiss = { showMoveSheet = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除 ${selectedIds.size} 个收藏",
            text = "将删除收藏快照及对应副本文件。该操作不可撤销。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                showDeleteConfirm = false
                scope.launch { viewModel.deleteSelected() }
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun EmptyFavorites(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(Spacing.screenEdge), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("收藏", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
