package com.example.flikky.ui.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.ui.theme.Spacing
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteRow(
    favorite: FavoriteEntity,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when {
        selecting && selected -> MaterialTheme.colorScheme.primaryContainer
        selecting -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selecting && selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenEdge)
            // 先 clip 圆角再挂 clickable，按压 ripple 不漫出卡片圆角（与 SessionRow 一致）。
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (favorite.kind == "FILE") R.drawable.ic_description else R.drawable.ic_content_copy
                ),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (selecting && selected) contentColor else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = favorite.primaryText(),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = favorite.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selecting && selected) contentColor.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                if (favorite.kind == "FILE") {
                    Text(
                        text = formatBytes(favorite.fileSize ?: 0L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun FavoriteEntity.primaryText(): String =
    if (kind == "FILE") fileName ?: "未命名文件" else textContent?.ifBlank { null } ?: "空文本"

private fun FavoriteEntity.subtitle(): String =
    listOfNotNull(sourceSessionName, formatTime(createdAt)).joinToString(" · ").ifBlank { formatTime(createdAt) }

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
