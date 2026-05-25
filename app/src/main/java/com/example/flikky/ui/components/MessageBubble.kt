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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flikky.session.Message
import com.example.flikky.session.Origin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: Message,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    transferProgress: Float? = null,
) {
    val mine = msg.origin == Origin.PHONE
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
    )
    val bg = if (mine) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface

    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bg)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { if (msg is Message.File) onClick() },
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when (msg) {
                is Message.Text -> SelectionContainer {
                    Text(
                        text = msg.content, color = fg,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                is Message.File -> FileBubbleContent(
                    msg = msg, fg = fg, mine = mine, transferProgress = transferProgress,
                )
            }
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
        Text(text = "📄", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = msg.name,
                color = fg,
                style = MaterialTheme.typography.bodyLarge,
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
                        !mine && msg.status == Message.File.Status.COMPLETED -> append("  ·  点击打开")
                        mine && msg.status == Message.File.Status.COMPLETED -> append("  ·  已发送")
                    }
                },
                color = fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isTransferring && transferProgress != null) {
                Spacer(Modifier.height(4.dp))
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
