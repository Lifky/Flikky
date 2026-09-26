package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastNonNullTest {

    @Test fun `nothing seen yet stays null`() {
        assertNull(LastNonNull<String>().update(null))
    }

    @Test fun `a present value passes straight through`() {
        val last = LastNonNull<String>()
        assertEquals("a", last.update("a"))
        assertEquals("b", last.update("b"))
    }

    @Test fun `a value that goes away keeps showing the last one`() {
        // 停止服务后页面还在播返回动画：地址已清空，卡片得停在最后一帧（2026-09-27 装机反馈）。
        val last = LastNonNull<String>()
        last.update("http://192.168.3.6:8089")
        assertEquals("http://192.168.3.6:8089", last.update(null))
        assertEquals("http://192.168.3.6:8089", last.update(null))
    }
}
