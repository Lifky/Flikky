package com.example.flikky.ui.serving.storage

/**
 * 把跨目录攒下的选择集拆成「当前目录里的」与「别处的」。
 *
 * ## 为什么需要它
 *
 * 选择集刻意**跨目录保留** —— 用户可以逛几个文件夹、攒一批文件再一起发。但装机验收
 * （2026-09-03）暴露出代价：工具栏报「已选 3 项」而当前目录一行都没选中，用户既不知道
 * 那 3 项在哪，也无从判断按下发送会发出什么。
 *
 * 用户裁决：**保留这个能力，但把「有多少在别处」写清楚。** 这个对象就是那条判据，
 * 两端各自拿它算自己的文案（浏览器端 JS 侧另有一份同样规则的实现，
 * 因为那边拿不到 Kotlin —— 一致性靠两边的测试各自钉住同一组用例）。
 *
 * ## 「当前目录」的边界
 *
 * 只算**直接子项**。`DCIM/Camera/p.jpg` 在 DCIM 这一屏上看不到，把它算作 here
 * 会让计数与屏幕上的勾再次对不上 —— 正是这条要修的毛病。
 */
object StorageSelectionScope {

    data class Split(val here: Int, val elsewhere: Int)

    fun split(paths: Set<String>, dir: String): Split {
        val normalized = dir.trim().trim('/')
        var here = 0
        paths.forEach { if (parentOf(it) == normalized) here += 1 }
        return Split(here = here, elsewhere = paths.size - here)
    }

    /**
     * 相对路径的父目录。根下的裸文件名给空串。
     *
     * 用 `lastIndexOf('/')` 而不是前缀比较：`startsWith("Music/")` 会把
     * `MusicVideos/b.mp4` 也算进 Music（前缀比较的经典坑），而且它还分不清
     * 直接子项与更深层的孙子项。
     */
    private fun parentOf(path: String): String {
        val p = path.trim().trim('/')
        val cut = p.lastIndexOf('/')
        return if (cut < 0) "" else p.substring(0, cut)
    }
}
