package com.example.flikky.ui.files

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.di.ServiceLocator
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.ConfirmDialog
import com.example.flikky.ui.components.EmptyStateContent
import com.example.flikky.ui.components.FlikkyFloatingToolbar
import com.example.flikky.ui.components.FlikkyFloatingToolbarLift
import com.example.flikky.ui.components.FlikkySelectingToolbarOverlay
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.formatSize
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.components.openStoredFile
import com.example.flikky.ui.components.sessionFile
import com.example.flikky.ui.components.shareStoredFile
import com.example.flikky.ui.favorites.FavoriteGroupPickerSheet
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilesScreen(
    onBack: () -> Unit,
    onOpenMessage: (sessionId: Long, messageId: Long) -> Unit,
    viewModel: FilesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val rows by viewModel.rows.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val category by viewModel.category.collectAsState()
    val query by viewModel.query.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val selecting by viewModel.selecting.collectAsState()
    val favoriteGroups by ServiceLocator.favoritesRepository.observeGroups()
        .collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var showFavoriteSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var saveTarget by remember { mutableStateOf<FileOverviewRow?>(null) }
    val focusRequester = remember { FocusRequester() }
    val selectedIds = selection.orEmpty()
    val selectedRows = remember(rows, selectedIds) {
        rows.filter { it.messageId in selectedIds }
    }
    val singleSelected = selectedRows.singleOrNull()
    val deletableRows = selectedRows.filter { it.sessionEndedAt != null }
    // 多选浮动工具栏可见时，snackbar 与列表底部都要为它让位（共用同一抬升量）。
    val toolbarLift by animateDpAsState(
        targetValue = if (selecting) FlikkyFloatingToolbarLift else 0.dp,
        animationSpec = Motion.effects(),
        label = "filesToolbarLift",
    )
    fun closeSearch() {
        searchActive = false
        viewModel.setQuery("")
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val target = saveTarget
        saveTarget = null
        if (uri != null && target != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                            sessionFile(target.sessionId, target.fileId)
                                .inputStream()
                                .use { input -> input.copyTo(output) }
                        }
                    }.isSuccess
                }
                snackbarHostState.showSnackbar(
                    context.getString(
                        if (saved) R.string.files_save_done else R.string.files_save_failed,
                    ),
                )
            }
        }
    }

    BackHandler(enabled = searchActive && !selecting) { closeSearch() }
    BackHandler(enabled = selecting) { viewModel.exitSelecting() }
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = toolbarLift),
            )
        },
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.files_selected_count, selectedIds.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::exitSelecting) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.home_close),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { viewModel.selectAll(rows.map { it.messageId }) },
                            enabled = rows.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.home_select_all))
                        }
                    },
                )
            } else if (searchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.files_search_hint)) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.home_search_clear),
                                        )
                                    }
                                } else {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                }
                            },
                            singleLine = true,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::closeSearch) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.history_back),
                            )
                        }
                    },
                    actions = {
                        SortMenuAction(
                            sort = sort,
                            expanded = sortExpanded,
                            onExpandedChange = { sortExpanded = it },
                            onSelect = viewModel::setSort,
                        )
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.files_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.history_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.files_search_hint),
                            )
                        }
                        SortMenuAction(
                            sort = sort,
                            expanded = sortExpanded,
                            onExpandedChange = { sortExpanded = it },
                            onSelect = viewModel::setSort,
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .maxContentWidth()
                    .fillMaxSize(),
            ) {
                Text(
                    text = stringResource(
                        R.string.files_stats,
                        stats.count,
                        formatSize(stats.totalBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = Spacing.screenEdge,
                        vertical = Spacing.sm,
                    ),
                )
                if (!selecting) {
                    FileCategoryChips(
                        selected = category,
                        onSelected = viewModel::setCategory,
                    )
                }
                if (rows.isEmpty()) {
                    if (query.isEmpty() && category == FileCategory.ALL) {
                        EmptyStateContent(
                            title = stringResource(R.string.files_empty_title),
                            description = stringResource(R.string.files_empty_desc),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.files_empty_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            top = Spacing.sm,
                            bottom = Spacing.xxl + toolbarLift,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.messageId }) { index, row ->
                            FileOverviewItem(
                                row = row,
                                index = index,
                                count = rows.size,
                                selecting = selecting,
                                selected = row.messageId in selectedIds,
                                modifier = flikkyItemAnimation(),
                                onNormalClick = {
                                    openStoredFile(
                                        context = context,
                                        sessionId = row.sessionId,
                                        fileId = row.fileId,
                                        displayName = row.fileName ?: row.fileId,
                                        mime = row.fileMime,
                                        onMissing = { viewModel.markMissing(row.messageId) },
                                    )
                                },
                                onEnterSelecting = {
                                    viewModel.toggleSelection(row.messageId)
                                },
                                onToggleSelection = {
                                    viewModel.toggleSelection(row.messageId)
                                },
                            )
                        }
                    }
                }
            }

            FlikkySelectingToolbarOverlay(
                visible = selecting,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FlikkyFloatingToolbar {
                    IconButton(
                        onClick = { showFavoriteSheet = true },
                        enabled = selectedRows.isNotEmpty(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_star_border),
                            contentDescription = stringResource(R.string.files_action_favorite),
                        )
                    }
                    singleSelected?.let { single ->
                        IconButton(
                            onClick = {
                                saveTarget = single
                                saveLauncher.launch(single.fileName ?: single.fileId)
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_file_download),
                                contentDescription = stringResource(R.string.files_action_save),
                            )
                        }
                        IconButton(
                            onClick = {
                                shareStoredFile(
                                    context,
                                    single.sessionId,
                                    single.fileId,
                                    single.fileName ?: single.fileId,
                                    single.fileMime,
                                )
                            },
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_share),
                                contentDescription = stringResource(R.string.files_action_share),
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.exitSelecting()
                                onOpenMessage(single.sessionId, single.messageId)
                            },
                            enabled = single.sessionEndedAt != null,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_history),
                                contentDescription = stringResource(
                                    R.string.files_action_open_in_session,
                                ),
                            )
                        }
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = deletableRows.isNotEmpty(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.files_action_delete),
                            tint = if (deletableRows.isNotEmpty()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                LocalContentColor.current
                            },
                        )
                    }
                }
            }
        }
    }

    if (showFavoriteSheet) {
        FavoriteGroupPickerSheet(
            groups = favoriteGroups,
            onSelect = { groupId ->
                showFavoriteSheet = false
                scope.launch {
                    selectedRows.forEach { row ->
                        runCatching {
                            ServiceLocator.favoritesRepository.favoriteFile(
                                row.sessionId,
                                row.sessionName,
                                row.toFileMessage(),
                                groupId,
                            )
                        }
                    }
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.files_favorite_done),
                    )
                    viewModel.exitSelecting()
                }
            },
            onCreateGroup = { name ->
                showFavoriteSheet = false
                scope.launch {
                    val groupId = ServiceLocator.favoritesRepository.createGroup(name)
                    selectedRows.forEach { row ->
                        runCatching {
                            ServiceLocator.favoritesRepository.favoriteFile(
                                row.sessionId,
                                row.sessionName,
                                row.toFileMessage(),
                                groupId,
                            )
                        }
                    }
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.files_favorite_done),
                    )
                    viewModel.exitSelecting()
                }
            },
            onDismiss = { showFavoriteSheet = false },
        )
    }

    if (showDeleteDialog) {
        val selectedSize = deletableRows.sumOf { it.fileSize ?: 0L }
        val hasActive = selectedRows.any { it.sessionEndedAt == null }
        val message = if (selectedRows.size == 1) {
            context.getString(R.string.files_delete_text_single, formatSize(selectedSize))
        } else {
            context.getString(R.string.files_delete_text_batch, formatSize(selectedSize))
        } + if (hasActive) {
            "\n\n${context.getString(R.string.files_delete_in_progress_hint)}"
        } else {
            ""
        }
        ConfirmDialog(
            title = if (selectedRows.size == 1) {
                stringResource(R.string.files_delete_title)
            } else {
                stringResource(R.string.files_delete_title_batch, selectedRows.size)
            },
            text = message,
            confirmLabel = stringResource(R.string.files_action_delete),
            danger = true,
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    val (deleted, requested) = viewModel.deleteRows(selectedRows)
                    snackbarHostState.showSnackbar(
                        if (deleted == requested) {
                            context.getString(
                                R.string.files_delete_done,
                                formatSize(selectedSize),
                            )
                        } else {
                            context.getString(
                                R.string.files_delete_partial,
                                deleted,
                                requested,
                            )
                        },
                    )
                }
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

@Composable
private fun SortMenuAction(
    sort: FileSort,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (FileSort) -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                painterResource(R.drawable.ic_filter_list),
                contentDescription = stringResource(R.string.files_sort),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            FileSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (option == FileSort.TIME) {
                                    R.string.files_sort_time
                                } else {
                                    R.string.files_sort_size
                                },
                            ),
                        )
                    },
                    leadingIcon = if (sort == option) {
                        { Icon(Icons.Filled.Done, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(option)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun FileCategoryChips(
    selected: FileCategory,
    onSelected: (FileCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(FileCategory.entries, key = { it.name }) { category ->
            val isSelected = category == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(category) },
                label = { Text(stringResource(category.labelResource())) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            Icons.Filled.Done,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileOverviewItem(
    row: FileOverviewRow,
    index: Int,
    count: Int,
    selecting: Boolean,
    selected: Boolean,
    onNormalClick: () -> Unit,
    onEnterSelecting: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = FilesListBuilder.categoryOf(row.fileMime)
    val haptic = LocalHapticFeedback.current
    val selectedDescription = stringResource(R.string.home_selected)
    val notSelectedDescription = stringResource(R.string.home_not_selected)
    val direction = stringResource(
        if (row.origin == "BROWSER") {
            R.string.files_direction_received
        } else {
            R.string.files_direction_sent
        },
    )
    // 不含会话名：单行副标题放不下四段，会话名最长且可从会话上下文获知，优先保大小/日期可见。
    val subtitle = listOf(
        direction,
        formatSize(row.fileSize ?: 0L),
        formatFileDate(row.timestamp),
    ).joinToString(" · ")

    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        leadingContentColor = MaterialTheme.colorScheme.primary,
        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedSupportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val rowModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.screenEdge)
        .then(
            if (selecting) {
                Modifier.semantics {
                    this.selected = selected
                    stateDescription = if (selected) {
                        selectedDescription
                    } else {
                        notSelectedDescription
                    }
                }
            } else {
                Modifier
            },
        )
    val leading: @Composable () -> Unit = {
        Icon(
            painter = painterResource(category.iconResource()),
            contentDescription = null,
        )
    }
    val supporting: @Composable () -> Unit = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val headline: @Composable () -> Unit = {
        Text(
            text = row.fileName ?: row.fileId,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (selecting) {
        SegmentedListItem(
            selected = selected,
            onClick = onToggleSelection,
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            leadingContent = leading,
            supportingContent = supporting,
            content = headline,
        )
    } else {
        SegmentedListItem(
            onClick = onNormalClick,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onEnterSelecting()
            },
            shapes = shapes,
            colors = colors,
            modifier = rowModifier,
            leadingContent = leading,
            supportingContent = supporting,
            content = headline,
        )
    }
}

private fun FileCategory.labelResource(): Int = when (this) {
    FileCategory.ALL -> R.string.files_filter_all
    FileCategory.IMAGE -> R.string.files_filter_image
    FileCategory.VIDEO -> R.string.files_filter_video
    FileCategory.AUDIO -> R.string.files_filter_audio
    FileCategory.DOCUMENT -> R.string.files_filter_document
    FileCategory.OTHER -> R.string.files_filter_other
}

private fun FileCategory.iconResource(): Int = when (this) {
    FileCategory.IMAGE -> R.drawable.ic_image
    FileCategory.VIDEO -> R.drawable.ic_movie
    FileCategory.AUDIO -> R.drawable.ic_music_note
    FileCategory.DOCUMENT -> R.drawable.ic_description
    FileCategory.ALL,
    FileCategory.OTHER,
    -> R.drawable.ic_draft
}

private fun formatFileDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.SHORT).format(Date(timestamp))

private fun FileOverviewRow.toFileMessage(): Message.File = Message.File(
    id = messageId,
    origin = Origin.valueOf(origin),
    timestamp = timestamp,
    fileId = fileId,
    name = fileName ?: fileId,
    sizeBytes = fileSize ?: 0L,
    mime = fileMime ?: "",
    status = Message.File.Status.COMPLETED,
)
