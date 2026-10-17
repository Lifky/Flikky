package com.example.flikky.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 叠放的头像（v1.23 多设备）：后一个压在前一个上面，露出三分之二，描一圈 [ringColor] 把彼此分开。
 * 调用方负责截到最多 3 个 —— 再多就看不清了，台数由旁边的文字说。
 */
@Composable
fun AvatarStack(
    avatarKeys: List<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.surface,
) {
    if (avatarKeys.isEmpty()) return
    val step = size * 2 / 3
    Box(modifier.width(size + step * (avatarKeys.size - 1)).height(size)) {
        avatarKeys.forEachIndexed { i, key ->
            Avatar(
                avatarKey = key,
                size = size,
                modifier = Modifier
                    .offset(x = step * i)
                    .then(if (avatarKeys.size > 1) Modifier.border(2.dp, ringColor, CircleShape) else Modifier),
            )
        }
    }
}
