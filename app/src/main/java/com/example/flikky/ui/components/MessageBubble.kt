package com.example.flikky.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.flikky.R
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.theme.Spacing
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: Message,
    onTap: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    // FLOATING 操作样式下单击弹工具栏而非打开文件，状态行提示要跟着换（「点击查看操作」），
    // 否则文案与实际行为脱节。
    tapOpensFile: Boolean = true,
    transferProgress: Float? = null,
    showAvatar: Boolean = true,
    avatarId: Int? = null,
    avatarKey: String? = null,
    cornerRadius: Dp = 18.dp,
    selected: Boolean = false,
    thumbnailFile: File? = null,
) {
    val mine = msg.origin == Origin.PHONE
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
    val shape = RoundedCornerShape(cornerRadius)
    val bg = if (mine) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface

    val interaction = remember { MutableInteractionSource() }

    val avatarSlot: @Composable () -> Unit = {
        if (showAvatar && avatarKey != null) {
            Avatar(avatarKey = avatarKey, size = 36.dp)
        } else if (showAvatar && avatarId != null) {
            Avatar(avatarId = avatarId, size = 36.dp)
        } else {
            Spacer(Modifier.width(36.dp))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                else Modifier
            ),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            avatarSlot()
            Spacer(Modifier.width(Spacing.sm))
        }
        // FLOATING 模式（onLongPress == null）：用纯 clickable 只检测 TAP，
        // 不消费 long-press，把长按留给屏幕级 SelectionContainer 起划词选择。
        // INLINE 模式（onLongPress != null）：combinedClickable 消费长按弹内联栏。
        val clickModifier = if (onLongPress != null) {
            Modifier.combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            )
        } else {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onTap,
            )
        }
        val fileMsg = msg as? Message.File
        val isMediaThumb = fileMsg != null &&
            fileMsg.status == Message.File.Status.COMPLETED &&
            thumbnailFile != null
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bg)
                .then(clickModifier)
                .then(
                    if (isMediaThumb) Modifier
                    else Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ),
        ) {
            when {
                msg is Message.Text -> Text(
                    text = msg.content, color = fg,
                    style = MaterialTheme.typography.bodyLarge.merge(
                        TextStyle(lineBreak = LineBreak.Paragraph)
                    ),
                )
                isMediaThumb -> MediaBubbleContent(
                    msg = fileMsg!!,
                    fg = fg,
                    thumbnail = thumbnailFile!!,
                )
                else -> FileBubbleContent(
                    msg = fileMsg!!, fg = fg, mine = mine,
                    tapOpensFile = tapOpensFile,
                    transferProgress = transferProgress,
                )
            }
        }
        if (mine) {
            Spacer(Modifier.width(Spacing.sm))
            avatarSlot()
        }
    }
}

@Composable
private fun MediaBubbleContent(
    msg: Message.File,
    fg: Color,
    thumbnail: File,
) {
    val isVideo = msg.mime.startsWith("video/")
    Column {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = if (isVideo) StoredVideo(thumbnail) else thumbnail,
                contentDescription = msg.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .heightIn(max = 280.dp),
            )
            if (isVideo) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_circle),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Text(
            text = "${msg.name}  ·  ${formatSize(msg.sizeBytes)}",
            color = fg.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun FileBubbleContent(
    msg: Message.File,
    fg: Color,
    mine: Boolean,
    tapOpensFile: Boolean,
    transferProgress: Float? = null,
) {
    val isTransferring = msg.status == Message.File.Status.IN_PROGRESS
    val isDeleted = msg.status == Message.File.Status.DELETED
    val contentColor = if (isDeleted) fg.copy(alpha = 0.38f) else fg
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_description),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = contentColor,
        )
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(
                text = msg.name,
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge,
                // File names need deliberate emphasis inside the metadata block.
                fontWeight = FontWeight.Medium,
            )
            val status = when {
                isTransferring -> stringResource(
                    R.string.file_transferring,
                    ((transferProgress ?: 0f) * 100).toInt(),
                )
                msg.status == Message.File.Status.FAILED ->
                    stringResource(R.string.file_transfer_failed)
                msg.status == Message.File.Status.COMPLETED -> stringResource(
                    if (tapOpensFile) R.string.file_tap_to_open
                    else R.string.file_tap_for_actions,
                )
                else -> null
            }
            Text(
                text = if (isDeleted) {
                    stringResource(R.string.file_deleted)
                } else {
                    listOfNotNull(formatSize(msg.sizeBytes), status).joinToString("  ·  ")
                },
                color = if (isDeleted) contentColor else fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isTransferring && transferProgress != null) {
                Spacer(Modifier.height(Spacing.xs))
                LinearProgressIndicator(
                    progress = { transferProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = fg.copy(alpha = 0.9f),
                    trackColor = fg.copy(alpha = 0.2f),
                )
            }
        }
    }
}

internal fun formatSize(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes >= 1024L * 1024L) return "%.1f MB".format(bytes / 1048576.0)
    if (bytes >= 1024L) return "%.1f KB".format(bytes / 1024.0)
    return "$bytes B"
}
