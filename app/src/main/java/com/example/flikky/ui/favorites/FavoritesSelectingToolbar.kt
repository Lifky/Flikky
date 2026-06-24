package com.example.flikky.ui.favorites

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.theme.Spacing

@Composable
fun FavoritesSelectingToolbar(
    selectedCount: Int,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled = selectedCount > 0
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Row(modifier = androidx.compose.ui.Modifier.padding(horizontal = Spacing.xs)) {
            IconButton(onClick = onMove, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_drive_file_move), contentDescription = "移动到合集")
            }
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "删除",
                    tint = if (enabled) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
        }
    }
}
