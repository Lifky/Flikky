package com.example.flikky.ui.serving

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.ui.components.FileLeadingVisual
import com.example.flikky.ui.components.formatSize
import com.example.flikky.ui.components.sessionFile
import com.example.flikky.ui.components.StoredVideo
import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.iconResource
import com.example.flikky.ui.files.FileSort
import com.example.flikky.ui.files.FilesListBuilder
import com.example.flikky.ui.files.labelResource
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesQuickSheet(
    rows: List<FileOverviewRow>,
    onSend: (FileOverviewRow) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FileCategory.ALL) }
    var pressedId by remember { mutableLongStateOf(0L) }
    val visibleRows = remember(rows, query, category) {
        FilesListBuilder.build(rows, category, query, FileSort.TIME)
    }

    LaunchedEffect(pressedId) {
        if (pressedId != 0L) {
            delay(420)
            pressedId = 0L
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.files_quick_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    horizontal = Spacing.screenEdge,
                    vertical = Spacing.sm,
                ),
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenEdge),
                placeholder = { Text(stringResource(R.string.files_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.favorite_quick_clear_search
                                ),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = SearchBarDefaults.inputFieldShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(FileCategory.entries, key = { it.name }) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = {
                            Text(
                                text = stringResource(item.labelResource()),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        leadingIcon = if (category == item) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Done,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
            if (visibleRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .padding(Spacing.screenEdge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (query.isBlank() && category == FileCategory.ALL) {
                                R.string.files_empty_title
                            } else {
                                R.string.files_quick_no_match
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                ) {
                    items(visibleRows, key = { it.messageId }) { row ->
                        FileQuickRow(
                            row = row,
                            sending = pressedId == row.messageId,
                            onSend = {
                                pressedId = row.messageId
                                onSend(row)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileQuickRow(
    row: FileOverviewRow,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val category = FilesListBuilder.categoryOf(row.fileMime)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (sending) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable(onClick = onSend)
            .heightIn(min = Sizes.rowMinH)
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // leading 与文件总览行逐像素一致，共用 FileLeadingVisual。
        FileLeadingVisual(
            iconRes = category.iconResource(),
            thumbnailModel = if (category == FileCategory.IMAGE || category == FileCategory.VIDEO) {
                remember(row.sessionId, row.fileId, category) {
                    val file = sessionFile(row.sessionId, row.fileId)
                    if (category == FileCategory.VIDEO) StoredVideo(file) else file
                }
            } else {
                null
            },
        )
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.fileName ?: row.fileId,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatSize(row.fileSize ?: 0L)} · ${row.sessionName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSend) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_upward),
                contentDescription = stringResource(R.string.favorites_send),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
