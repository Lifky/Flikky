package com.example.flikky.ui.serving.storage

import com.example.flikky.util.StorageListingPolicy
import com.example.flikky.util.StoragePathPolicy
import java.io.File
import java.net.URLConnection

/**
 * 列表里的一项。
 *
 * [relativePath] 是 UI 的 key 与选择集合的元素，不能只用 [name]——两个不同目录下的同名文件
 * 会互相顶掉。[absolutePath] 是 Coil 加载缩略图要用的真实路径：只有相对路径时 Coil 打不开文件，
 * 而失败是**静默**的（回落成图标容器），媒体行看起来只是"没有缩略图"，没人会发现。
 */
data class LocalEntry(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
    val mime: String?,
    val restricted: Boolean,
)

/** 操作条摘要。[skipped] 涵盖「路径非法 / 已被删 / 是目录」三种情况。 */
data class StorageSelectionSummary(
    val count: Int,
    val totalBytes: Long,
    val skipped: Int,
)

/** 一次目录列举的结果。[selected] 由调用方持有并跨目录累积，这里只做纯变换。 */
data class LocalStorageState(
    val path: String,
    val entries: List<LocalEntry>,
    val selected: Set<String> = emptySet(),
)

/**
 * App 端本机共享存储浏览器：路径栈 + 选择集合的纯逻辑。不走 HTTP，直接读 [java.io.File]。
 *
 * ## 为什么过滤排序必须委派给 [StorageListingPolicy]
 *
 * App 端在手机上看到的顺序，和浏览器端在电脑上看到的顺序，必须一致。
 * 在这里自写一个比较器即便当下结果相同，服务端将来调整规则时也不会跟上，
 * 而**两端各自的测试都会是绿的**——这类不一致极难被发现。v1.18.0 的时间戳
 * 靠「双端同构纯函数」拿到一致性，这里照此办。
 *
 * ## 为什么本地路径也要过 [StoragePathPolicy]
 *
 * 这个类不面向网络，但路径拼接错误一样会读到不该读的地方（`..` 穿越、
 * 指向根外的符号链接）。守卫已经现成，绕过它只是省一行调用。
 *
 * 无 Android 依赖，可在 `test/` 直接跑（mime 用 [URLConnection] 而非 Android 的 MimeTypeMap）。
 */
class LocalStorageBrowser(private val root: File) {

    /**
     * 列举 [relative] 下的内容。返回 null 表示**不给进**：路径非法、不存在、不是目录，
     * 或落在系统锁死的沙箱里。UI 拿到 null 就停在原地，不要自动回退——
     * 自动回退会在「目录刚被删掉」时变成反复请求。
     */
    fun list(relative: String): LocalStorageState? {
        val dir = StoragePathPolicy.resolve(root, relative) ?: return null
        val path = StoragePathPolicy.relativize(root, dir)
        // 沙箱目录本身可以被**列出**（上一级的列表里要显示它），但不能被**进入**。
        if (StorageListingPolicy.isRestricted(path)) return null
        if (!dir.isDirectory) return null
        val children = dir.listFiles()?.toList() ?: return null
        val ordered = StorageListingPolicy.filterAndSort(
            children,
            { it.isDirectory },
            { it.name },
        )
        return LocalStorageState(
            path = path,
            entries = ordered.map { child ->
                val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
                val isDir = child.isDirectory
                LocalEntry(
                    name = child.name,
                    relativePath = childPath,
                    absolutePath = child.absolutePath,
                    isDir = isDir,
                    size = if (isDir) 0L else child.length(),
                    mtime = child.lastModified(),
                    mime = if (isDir) null else URLConnection.guessContentTypeFromName(child.name),
                    restricted = StorageListingPolicy.isRestricted(childPath),
                )
            },
        )
    }

    /**
     * 上一级的相对路径，根目录返回 **null**。
     *
     * 返回 null 而不是 `""` 是刻意的：`BackHandler` 的 `enabled` 直接用它判断，
     * 根目录返回 `""` 会让返回键在根目录也被拦住，用户困在文件 tab 里出不去。
     */
    fun parentOf(relative: String): String? {
        val trimmed = relative.trim().trim('/')
        if (trimmed.isEmpty()) return null
        val cut = trimmed.lastIndexOf('/')
        return if (cut < 0) "" else trimmed.substring(0, cut)
    }

