package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.Sizes

@Composable
fun CircleActionButton(
    icon: Painter,
    contentDescription: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = if (danger) MaterialTheme.colorScheme.errorContainer
             else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (danger) MaterialTheme.colorScheme.onErrorContainer
             else MaterialTheme.colorScheme.onSecondaryContainer
    Box(Modifier.size(Sizes.touchTarget), Alignment.Center) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = bg,
            modifier = Modifier.size(Sizes.avatar),
        ) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(22.dp))
            }
        }
    }
}
