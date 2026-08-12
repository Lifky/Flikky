package com.example.flikky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.flikky.ui.files.FileCategory
import com.example.flikky.ui.files.iconResource

/**
 * 文件行 leading 的尺寸/形状唯一事实源。
 *
 * 对齐的本质是**占位相同**：`ListItem` 的 leading 槽按内容宽度测量，缩略图与图标容器只要宽度不等，
 * headline 起点就会在媒体行与非媒体行之间左右跳动。因此两种形态共用同一个 [size]。
 * 守卫单测见 `ui/components/FileLeadingSpecTest`。
 */
internal object FileLeadingSpec {
    /** 缩略图与图标容器的统一占位（MD3 leading avatar container 尺寸）。 */
    val size: Dp = 40.dp

    /** 图标容器内的分类图标直径（M3 `Icon` 标准 24dp）。 */
    val iconSize: Dp = 24.dp

    /** 图片/视频缩略图形状。 */
    val thumbnailShape: Shape = RoundedCornerShape(8.dp)

    /** 非媒体文件的图标容器形状（MD3 leading avatar container 默认圆形）。 */
    val iconContainerShape: Shape = CircleShape

    /** 文件行的垂直对齐：headline 换行时 leading 仍居中。 */
    val rowAlignment: Alignment.Vertical = Alignment.CenterVertically
}

/**
 * 文件行的 leading 视觉，四处文件行（文件总览 / 收藏页 / 文件快发 Sheet / 收藏快发 Sheet）唯一实现。
 *
 * - [thumbnailModel] 非空 → 渲染 [FileLeadingSpec.size] 的圆角方形缩略图（图片/视频）。
 * - [thumbnailModel] 为空、或缩略图解码失败 → 渲染同占位的圆形容器 + 分类图标。
 *
 * 解码失败回落到容器而不是把 24dp 矢量图拉伸到 40dp，保证失败行与其他非媒体行长得一样。
 *
 * @param selected 多选选中态。选中行底色为 primaryContainer，容器需翻到 primary 才拉开对比。
 */
@Composable
internal fun FileLeadingVisual(
    category: FileCategory,
    thumbnailModel: Any?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    var thumbnailFailed by remember(thumbnailModel) { mutableStateOf(false) }
    if (thumbnailModel != null && !thumbnailFailed) {
        AsyncImage(
            model = thumbnailModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { thumbnailFailed = true },
            modifier = modifier
                .size(FileLeadingSpec.size)
                .clip(FileLeadingSpec.thumbnailShape),
        )
    } else {
        Box(
            modifier = modifier
                .size(FileLeadingSpec.size)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = FileLeadingSpec.iconContainerShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(category.iconResource()),
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
                modifier = Modifier.size(FileLeadingSpec.iconSize),
            )
        }
    }
}
