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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteQuickSheet(
    favorites: List<FavoriteEntity>,
    groups: List<FavoriteGroupEntity>,
    recentFavoriteIds: List<Long>,
    onSend: (FavoriteEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var query by remember { mutableStateOf("") }
    var activeGroupId by remember { mutableStateOf<Long?>(null) }
    var pressedId by remember { mutableLongStateOf(0L) }
    val visibleFavorites = remember(favorites, query, activeGroupId) {
        val grouped = activeGroupId?.let { id -> favorites.filter { it.groupId == id } } ?: favorites
        searchFavorites(grouped, query)
    }
    val orderedGroups = remember(groups) {
        groups.sortedWith(compareBy<FavoriteGroupEntity> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
    }
    val recentFavorites = remember(favorites, recentFavoriteIds) {
        val byId = favorites.associateBy { it.id }
        recentFavoriteIds.mapNotNull(byId::get).take(5)
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
                text = "收藏",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            )
            if (recentFavorites.isNotEmpty()) {
                Text(
                    text = "最近使用",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs),
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screenEdge)
                        .padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(recentFavorites, key = { it.id }) { favorite ->
                        RecentFavoriteChip(
                            favorite = favorite,
                            sending = pressedId == favorite.id,
                            onSend = {
                                pressedId = favorite.id
                                onSend(favorite)
                            },
                        )
                    }
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenEdge),
                placeholder = { Text("搜索收藏") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清空搜索")
                        }
                    }
                },
                singleLine = true,
                shape = SearchBarDefaults.inputFieldShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                item {
                    FavoriteGroupChip(
                        label = "全部",
                        selected = activeGroupId == null,
                        onClick = { activeGroupId = null },
                    )
                }
                items(orderedGroups, key = { it.id }) { group ->
                    FavoriteGroupChip(
                        label = group.name,
                        selected = activeGroupId == group.id,
                        onClick = { activeGroupId = group.id },
                    )
                }
            }
            if (visibleFavorites.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .padding(Spacing.screenEdge),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (query.isBlank()) "暂无收藏" else "没有匹配的收藏",
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
                    items(visibleFavorites, key = { it.id }) { favorite ->
                        FavoriteQuickRow(
                            favorite = favorite,
                            sending = pressedId == favorite.id,
                            onSend = {
                                pressedId = favorite.id
                                onSend(favorite)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentFavoriteChip(
    favorite: FavoriteEntity,
    sending: Boolean,
    onSend: () -> Unit,
) {
    AssistChip(
        onClick = onSend,
        label = {
            Text(
                text = favorite.compactLabel(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = if (favorite.kind == "FILE") {
            {
                Icon(
                    painter = painterResource(R.drawable.ic_description),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_upward),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        colors = if (sending) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun FavoriteGroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = if (selected) {
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

@Composable
private fun FavoriteQuickRow(
    favorite: FavoriteEntity,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (sending) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onSend)
            .heightIn(min = Sizes.rowMinH)
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (favorite.kind == "FILE") {
            Icon(
                painter = painterResource(R.drawable.ic_description),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.lg))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = favorite.primaryLabel(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val secondary = favorite.secondaryLabel()
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onSend) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_upward),
                contentDescription = "发送收藏",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun FavoriteEntity.primaryLabel(): String =
    if (kind == "FILE") fileName ?: "未命名文件" else textContent?.ifBlank { null } ?: "空文本"

private fun FavoriteEntity.compactLabel(): String {
    val raw = primaryLabel().replace('\n', ' ').trim()
    return if (raw.length <= 12) raw else raw.take(12).trimEnd() + "…"
}

private fun FavoriteEntity.secondaryLabel(): String =
    when (kind) {
        "FILE" -> listOfNotNull(fileMime, fileSize?.let(::formatBytes)).joinToString(" · ")
        else -> ""
    }

private fun searchFavorites(all: List<FavoriteEntity>, query: String): List<FavoriteEntity> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return all
    return all.filter { favorite ->
        favorite.textContent?.contains(trimmed, ignoreCase = true) == true ||
            favorite.fileName?.contains(trimmed, ignoreCase = true) == true
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return "${java.text.DecimalFormat("#.#").format(value)} ${units[index]}"
}
