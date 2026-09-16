package com.example.flikky.ui.serving.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.example.flikky.R
import com.example.flikky.data.MediaStoreLibrary
import com.example.flikky.server.dto.AlbumBucketDto

import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.AlbumItemId

/**
 * 相册簿视图：封面 + 名字 + 张数，点进去看该簿的时间线。
 *
 * ## 为什么两种视图而不是一种
 *
 * 时间线回答「最近拍的」，相册簿回答「微信存的那张在哪」。用户装机后要的是两者
 * （2026-09-16 裁决），与 Android 系统相册的两个 tab 同构。
 *
 * 两列而不是三列：这里的卡片要放得下名字与张数，三列在手机竖屏上会让名字
 * 挤成一两个字加省略号。
 */
@Composable
fun AlbumBucketGrid(
    buckets: List<AlbumBucketDto>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Spacing.screenEdge,
            end = Spacing.screenEdge,
            top = Spacing.md,
            // 与时间线一致地给 FAB 留出高度，否则最后一行永远压在 FAB 下面。
            bottom = Spacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(buckets, key = { it.name }) { bucket ->
            AlbumBucketCard(bucket = bucket, onOpen = onOpen)
        }
    }
}

@Composable
private fun AlbumBucketCard(bucket: AlbumBucketDto, onOpen: (String) -> Unit) {
    val label = bucket.name.ifEmpty { stringResource(R.string.album_bucket_unknown) }
    Card(
        onClick = { onOpen(bucket.name) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            // 封面与格子走同一条缩略图路径（content Uri + Coil），
            // 所以相册簿封面与时间线里的同一张图必然长得一样。
            val uri = AlbumItemId.parse(bucket.coverId)?.let(MediaStoreLibrary::contentUri)
            if (uri != null) {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = Spacing.md,
                end = Spacing.md,
                top = Spacing.sm,
            ),
        )
        Text(
            text = stringResource(R.string.album_bucket_count, bucket.count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = Spacing.md,
                end = Spacing.md,
                bottom = Spacing.md,
            ),
        )
    }
}
