package com.example.flikky.ui.favorites

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏行尾部 SplitButton 的形态守卫。官方两半都带 48dp 最小宽，直接用默认值会得到
 * 等宽的两颗按钮；MD3 示例是左长右短，所以 trailing 必须显式收窄。
 */
class FavoriteSplitButtonSpecTest {

    @Test
    fun usesExtraSmallContainerHeight() {
        assertEquals(32.dp, FavoriteSplitButtonSpec.height)
    }

    @Test
    fun trailingHalfIsShorterThanLeading() {
        assertTrue(FavoriteSplitButtonSpec.trailingWidth < FavoriteSplitButtonSpec.leadingMinWidth)
    }

    /** 收窄有下限：trailing 至少要放得下 22dp 图标。 */
    @Test
    fun trailingHalfStillFitsItsIcon() {
        assertTrue(FavoriteSplitButtonSpec.trailingWidth >= 32.dp)
    }
}
