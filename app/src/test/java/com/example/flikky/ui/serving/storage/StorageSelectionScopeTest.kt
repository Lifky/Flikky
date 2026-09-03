package com.example.flikky.ui.serving.storage

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 选择集**跨目录保留**（两端一直如此），但用户得看得见这件事。
 *
 * 装机验收（2026-09-03）：工具栏可能报「已选 3 项」，而当前目录一行都没选中 ——
 * 用户无从知道那 3 项在哪，也无从判断按下发送会发出什么。用户裁决：
 * **保留跨目录攒文件的能力，但把「有多少在别处」写清楚。**
 *
 * 拆分规则本身是纯函数，两端各自用它算自己的文案。
 */
class StorageSelectionScopeTest {

    private fun split(paths: Set<String>, dir: String) =
        StorageSelectionScope.split(paths, dir)

    @Test
    fun `entries directly inside the current directory count as here`() {
        val s = split(setOf("DCIM/a.jpg", "DCIM/b.jpg"), "DCIM")
        assertEquals(2, s.here)
        assertEquals(0, s.elsewhere)
    }

    @Test
    fun `entries in other directories count as elsewhere`() {
        val s = split(setOf("DCIM/a.jpg", "Music/x.mp3", "Music/y.mp3"), "DCIM")
        assertEquals(1, s.here)
        assertEquals(2, s.elsewhere)
    }

    @Test
    fun `a nested subdirectory is elsewhere, not here`() {
        // 「当前目录」是这一屏能看到的那些行。DCIM/Camera/p.jpg 在 DCIM 里看不到，
        // 把它算作 here 会让计数与屏幕上的勾再次对不上——正是这条要修的毛病。
        val s = split(setOf("DCIM/Camera/p.jpg", "DCIM/a.jpg"), "DCIM")
        assertEquals(1, s.here)
        assertEquals(1, s.elsewhere)
    }

    @Test
    fun `the root directory holds bare names`() {
        val s = split(setOf("a.txt", "DCIM/b.jpg"), "")
        assertEquals(1, s.here)
        assertEquals(1, s.elsewhere)
    }

    @Test
    fun `a directory whose name is a prefix of another is not confused with it`() {
        // 前缀比较的经典坑：Music 与 MusicVideos。
        val s = split(setOf("Music/a.mp3", "MusicVideos/b.mp4"), "Music")
        assertEquals(1, s.here)
        assertEquals(1, s.elsewhere)
    }

    @Test
    fun `an empty selection splits into nothing`() {
        val s = split(emptySet(), "DCIM")
        assertEquals(0, s.here)
        assertEquals(0, s.elsewhere)
    }

    @Test
    fun `here plus elsewhere always accounts for every selected path`() {
        val paths = setOf("a.txt", "DCIM/b.jpg", "DCIM/Camera/c.jpg", "Music/d.mp3")
        listOf("", "DCIM", "DCIM/Camera", "Music", "Nope").forEach { dir ->
            val s = split(paths, dir)
            assertEquals(
                "nothing may be lost or double counted for dir='$dir'",
                paths.size,
                s.here + s.elsewhere,
            )
        }
    }
}
