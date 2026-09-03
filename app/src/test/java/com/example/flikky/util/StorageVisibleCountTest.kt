package com.example.flikky.util

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 目录的**条目数**必须与**列表实际画出来的条目**用同一个判据。
 *
 * 装机验收（2026-09-03）：父目录副标题报「5 项」，进去只有 4 行，页脚也报「共 4 项」。
 * 那个第 5 项是一个 `.` 开头的隐藏文件——[StorageListingPolicy.filterAndSort] 刻意
 * 把它过滤掉了，而四处 `childCount` 全是裸的 `File.list()?.size`，不走这个策略。
 *
 * 这正是「两端共用一个策略对象」要防的那类不一致，只是漏在了计数这个点上：
 * 顺序和过滤共用了，计数没有。所以计数也得由策略拥有——
 * [StorageListingPolicy.visibleCount] 就是那个唯一出口，调用方不许再自己数。
 */
class StorageVisibleCountTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `visible count skips what the listing skips`() {
        val names = arrayOf("a.txt", ".hidden", "b.txt", "sub", ".cache")
        assertEquals(3, StorageListingPolicy.visibleCount(names))
    }

    @Test
    fun `visible count agrees with filterAndSort on the same input`() {
        // 这条是判据本身：两个函数在同一份输入上必须给出同一个数量。
        // 分别写死期望值的话，将来改了过滤规则只会有一边红。
        val names = listOf("a.txt", ".hidden", "Z.txt", ".DS_Store", "sub", "..weird")
        val listed = StorageListingPolicy.filterAndSort(
            items = names,
            isDir = { it == "sub" },
            name = { it },
        )
        assertEquals(
            "visibleCount and filterAndSort must not disagree",
            listed.size,
            StorageListingPolicy.visibleCount(names.toTypedArray()),
        )
    }

    @Test
    fun `an unreadable directory yields null, not zero`() {
        // File.list() 在读不到时返回 null。当成 0 会让副标题理直气壮地说「0 项」，
        // 而真相是「不知道」——UI 那边靠 null 退回「文件夹」这个不含数量的措辞。
        assertNull(StorageListingPolicy.visibleCount(null))
    }

    @Test
    fun `an empty directory yields zero`() {
        assertEquals(0, StorageListingPolicy.visibleCount(emptyArray()))
    }

    @Test
    fun `no listing site counts entries on its own`() {
        // 守卫：四处 childCount 都必须走策略。裸 `.list()?.size` / `.list()!!.size`
        // 是这个 bug 的原形，不许再出现在任何列举代码里。
        val roots = listOf(
            "src/main/java/com/example/flikky/data",
            "src/main/java/com/example/flikky/ui/serving/storage",
            "src/main/java/com/example/flikky/util",
        ).map { File(it).let { f -> if (f.exists()) f else File("app/$it") } }
        val offenders = mutableListOf<String>()
        roots.forEach { root ->
            if (!root.isDirectory) return@forEach
            root.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
                val src = f.readText()
                    .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""(?m)^\s*//.*$"""), "")
                if (Regex("""\.list\(\)\s*[?!]*\s*\.size""").containsMatchIn(src)) {
                    offenders += f.name
                }
            }
        }
        assertTrue(
            "directory entry counts must go through StorageListingPolicy.visibleCount, " +
                "or the count and the listing drift apart again. Offenders: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the guard can actually see a raw count`() {
        // 防空转：上面那条扫描必须真的认得出裸计数这个形状。
        val probe = "val n = dir.list()?.size"
        assertTrue(
            "the scan cannot recognise a raw count, so the guard proves nothing",
            Regex("""\.list\(\)\s*[?!]*\s*\.size""").containsMatchIn(probe),
        )
    }

    @Test
    fun `showing hidden files makes the count include them`() {
        val names = arrayOf("a.txt", ".hidden", "b.txt")
        assertEquals(2, StorageListingPolicy.visibleCount(names))
        assertEquals(3, StorageListingPolicy.visibleCount(names, includeHidden = true))
    }

    @Test
    fun `count and listing agree under both settings`() {
        // 这条才是判据本身。开关加进来之后，两个函数在**同一个开关值**下必须一致——
        // 只在默认值下对齐等于把 bug 挪到了开着的那一档。
        val names = listOf("a.txt", ".hidden", "Z.txt", ".DS_Store", "sub")
        listOf(false, true).forEach { includeHidden ->
            val listed = StorageListingPolicy.filterAndSort(
                items = names,
                isDir = { it == "sub" },
                name = { it },
                includeHidden = includeHidden,
            )
            assertEquals(
                "visibleCount and filterAndSort disagree with includeHidden=$includeHidden",
                listed.size,
                StorageListingPolicy.visibleCount(names.toTypedArray(), includeHidden),
            )
        }
    }

    @Test
    fun `both browsers pass the setting to the policy, not a hard-coded default`() {
        // 漏传的后果不是编译错误（两个参数都有默认值），而是那一端悄悄退回默认档——
        // 于是开关只对另一端生效。守卫钉住每个调用点都真的传了。
        val files = listOf(
            "src/main/java/com/example/flikky/data/SharedStorageBrowser.kt",
            "src/main/java/com/example/flikky/ui/serving/storage/LocalStorageBrowser.kt",
        ).map { File(it).let { f -> if (f.exists()) f else File("app/$it") } }
        files.forEach { f ->
            val src = f.readText()
                .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                .replace(Regex("""(?m)^\s*//.*$"""), "")
            assertTrue(
                "${f.name} must read the setting for every listing",
                src.contains("showHidden()"),
            )
            // visibleCount 的每个调用点都要带上它。
            val bare = Regex("""visibleCount\([^)]*\.list\(\)\)""").findAll(src).count()
            assertEquals(
                "${f.name} has $bare visibleCount calls that ignore the setting",
                0,
                bare,
            )
        }
    }
}