    /** 单击勾选：在集合里就移除，不在就加入。跨目录累积由调用方持有集合实现。 */
    fun toggle(selected: Set<String>, relativePath: String): Set<String> =
        if (relativePath in selected) selected - relativePath else selected + relativePath

    /**
     * 操作条那一行「已选 N 项 · 合计大小」的数据，以及跳过数。
     *
     * **必须与 [resolveExisting] 算出同一个结果**——两处各判一遍是分叉的起点：
     * 摘要说「已选 2 项 · 8 KB」而实际只发出 1 个文件，用户会以为传输丢了数据，
     * 而两边各自的测试都能是绿的。因此这里直接复用 [resolveExisting]，不另写筛选。
     * 守卫见 `StorageSendSummaryTest.the summary agrees with what resolveExisting will actually send`。
     */
    fun selectionSummary(selected: Collection<String>): StorageSelectionSummary {
        val (files, skipped) = resolveExisting(selected)
        return StorageSelectionSummary(
            count = files.size,
            totalBytes = files.sumOf { it.length() },
            skipped = skipped,
        )
    }

    /**
     * 把选中的相对路径解析成真实文件，返回「存在的文件」与「跳过数」。
     *
     * 跳过的三种情况：路径非法、文件已不存在、目标是目录。
     * 目录那条不是多余的——选择集合理论上只装文件，但一旦混进目录，
     * `offerStoredFile` 会拿到一个目录并以 0 字节发出去，两端都不报错。
     * 跳过数要报给 snackbar（与 v1.17.1 收藏批量操作一致），不能静默丢弃。
     */
    fun resolveExisting(relativePaths: Collection<String>): Pair<List<File>, Int> {
        var skipped = 0
        val files = mutableListOf<File>()
        for (p in relativePaths) {
            val f = StoragePathPolicy.resolve(root, p)
            if (f == null || !f.isFile) {
                skipped++
                continue
            }
            files += f
        }
        return files to skipped
    }
}

/** 点一行会发生什么。UI 只许在这三个分支上分派，不许自己再判 `isDir` / `restricted`。 */
enum class StorageRowAction {
    /** 目录：进入。**不进选择集合**——裁决 B 保留「单击即勾选」但只对文件生效。 */
    OPEN,

    /** 文件：切换勾选。跨目录累积。 */
    TOGGLE,

    /** 系统锁死的沙箱目录：不可点、不可进、不可选。行仍要显示，用户得知道它存在。 */
    NONE,
}

/**
 * 行为决策抽成纯函数的理由：这三条（目录不可选 / 文件单击即选 / 沙箱行完全惰性）
 * 全都是「写错了照样编译、照样跑」的规则，而验它们最自然的地方是仪器测试——
 * 那要立整屏 + ViewModel + ServiceLocator。抽出来后在 `test/` 秒级可验，
 * 且 UI 侧只剩一个 `when`，把「判断」和「渲染」分开。
 *
 * `restricted` 必须在 `isDir` **之前**判：沙箱目录同时满足两者，
 * 顺序颠倒就会让 `Android/data` 变成可进入的普通目录。
 */
fun storageRowAction(entry: LocalEntry): StorageRowAction = when {
    entry.restricted -> StorageRowAction.NONE
    entry.isDir -> StorageRowAction.OPEN
    else -> StorageRowAction.TOGGLE
}

/** 面包屑的一级。[path] 为空表示根。 */
data class StorageCrumb(val label: String, val path: String)

/**
 * 面包屑的分级与折叠规则。**与浏览器端 `panel-files.js` 的 `breadcrumbSegments` 同构**：
 * 总级数 ≤ 4 时全显示；超过则首级 + `…` + 末两级。
 *
 * 返回列表里的 `null` 就是那个 `…` 占位——UI 渲染成不可点的省略号。
 * 两端各写一遍的后果是「手机上折叠、电脑上不折叠」，而两边测试都绿。
 *
 * [rootLabel] 由调用方给（i18n），纯函数本身不碰资源，所以能在 `test/` 直接跑。
 */
fun breadcrumbSegments(path: String, rootLabel: String): List<StorageCrumb?> {
    val parts = path.trim().trim('/').split('/').filter { it.isNotEmpty() }
    val all = buildList {
        add(StorageCrumb(rootLabel, ""))
        parts.forEachIndexed { i, name ->
            add(StorageCrumb(name, parts.take(i + 1).joinToString("/")))
        }
    }
    if (all.size <= 4) return all
    return buildList {
        add(all.first())
        add(null)
        addAll(all.takeLast(2))
    }
}
