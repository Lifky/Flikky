package com.example.flikky.ui.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedNow = selected
    val selectedDescription = stringResource(R.string.home_selected)
    val notSelectedDescription = stringResource(R.string.home_not_selected)
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

    // leading 与文件总览逐像素一致：共用 FileLeadingVisual（媒体缩略图 / 同占位圆形图标容器）。
    val leading: (@Composable () -> Unit)? = if (favorite.kind == "FILE") {
        {
            val depotId = favorite.fileId
            val category = FilesListBuilder.categoryOf(favorite.fileMime)
            FileLeadingVisual(
                category = category,
                thumbnailModel = if (depotId != null && FilesListBuilder.isMedia(favorite.fileMime)) {
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
    } else null

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
            if (favorite.kind == "FILE") {
                Text(
                    text = formatBytes(favorite.fileSize ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
    // 即时发送按钮：复用服务页发送按钮（FilledIconButton + ic_arrow_upward + 默认配色）。
    // 用 MD3 disabled 态自动变暗——未连接浏览器（sendEnabled=false）或多选态时按钮明确置灰、不可点。
    val trailing: @Composable () -> Unit = {
        FilledIconButton(
            onClick = onSend,
            enabled = !selecting && sendEnabled,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_upward),
                contentDescription = stringResource(R.string.favorites_send),
            )
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
