package com.example.flikky.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯逻辑单测：[isTopLevelRoute] 区分底栏同级目的地（fade-through 转场）与父子详情目的地
 * （shared-axis 转场）。分类错 = 转场用错，故单测守住。
 */
class NavRouteTest {

    @Test
    fun `bottom-bar destinations are top level`() {
        assertTrue(isTopLevelRoute("transfer"))
        assertTrue(isTopLevelRoute("favorites"))
        assertTrue(isTopLevelRoute("settings"))
    }

    @Test
    fun `detail destinations are not top level`() {
        assertFalse(isTopLevelRoute("serving"))
        assertFalse(isTopLevelRoute("exporting"))
        assertFalse(isTopLevelRoute("history/{id}?highlight={messageId}"))
    }

    @Test
    fun `null route is not top level`() {
        assertFalse(isTopLevelRoute(null))
    }
}
