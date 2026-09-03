package com.example.flikky.ui.serving.storage

/**
 * 逛过的目录留一份，回退时直接拿 —— **不重新枚举文件系统**。
 *
 * ## 为什么值得
 *
 * 回退到一个上千项的父目录，原本要把 readdir + 每条一次 stat 全部重做一遍，
 * 用户看着它重新流式长出来。而那份内容一分钟前刚读过。
 *
 * ## 刻意不做自动刷新
 *
 * 用户在子目录待久了，父目录可能已经变了（新增 / 删除）。这里**不**自动重取——
 * 那会把「秒回」变回「每次都等」。用户裁决（2026-09-03）：先保住秒回的流畅，
 * 内容变了由用户手动刷新。所以这个类没有任何时效判断，是纯粹的 LRU。
 *
 * ## 安全边界：两个上限都要卡
 *
 * 只卡目录数不行——32 个各含一万条的目录一样会撑爆内存。
 * 只卡总条目数也不行——会让极多的小目录把链表撑得很长。
 *
 * ## 线程
 *
 * 只在主线程（ViewModel）读写。[LinkedHashMap] 不是线程安全的，也不需要是。
 */
class StorageDirectoryCache {

    /** 缓存里的一格：内容 + 用户当时看到哪儿。 */
    data class Snapshot(
        val entries: List<LocalEntry>,
        val scrollIndex: Int,
        val scrollOffset: Int,
    )

    /**
     * `accessOrder = true` 的 [LinkedHashMap] 把**读**也算作一次使用，
     * 于是它天然是 LRU 而不是 FIFO。少了这个参数，一直在用的目录会因为
     * 「存得早」被淘汰（守卫：`reading a directory makes it recent again`）。
     */
    private val map = LinkedHashMap<String, Snapshot>(16, 0.75f, true)

    /** 缓存里的总条目数。自己记而不是每次求和：淘汰循环里会反复读它。 */
    var entryCount: Int = 0
        private set

    fun get(path: String): Snapshot? = map[path]

    fun put(path: String, entries: List<LocalEntry>, scrollIndex: Int, scrollOffset: Int) {
        map.remove(path)?.let { entryCount -= it.entries.size }
        // 单个目录就超过总预算时不存它：存进去会把其他所有目录挤光，
        // 而它自己下次也一定被淘汰，白占一轮。
        if (entries.size > MAX_ENTRIES) return
        // 存副本。调用方手里的列表后来被改动，缓存里这份不该跟着变——
        // 否则「秒回」回来的是一份被改过的历史。
        map[path] = Snapshot(entries.toList(), scrollIndex, scrollOffset)
        entryCount += entries.size
        evict(keep = path)
    }

    /**
     * 丢掉一个目录。手动刷新用这个，**不是** [clear] ——
     * 刷新一个目录不该把其他目录的缓存也扔了（浏览器端一直是这个语义，
     * App 端 2026-09-03 的针对性审查发现走的是 clear，秒回一次性归零）。
     */
    fun remove(path: String) {
        map.remove(path)?.let { entryCount -= it.entries.size }
    }

    fun clear() {
        map.clear()
        entryCount = 0
    }

    private fun evict(keep: String) {
        while (map.size > MAX_DIRS || entryCount > MAX_ENTRIES) {
            val victim = map.keys.firstOrNull { it != keep } ?: break
            map.remove(victim)?.let { entryCount -= it.entries.size }
        }
    }

    companion object {
        const val MAX_DIRS = 32
        const val MAX_ENTRIES = 20000
    }
}
