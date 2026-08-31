package com.example.flikky.ui.serving.storage

import com.example.flikky.util.StorageListingPolicy
import com.example.flikky.util.StoragePathPolicy
import java.io.File
import java.net.URLConnection

/** 列表里的一项。`relativePath` 是 UI 的 key 与选择集合的元素，不能只用 [name]。 */
data class LocalEntry(
    val name: String,
    val relativePath: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
    val mime: String?,
    val restricted: Boolean,
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
