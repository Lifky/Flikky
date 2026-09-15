package com.example.flikky.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListPolicyTest {
    private fun e(pkg: String, label: String, system: Boolean = false) = AppEntry(pkg, label, "1", 1, "/$pkg/base.apk", 0, 1, system)
    private val all = listOf(e("com.b", "Banana"), e("com.sys", "System Thing", true), e("com.a", "apple"), e("com.c", "樱桃"))
    @Test fun `system apps are hidden by default`() { assertEquals(listOf("com.a", "com.b", "com.c"), AppListPolicy.shape(all, false, "").map { it.packageName }) }
    @Test fun `system apps appear when asked for`() { assertTrue(AppListPolicy.shape(all, true, "").any { it.packageName == "com.sys" }) }
    @Test fun `ordering is case insensitive on the label`() { assertEquals(listOf("apple", "Banana", "樱桃"), AppListPolicy.shape(all, false, "").map { it.label }) }
    @Test fun `ordering uses the same comparator as the rest of the app`() {
        val rows = listOf(e("1", "文件2"), e("2", "文件10")); assertEquals(rows.sortedWith(compareBy(NAME_ORDER) { it.label }), AppListPolicy.shape(rows, false, ""))
    }
    @Test fun `query matches the label case insensitively`() { assertEquals(listOf("com.b"), AppListPolicy.shape(all, false, "BAN").map { it.packageName }) }
    @Test fun `query also matches the package name`() { assertEquals(listOf("com.c"), AppListPolicy.shape(all, false, "com.c").map { it.packageName }) }
    @Test fun `query is trimmed`() { assertEquals(1, AppListPolicy.shape(all, false, "  Banana  ").size) }
    @Test fun `blank query keeps everything`() { assertEquals(3, AppListPolicy.shape(all, false, "   ").size) }
    @Test fun `filtering and search compose`() { assertEquals(listOf("com.sys"), AppListPolicy.shape(all, true, "system").map { it.packageName }) }
}
