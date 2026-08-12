package com.example.flikky.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页会话行的行内菜单集。进行中的会话在多选里本来就不可选（要先停止服务），
 * 所以它也不该有行内菜单——尾部留给「停止」按钮。
 */
class HomeRowMenuPolicyTest {

    @Test
    fun endedSessionOffersAllRowActions() {
        assertEquals(
            listOf(
                HomeRowAction.PIN,
                HomeRowAction.RENAME,
                HomeRowAction.MOVE,
                HomeRowAction.EXPORT,
                HomeRowAction.DELETE,
            ),
            HomeRowMenuPolicy.rowMenu(inProgress = false),
        )
    }

    @Test
    fun inProgressSessionHasNoRowMenu() {
        assertTrue(HomeRowMenuPolicy.rowMenu(inProgress = true).isEmpty())
    }

    @Test
    fun pinEntryFlipsWithCurrentState() {
        assertEquals(false, HomeRowMenuPolicy.pinTarget(pinned = true))
        assertEquals(true, HomeRowMenuPolicy.pinTarget(pinned = false))
    }
}
