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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.theme.Spacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: Message,
    onTap: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    transferProgress: Float? = null,
    showAvatar: Boolean = true,
    avatarId: Int? = null,
    cornerRadius: Dp = 18.dp,
    selected: Boolean = false,
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
        if (showAvatar && avatarId != null) {
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
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bg)
                .then(clickModifier)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            when (msg) {
                is Message.Text -> Text(
                    text = msg.content, color = fg,
                    style = MaterialTheme.typography.bodyLarge.merge(
                        TextStyle(lineBreak = LineBreak.Paragraph)
                    ),
                )
                is Message.File -> FileBubbleContent(
                    msg = msg, fg = fg, mine = mine,
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
private fun FileBubbleContent(
    msg: Message.File,
    fg: Color,
    mine: Boolean,
    transferProgress: Float? = null,
) {
    val isTransferring = msg.status == Message.File.Status.IN_PROGRESS
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_description),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = fg,
        )
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(
                text = msg.name,
                color = fg,
                style = MaterialTheme.typography.bodyLarge,
                // File names need deliberate emphasis inside the metadata block.
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append(formatSize(msg.sizeBytes))
                    when {
                        isTransferring -> {
                            val pct = ((transferProgress ?: 0f) * 100).toInt()
                            append("  ·  传输中 $pct%")
                        }
                        msg.status == Message.File.Status.FAILED -> append("  ·  传输失败")
                        msg.status == Message.File.Status.COMPLETED -> append("  ·  点击打开")
                    }
                },
                color = fg.copy(alpha = 0.75f),
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
