package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FitFontSizeTest {

    @Test fun `the largest size is used when it fits`() {
        assertEquals(22f, fitFontSize(maxSp = 22f, minSp = 14f, stepSp = 1f) { true })
    }

    @Test fun `it steps down to the largest size that fits`() {
        // 模拟：宽度与字号成正比，18sp 及以下放得下。
        assertEquals(18f, fitFontSize(maxSp = 22f, minSp = 14f, stepSp = 1f) { it <= 18f })
    }

    @Test fun `it never goes below the minimum even if nothing fits`() {
        assertEquals(14f, fitFontSize(maxSp = 22f, minSp = 14f, stepSp = 1f) { false })
    }

    @Test fun `a step that skips past the minimum still lands on the minimum`() {
        assertEquals(14f, fitFontSize(maxSp = 22f, minSp = 14f, stepSp = 3f) { it < 16f })
    }
}
