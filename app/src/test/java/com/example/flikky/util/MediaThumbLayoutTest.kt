package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 媒体气泡缩略图的主流聊天布局算法：等比缩放放进 maxW x maxH 边界框，不裁剪；
 * 仅当缩放结果的某条边小于 minEdge（极端长图）时抬到 minEdge、另一边取框边，
 * 由 ContentScale.Crop 中央裁剪兜底。
 */
class MediaThumbLayoutTest {

    private fun fit(w: Int, h: Int) = MediaThumbLayout.fit(
        srcWidth = w, srcHeight = h,
        maxWidth = 220f, maxHeight = 300f, minEdge = 96f,
    )

    @Test
    fun `landscape scales to full width with proportional height`() {
        val (w, h) = fit(1920, 1080)!!
        assertEquals(220f, w, 0.01f)
        assertEquals(123.75f, h, 0.01f)
    }

    @Test
    fun `portrait scales to full height with proportional width`() {
        val (w, h) = fit(1080, 1920)!!
        assertEquals(168.75f, w, 0.01f)
        assertEquals(300f, h, 0.01f)
    }

    @Test
    fun `square fills the smaller box edge`() {
        val (w, h) = fit(800, 800)!!
        assertEquals(220f, w, 0.01f)
        assertEquals(220f, h, 0.01f)
    }

    @Test
    fun `tall long screenshot keeps full content as a narrow strip`() {
        // 1080x2400 手机长截图：比例 1:2.222，等比缩放后宽 135 > minEdge，不裁剪。
        val (w, h) = fit(1080, 2400)!!
        assertEquals(135f, w, 0.01f)
        assertEquals(300f, h, 0.01f)
    }

    @Test
    fun `extremely tall image clamps width to min edge`() {
        // 500x5000：等比缩放宽只剩 30，抬到 minEdge=96、高取框高，由 Crop 兜底。
        val (w, h) = fit(500, 5000)!!
        assertEquals(96f, w, 0.01f)
        assertEquals(300f, h, 0.01f)
    }

    @Test
    fun `extremely wide image clamps height to min edge`() {
        val (w, h) = fit(5000, 500)!!
        assertEquals(220f, w, 0.01f)
        assertEquals(96f, h, 0.01f)
    }

    @Test
    fun `invalid dimensions return null`() {
        assertNull(fit(0, 100))
        assertNull(fit(100, 0))
        assertNull(fit(-5, -5))
    }
}
