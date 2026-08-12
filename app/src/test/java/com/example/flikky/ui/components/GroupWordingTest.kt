package com.example.flikky.ui.components

import com.example.flikky.R
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏页的容器叫「合集」，首页会话的叫「分组」。共享组件的文案参数化后，两套词汇不能再串——
 * 串了就是 v1.17.0 装机看到的"同一屏里既有合集又有分组"。
 */
class GroupWordingTest {

    private val stringNames: Map<Int, String> = R.string::class.java.fields
        .associate { it.getInt(null) to it.name }

    private fun names(wording: GroupWording): List<String> = listOf(
        wording.newGroup,
        wording.groupName,
        wording.deleteGroup,
        wording.moveSheetTitle,
        wording.moveOutOfGroup,
    ).map { stringNames.getValue(it) }

    @Test
    fun favoritesWordingOnlyUsesFavoritesStrings() {
        val leaked = names(GroupWording.Favorites).filterNot { it.startsWith("favorites_") }
        assertTrue("收藏页文案漏用了非收藏资源：$leaked", leaked.isEmpty())
    }

    @Test
    fun sessionsWordingOnlyUsesHomeStrings() {
        val leaked = names(GroupWording.Sessions).filterNot { it.startsWith("home_") }
        assertTrue("首页文案漏用了非首页资源：$leaked", leaked.isEmpty())
    }

    @Test
    fun theTwoSetsShareNoString() {
        val shared = names(GroupWording.Favorites).intersect(names(GroupWording.Sessions).toSet())
        assertTrue("两套词汇共用了资源：$shared", shared.isEmpty())
    }
}
