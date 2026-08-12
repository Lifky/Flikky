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

/**
 * 收藏多选工具栏，动作集与文件总览一致（分享 / 移动 / 存相册 / 另存为 / 删除）。
 *
 * 每个文件类动作按自己的适用子集置灰：[fileTargetCount] 是有落盘副本的文件收藏数，
 * [mediaTargetCount] 再收窄到图片视频。文本收藏只能移动和删除，选中它们不会让分享变可点。
 */
@Composable
fun FavoritesSelectingToolbar(
    selectedCount: Int,
    fileTargetCount: Int,
    mediaTargetCount: Int,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onSaveToGallery: () -> Unit,
    onSaveAs: () -> Unit,
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
        IconButton(onClick = onShare, enabled = fileTargetCount > 0) {
            Icon(
                painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.favorites_share),
            )
        }
        IconButton(onClick = onSaveToGallery, enabled = mediaTargetCount > 0) {
            Icon(
                painterResource(R.drawable.ic_file_download),
                contentDescription = stringResource(R.string.files_action_gallery),
            )
        }
        IconButton(onClick = onSaveAs, enabled = fileTargetCount > 0) {
            Icon(
                painterResource(R.drawable.ic_save),
                contentDescription = stringResource(R.string.files_action_save_as),
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
