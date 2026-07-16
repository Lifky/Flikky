package com.example.flikky.ui.favorites

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.ui.components.FlikkyFloatingToolbar

@Composable
fun FavoritesSelectingToolbar(
    selectedCount: Int,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled = selectedCount > 0
    FlikkyFloatingToolbar {
        IconButton(onClick = onMove, enabled = enabled) {
            Icon(
                painterResource(R.drawable.ic_drive_file_move),
                contentDescription = stringResource(R.string.favorites_move_to_group),
            )
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.favorites_delete),
                tint = if (enabled) MaterialTheme.colorScheme.error else LocalContentColor.current,
            )
        }
    }
}
