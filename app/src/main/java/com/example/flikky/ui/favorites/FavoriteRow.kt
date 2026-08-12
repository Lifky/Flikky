package com.example.flikky.ui.favorites

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.example.flikky.R
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.di.ServiceLocator
import com.example.flikky.ui.components.FileLeadingSpec
import com.example.flikky.ui.components.FileLeadingVisual
import com.example.flikky.ui.components.StoredVideo
import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.FilesListBuilder
import com.example.flikky.ui.files.iconResource
import com.example.flikky.ui.theme.Spacing
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 与文件总览行共用同一个垂直对齐 token，两屏不会漂移。 */
internal fun favoriteRowVerticalAlignment(): Alignment.Vertical = FileLeadingSpec.rowAlignment

/**
 * A favorite row rendered with the official M3 Expressive [SegmentedListItem].
 *
 * The favorites list is flat (no section headers), so [positionInRun]/[runSize] are simply the
 * item index and total count — they drive the per-position segmented corners. In multi-select the
 * `selected` overload supplies the built-in selection spring (shape + container morph); outside
 * multi-select the clickable overload keeps tap-to-open / long-press-to-select.
 *
 * 交互与文件总览一致：点行=预览/打开（[onClick] 由调用方按 [FavoriteActionPolicy] 分派），
 * 点 leading=进多选，长按=进多选。尾部是 SplitButton——左半发送（服务未连浏览器时置灰），
 * 右半展开行内菜单，多选态整组隐藏。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteRow(
    favorite: FavoriteEntity,
    selecting: Boolean,
    selected: Boolean,
    sendEnabled: Boolean,
    positionInRun: Int,
    runSize: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLeadingClick: () -> Unit,
    onSend: () -> Unit,
    onMenuAction: (FavoriteRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedNow = selected
    val isText = favorite.kind == FavoriteActionPolicy.KIND_TEXT
    val selectedDescription = stringResource(R.string.home_selected)
    val notSelectedDescription = stringResource(R.string.home_not_selected)
    val selectLabel = stringResource(R.string.files_select_item)
    val shapes = ListItemDefaults.segmentedShapes(index = positionInRun, count = runSize)
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
            if (selecting) Modifier.semantics {
                this.selected = selectedNow
                stateDescription = if (selectedNow) selectedDescription else notSelectedDescription
            } else Modifier
        )

    // leading 与文件总览逐像素一致：共用 FileLeadingVisual（媒体缩略图 / 同占位异形图标容器）。
    // 文本收藏没有文件，用 format_quote 图标占同一位置——否则文本行的标题会比文件行左移一截。
    val leadingVisual: @Composable () -> Unit = {
        val depotId = favorite.fileId
        val category = FilesListBuilder.categoryOf(favorite.fileMime)
        FileLeadingVisual(
            iconRes = if (isText) R.drawable.ic_format_quote else category.iconResource(),
            thumbnailModel = if (!isText && depotId != null &&
                FilesListBuilder.isMedia(favorite.fileMime)
            ) {
                remember(depotId, category) {
                    val file = ServiceLocator.favoriteFileStore.resolve(depotId)
                    if (category == FileCategory.VIDEO) StoredVideo(file) else file
                }
            } else {
                null
            },
            selected = selectedNow,
        )
    }
    val leading: @Composable () -> Unit = if (selecting) {
        leadingVisual
    } else {
        {
            Box(modifier = Modifier.clickable(onClickLabel = selectLabel) { onLeadingClick() }) {
                leadingVisual()
            }
        }
    }

    val headline: @Composable () -> Unit = {
        Text(
            text = favorite.primaryText(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val supporting: @Composable () -> Unit = {
        Column {
            Text(
                text = favorite.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isText) {
                Text(
                    text = formatBytes(favorite.fileSize ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
    // 尾部 SplitButton：左半即时发送（未连接浏览器时按 MD3 disabled 态置灰，与原按钮逻辑一致），
    // 右半展开行内菜单——单条操作不必再长按进多选，菜单项带文案也比图标工具栏自解释。
    val trailing: (@Composable () -> Unit)? = if (selecting) {
        null
    } else {
        {
            var menuExpanded by remember { mutableStateOf(false) }
            val chevronRotation by animateFloatAsState(
                targetValue = if (menuExpanded) 180f else 0f,
                label = "favoriteRowChevron",
            )
            Box {
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.LeadingButton(
                            onClick = onSend,
                            enabled = sendEnabled,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_upward),
                                contentDescription = stringResource(R.string.favorites_send),
                                modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                            )
                        }
                    },
                    trailingButton = {
                        SplitButtonDefaults.TrailingButton(
                            checked = menuExpanded,
                            onCheckedChange = { menuExpanded = it },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_expand_more),
                                contentDescription = stringResource(R.string.files_more_actions),
                                modifier = Modifier
                                    .size(SplitButtonDefaults.TrailingIconSize)
                                    .graphicsLayer { rotationZ = chevronRotation },
                            )
                        }
                    },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    FavoriteActionPolicy.rowMenu(
                        kind = favorite.kind,
                        mime = favorite.fileMime,
                        hasFile = favorite.fileId != null,
                    ).forEach { entry ->
                        DropdownMenuItem(
                            text = { Text(stringResource(entry.action.labelResource())) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(entry.action.iconResource()),
                                    contentDescription = null,
                                )
                            },
                            colors = if (entry.action == FavoriteRowAction.DELETE) {
                                MenuDefaults.itemColors(
                                    textColor = MaterialTheme.colorScheme.error,
                                    leadingIconColor = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                MenuDefaults.itemColors()
                            },
                            enabled = entry.enabled,
                            onClick = {
                                menuExpanded = false
                                onMenuAction(entry.action)
                            },
                        )
                    }
                }
            }
        }
    }

    if (selecting) {
        SegmentedListItem(
            selected = selectedNow,
            onClick = onClick,
            shapes = shapes,
            modifier = rowModifier,
            colors = colors,
            onLongClick = onLongClick,
            verticalAlignment = favoriteRowVerticalAlignment(),
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    } else {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = rowModifier,
            colors = colors,
            onLongClick = onLongClick,
            verticalAlignment = favoriteRowVerticalAlignment(),
            leadingContent = leading,
            supportingContent = supporting,
            trailingContent = trailing,
            content = headline,
        )
    }
}

internal fun FavoriteRowAction.labelResource(): Int = when (this) {
    FavoriteRowAction.COPY -> R.string.favorites_action_copy
    FavoriteRowAction.SHARE -> R.string.favorites_share
    FavoriteRowAction.MOVE -> R.string.favorites_move_to_group
    FavoriteRowAction.OPEN_WITH -> R.string.favorites_action_open_with
    FavoriteRowAction.GALLERY -> R.string.files_action_gallery
    FavoriteRowAction.SAVE_AS -> R.string.files_action_save_as
    FavoriteRowAction.DELETE -> R.string.favorites_delete
}

internal fun FavoriteRowAction.iconResource(): Int = when (this) {
    FavoriteRowAction.COPY -> R.drawable.ic_content_copy
    FavoriteRowAction.SHARE -> R.drawable.ic_share
    FavoriteRowAction.MOVE -> R.drawable.ic_drive_file_move
    FavoriteRowAction.OPEN_WITH -> R.drawable.ic_open_in_new
    FavoriteRowAction.GALLERY -> R.drawable.ic_file_download
    FavoriteRowAction.SAVE_AS -> R.drawable.ic_save
    FavoriteRowAction.DELETE -> R.drawable.ic_delete
}

@Composable
private fun FavoriteEntity.primaryText(): String =
    if (kind == "FILE") {
        fileName ?: stringResource(R.string.favorites_unnamed_file)
    } else {
        textContent?.ifBlank { null } ?: stringResource(R.string.favorites_empty_text)
    }

private fun FavoriteEntity.subtitle(): String =
    formatTime(createdAt)

private val dateFormatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private fun formatTime(ms: Long): String = dateFormatter.format(Date(ms))

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return "${DecimalFormat("#.#").format(value)} ${units[index]}"
}
