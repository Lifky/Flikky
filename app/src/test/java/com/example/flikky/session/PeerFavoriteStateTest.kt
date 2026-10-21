package com.example.flikky.session

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** D78：推给浏览器的「本会话已收藏」集合 —— 允许对端收藏时是真实集合，否则为空。 */
class PeerFavoriteStateTest {

    @Test fun `the set follows the phone while peer favoriting is on`() = runTest {
        val gate = MutableStateFlow(true)
        val ids = MutableStateFlow(listOf(1L))
        peerFavoritedIds(gate, ids).test {
            assertEquals(listOf(1L), awaitItem())
            ids.value = listOf(1L, 2L)
            assertEquals(listOf(1L, 2L), awaitItem())
            ids.value = listOf(2L) // 手机上取消了 1
            assertEquals(listOf(2L), awaitItem())
        }
    }

    @Test fun `closing the gate empties the set and opening it restores the truth`() = runTest {
        val gate = MutableStateFlow(true)
        val ids = MutableStateFlow(listOf(7L))
        peerFavoritedIds(gate, ids).test {
            assertEquals(listOf(7L), awaitItem())
            gate.value = false
            assertEquals(emptyList<Long>(), awaitItem())
            ids.value = listOf(7L, 8L) // 关着时的变化不外发
            expectNoEvents()
            gate.value = true
            assertEquals(listOf(7L, 8L), awaitItem())
        }
    }

    @Test fun `unchanged sets are not pushed again`() = runTest {
        val gate = MutableStateFlow(true)
        val ids = MutableStateFlow(listOf(3L))
        peerFavoritedIds(gate, ids).test {
            assertEquals(listOf(3L), awaitItem())
            ids.value = listOf(3L)
            gate.value = true
            expectNoEvents()
        }
    }
    // v1.24 装机反馈：浏览器收藏面板不跟着更新。允许浏览器查看收藏时，收藏或合集一变就发一次「去重新拉」。
    @Test fun `the panel is nudged on every change while browsing is allowed`() = runTest {
        val gate = MutableStateFlow(true)
        val content = MutableStateFlow(listOf(1L))
        peerFavoritesChanged(gate, content).test {
            expectNoEvents() // 开着时的初值不算变化：浏览器开面板时自己会拉
            content.value = listOf(1L, 2L)
            assertEquals(Unit, awaitItem())
            content.value = listOf(2L)
            assertEquals(Unit, awaitItem())
        }
    }

    @Test fun `nothing is sent while browsing is off, and opening it is not a change`() = runTest {
        val gate = MutableStateFlow(false)
        val content = MutableStateFlow(listOf(1L))
        peerFavoritesChanged(gate, content).test {
            content.value = listOf(1L, 2L)
            expectNoEvents()
            gate.value = true // 打开时浏览器靠 data-favorite-enabled 自己拉一次
            expectNoEvents()
            content.value = listOf(3L)
            assertEquals(Unit, awaitItem())
        }
    }
}
