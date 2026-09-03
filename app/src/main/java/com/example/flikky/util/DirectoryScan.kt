package com.example.flikky.util

import java.io.File
import java.io.IOException
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

/**
 * 一次目录扫描里一个条目的「结构信息」：排序与首屏渲染需要的全部内容。
 *
 * 刻意**不含** `childCount`——那要为每个子目录再来一次 readdir，是目录密集的文件夹里
 * 最贵的一项。它按批懒算，见 [DirectoryScan.batches] 的用法。
 */
data class ScannedEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
)

/**
 * 目录枚举的共享内核：**两端同源**（服务端 `data/SharedStorageBrowser` 与 App 端
 * `ui/serving/storage/LocalStorageBrowser` 都调它）。
 *
 * ## 为什么用 `Files.readAttributes` 而不是 `java.io.File`
 *
 * `File.isDirectory()` / `length()` / `lastModified()` 各是一次 `stat` 系统调用。
 * 一个 2000 项的目录因此要 6000 次；`readAttributes` 一次拿全三样，降到 2000 次。
 * 这是「打开大文件夹很久」里最大的一块常数。
 *
 * 代价是**语义有一处必须补**：`readAttributes` 对扫描期间消失的条目会抛
 * [IOException]，而 `File.isDirectory()` 只是返回 false。不逐条兜住的话，
 * 「扫描时恰好有文件被删」会让整个目录列不出来。守卫见 `DirectoryScanTest`。
 *
 * ## 为什么排序必须在这里、在分批之前
 *
 * 顺序是「目录优先 + 名称不区分大小写」（[StorageListingPolicy]，两端共用）。
 * 要知道第一行是谁，就得先知道**所有**条目的 `isDir`——所以「发现即显示」与
 * 「稳定排序」不可兼得。2026-08-31 用户裁决保排序：先做这一遍廉价扫描（每条 1 次
 * stat），排完再分批流出。首屏等的是扫描而不是全部元数据，2000 项约几十毫秒。
 *
 * 无 Android 依赖，可在 `test/` 直接跑。
 */
object DirectoryScan {

    /**
     * 首批条数。刻意比后续批次小：首屏只要够填满一屏就行，越小越早出现。
     * 24 行在常见手机高度上已经超过一屏。
     */
    const val FIRST_BATCH = 24

    /**
     * 单批上限。指数增长不设上限的话，最后一批会是几万条，一次追加就把主线程顶住 ——
     * 正好抵掉分批的意义。
     */
    const val MAX_BATCH = 2048


    /**
     * 扫描 [dir] 并返回**已排好序**的结构信息；读不了返回 null。
     *
     * null 与空列表是两件事：null = 读不了（不是目录、无权限、IO 失败），
     * 空列表 = 读了，里面没东西。UI 对这两者的反应不同。
     *
     * [onCancelCheck] **每个条目**调一次（在读该条目属性之前），调用方在这里做
     * `ensureActive()`。扫描是个没有挂起点的紧循环，不主动检查的话协程已经取消了
     * 它还在跑，白烧几千次系统调用。
     */
    fun scan(
        dir: File,
        includeHidden: Boolean = false,
        onCancelCheck: () -> Unit = {},
    ): List<ScannedEntry>? {
        if (!dir.isDirectory) return null
        val collected = ArrayList<ScannedEntry>()
        var seen = 0
        try {
            Files.newDirectoryStream(dir.toPath()).use { stream ->
                for (path in stream) {
                    seen++
                    // 逐条调用，且在 readAttributes **之前**。
                    // 之前是每 128 条一次「省开销」，但一次 lambda 调用是纳秒级、
                    // 紧接着的 stat 是微秒级——省的那点相对开销不存在，而代价是
                    // 取消最多迟 128 条，且小目录里钩子一次都不触发
                    // （「消失的条目被跳过」那条测试因此空转全绿，逼红实测）。
                    onCancelCheck()
                    val name = path.fileName?.toString() ?: continue
                    // 逐条兜住：条目可能在我们读到它之前就被删了。整个目录不该因此列不出来。
                    val attrs = try {
                        Files.readAttributes(path, BasicFileAttributes::class.java)
                    } catch (e: IOException) {
                        continue
                    }
                    val isDir = attrs.isDirectory
                    collected += ScannedEntry(
                        name = name,
                        isDir = isDir,
                        // 目录不报文件系统给的目录大小：各平台不一致，且对用户无意义。
                        size = if (isDir) 0L else attrs.size(),
                        mtime = attrs.lastModifiedTime().toMillis(),
                    )
                }
            }
        } catch (e: IOException) {
            return null
        } catch (e: DirectoryIteratorException) {
            // 迭代过程中底层 readdir 失败。已经收到的条目仍然有效，但目录内容不完整，
            // 报「读不了」比给出一份沉默的残缺列表诚实。
            return null
        }
        return StorageListingPolicy.filterAndSort(
            collected,
            { it.isDir },
            { it.name },
            includeHidden,
        )
    }

    /**
     * 把已排序的条目切成批次：首批 [FIRST_BATCH]，之后**逐批翻倍**直到 [MAX_BATCH]。
     *
     * 纯切分——顺序与内容都不许变（少一条或顺序变了，用户看到的目录内容就是错的）。
     * 空输入返回空列表而不是一个空批次：一个空批次会让 UI 以为「来了一批，但是空的」，
     * 与「这个目录没有内容」混淆。
     */
    fun <T> batches(sorted: List<T>): List<List<T>> {
        // 不需要 `if (sorted.isEmpty()) return emptyList()`：下面的 while 条件本身
        // 就不会为空列表执行一次。加那行看着稳妥，实际不可达——逼红时零条红实证。
        val out = ArrayList<List<T>>()
        var from = 0
        var take = FIRST_BATCH
        while (from < sorted.size) {
            val to = minOf(from + take, sorted.size)
            out += sorted.subList(from, to)
            from = to
            // **翻倍**而不是固定批长。调用方每批做一次 `entries + batch`（O(n) 拷贝），
            // 固定 96 条时 10000 项要 104 次追加、累计约 50 万次元素复制，
            // 加载期间一直在造临时数组。翻倍把追加次数压到 O(log n)、累计拷贝约 2n，
            // 而前几批仍然很小 —— 用户此刻看的就是前几批。
            take = minOf(take * 2, MAX_BATCH)
        }
        return out
    }
}
