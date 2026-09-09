package com.example.flikky.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.flikky.util.LeadingShape
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件行 leading 的对齐守卫：缩略图与图标容器必须同占位，否则 ListItem 的 leading 槽宽度不同，
 * headline 起点就会在媒体行与非媒体行之间左右跳动（v1.17.0 装机反馈的原始缺陷）。
 */
class FileLeadingSpecTest {
    private fun productSource(): String = File(
        "src/main/java/com/example/flikky/ui/components/FileLeadingVisual.kt",
    ).readText()

    private fun withoutCommentsAndImports(source: String): String = source
        .replace(Regex("""(?s)/\*.*?\*/"""), "")
        .replace(Regex("""(?m)//.*$"""), "")
        .replace(Regex("""(?m)^\s*import\s+.*$"""), "")

    @Test
    fun thumbnailAndIconContainerShareTheSameFootprint() {
        assertEquals(40.dp, FileLeadingSpec.size)
    }

    @Test
    fun iconDiameterStays24DpForEverySelectableShape() {
        LeadingShape.entries.forEach { shape ->
            assertEquals("${shape.id} must keep the standard icon diameter", 24.dp, FileLeadingSpec.iconSize)
            assertTrue(FileLeadingSpec.iconSize < FileLeadingSpec.size)
        }
    }

    /** 图标容器要的是 M3 Expressive 官方异形，退回普通圆形/圆角方形都算回归。 */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun iconContainerUsesExpressiveCookieShape() {
        val polygon = FileLeadingSpec.iconContainerPolygon(LeadingShape.Default)
        assertEquals(
            MaterialShapes.Cookie9Sided.cubics.size,
            polygon.cubics.size,
        )
        assertTrue(polygon.cubics.size > 4)
    }

    @Test
    fun thumbnailKeepsRoundedSquareRegardlessOfLeadingShape() {
        LeadingShape.entries.forEach { shape ->
            assertEquals(
                "${shape.id} must not crop thumbnail content into the decorative leading shape",
                RoundedCornerShape(8.dp),
                FileLeadingSpec.thumbnailShape,
            )
        }
        assertTrue(
            "The thumbnail rendering branch must keep using the fixed rounded-square shape",
            withoutCommentsAndImports(productSource())
                .contains(".clip(FileLeadingSpec.thumbnailShape)"),
        )
    }

    @Test
    fun fileLeadingVisualDoesNotHardCodeCookie9Sided() {
        val implementation = withoutCommentsAndImports(productSource())
        assertFalse(
            "FileLeadingVisual must resolve the selected shape instead of hard-coding Cookie9Sided",
            implementation.contains("Cookie9Sided"),
        )
    }

    @Test
    fun rowAlignmentIsCentered() {
        assertSame(Alignment.CenterVertically, FileLeadingSpec.rowAlignment)
    }
}
