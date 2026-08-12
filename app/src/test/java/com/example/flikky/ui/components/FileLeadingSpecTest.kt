package com.example.flikky.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件行 leading 的对齐守卫：缩略图与图标容器必须同占位，否则 ListItem 的 leading 槽宽度不同，
 * headline 起点就会在媒体行与非媒体行之间左右跳动（v1.17.0 装机反馈的原始缺陷）。
 */
class FileLeadingSpecTest {
    @Test
    fun thumbnailAndIconContainerShareTheSameFootprint() {
        assertEquals(40.dp, FileLeadingSpec.size)
    }

    @Test
    fun iconFitsInsideContainerWithBreathingRoom() {
        assertEquals(24.dp, FileLeadingSpec.iconSize)
        assertTrue(FileLeadingSpec.iconSize < FileLeadingSpec.size)
    }

    @Test
    fun iconContainerIsCircular() {
        assertEquals(CircleShape, FileLeadingSpec.iconContainerShape)
    }

    @Test
    fun thumbnailKeepsRoundedSquare() {
        assertEquals(RoundedCornerShape(8.dp), FileLeadingSpec.thumbnailShape)
    }

    @Test
    fun rowAlignmentIsCentered() {
        assertSame(Alignment.CenterVertically, FileLeadingSpec.rowAlignment)
    }
}
