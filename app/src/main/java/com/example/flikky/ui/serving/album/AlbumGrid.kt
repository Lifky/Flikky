package com.example.flikky.ui.serving.album

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.example.flikky.R
import com.example.flikky.data.MediaStoreLibrary
import com.example.flikky.server.dto.AlbumItemDto
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.AlbumDateLabel
import com.example.flikky.util.AlbumItemId
import com.example.flikky.util.AlbumTimeline
import java.time.ZoneId

/** Fixed column count avoids duplicating layout measurement in the grid. */
private const val ALBUM_COLUMNS = 3

/** Album timeline with full-width date headers and local MediaStore thumbnails. */
@Composable
fun AlbumGrid(
    items: List<AlbumItemDto>,
    selected: Set<String>,
    selecting: Boolean,
    onToggleSelection: (String) -> Unit,
    onPreview: (AlbumItemDto) -> Unit,
    onStartSelecting: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val sections = remember(items) {
        AlbumTimeline.group(items, { it.takenAtMs }, System.currentTimeMillis(), zone)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(ALBUM_COLUMNS),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Spacing.screenEdge,
            end = Spacing.screenEdge,
            bottom = Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        sections.forEach { section ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                AlbumDateHeader(section.label)
            }
            items(section.items, key = { it.id }) { item ->
                AlbumCell(
                    item = item,
                    checked = item.id in selected,
                    selecting = selecting,
                    onTap = {
                        if (selecting) onToggleSelection(item.id) else onPreview(item)
                    },
                    onLongClick = { onStartSelecting(item.id) },
                )
            }
        }
    }
}

@Composable
private fun AlbumDateHeader(label: AlbumDateLabel) {
    val text = when (label) {
        AlbumDateLabel.Today -> stringResource(R.string.album_date_today)
        AlbumDateLabel.Yesterday -> stringResource(R.string.album_date_yesterday)
        is AlbumDateLabel.SameYear ->
            stringResource(R.string.album_date_same_year, label.month, label.day)
        is AlbumDateLabel.Older ->
            stringResource(R.string.album_date_older, label.year, label.month, label.day)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun AlbumCell(
    item: AlbumItemDto,
    checked: Boolean,
    selecting: Boolean,
    onTap: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Spacing.xs))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = onTap, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = contentUriOf(item.id),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (checked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
            )
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = stringResource(R.string.album_select),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
            )
        } else if (selecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)),
            )
        }
        if (item.durationMs > 0L) {
            Text(
                text = formatDuration(item.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.xs)
                    .background(Color.Black.copy(alpha = 0.64f), RoundedCornerShape(Spacing.xs))
                    .padding(horizontal = Spacing.xs),
            )
        }
    }
}

private fun contentUriOf(rawId: String) =
    AlbumItemId.parse(rawId)?.let(MediaStoreLibrary::contentUri)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
