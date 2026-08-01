package com.example.flikky.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.ui.components.EmptyStateContent
import com.example.flikky.ui.components.flikkyItemAnimation
import com.example.flikky.ui.components.formatSize
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.components.openStoredFile
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

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
    val snackbarHostState = remember { SnackbarHostState() }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val searchSidePadding by animateDpAsState(
        targetValue = if (searchActive) 0.dp else Spacing.screenEdge,
        animationSpec = Motion.effects(),
        label = "filesSearchSidePadding",
    )

    fun closeSearch() {
        searchActive = false
        viewModel.setQuery("")
    }

    BackHandler(enabled = searchActive) { closeSearch() }
    LaunchedEffect(searchActive) {
        if (searchActive) focusRequester.requestFocus()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (searchActive) {
                SearchBar(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = searchSidePadding.coerceAtLeast(0.dp)),
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = query,
                            onQueryChange = viewModel::setQuery,
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text(stringResource(R.string.files_search_hint)) },
                            leadingIcon = {
                                IconButton(onClick = ::closeSearch) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.history_back),
                                    )
                                }
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setQuery("") }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.home_search_clear),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                ) {}
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
                        Box {
                            IconButton(onClick = { sortExpanded = true }) {
                                Icon(
                                    painterResource(R.drawable.ic_swap_vert),
                                    contentDescription = stringResource(R.string.files_sort),
                                )
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false },
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
                                            viewModel.setSort(option)
                                            sortExpanded = false
                                        },
                                    )
                                }
                            }
                        }
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
                FileCategoryChips(
                    selected = category,
                    onSelected = viewModel::setCategory,
                )
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
                            bottom = Spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.messageId }) { index, row ->
                            FileOverviewItem(
                                row = row,
                                index = index,
                                count = rows.size,
                                modifier = flikkyItemAnimation(),
                                onClick = {
                                    openStoredFile(
                                        context = context,
                                        sessionId = row.sessionId,
                                        fileId = row.fileId,
                                        displayName = row.fileName ?: row.fileId,
                                        mime = row.fileMime,
                                        onMissing = { viewModel.markMissing(row.messageId) },
                                    )
                                },
                            )
                        }
                    }
                }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = FilesListBuilder.categoryOf(row.fileMime)
    val direction = stringResource(
        if (row.origin == "BROWSER") {
            R.string.files_direction_received
        } else {
            R.string.files_direction_sent
        },
    )
    val subtitle = listOf(
        direction,
        row.sessionName,
        formatSize(row.fileSize ?: 0L),
        formatFileDate(row.timestamp),
    ).joinToString(" · ")

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge),
        leadingContent = {
            Icon(
                painter = painterResource(category.iconResource()),
                contentDescription = null,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            Text(
                text = row.fileName ?: row.fileId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
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
