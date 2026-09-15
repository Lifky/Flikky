package com.example.flikky.data

import com.example.flikky.server.dto.StorageEntryDto
import com.example.flikky.server.dto.StorageListDto
import com.example.flikky.server.routes.StorageBrowser
import com.example.flikky.server.routes.StorageFileHandle
import com.example.flikky.server.routes.StorageResult
import com.example.flikky.server.routes.StorageStream
import com.example.flikky.util.DirectoryScan
import com.example.flikky.util.ScannedEntry
import com.example.flikky.util.SortSpec
import com.example.flikky.util.StorageListingPolicy
import com.example.flikky.util.StoragePathPolicy
import java.io.File
import com.example.flikky.util.MimeGuess
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow

/**
 * 共享存储的只读实现。
 *
 * **接 [root] 构造参数而不是自己去问 `Environment`**：这样整个类零 Android 依赖，可以在
 * `test/` 用 tmpdir 直测。只有 `di/ServiceLocator` 认识
 * `Environment.getExternalStorageDirectory()`。权限检查也不在这里——它是路由的门禁，
 * 不是存储访问的一部分。
 *
 * 过滤与排序一律走 [StorageListingPolicy]，**禁止在这里另写一遍**：App 端本机浏览器调的是
 * 同一个对象，各写一遍就会让两端顺序不一致，而两边各自的测试都会是绿的。
 */
class SharedStorageBrowser(
    private val root: File,
    /**
     * 用户是否打开了「显示隐藏文件」。
     *
     * 每次列举都现取（lambda 而不是布尔值）：设置随时可改，而这个对象活在
     * ServiceLocator 里、生命周期比一次设置变更长得多。
     */
    private val showHidden: () -> Boolean = { false },
) : StorageBrowser {

    override fun list(relative: String, sort: SortSpec): StorageResult<StorageListDto> {
        val dir = StoragePathPolicy.resolve(root, relative) ?: return StorageResult.InvalidPath
        val normalized = StoragePathPolicy.relativize(root, dir)
        if (StorageListingPolicy.isRestricted(normalized)) return StorageResult.Restricted
        if (!dir.isDirectory) return StorageResult.NotFound
        val children = dir.listFiles()?.toList() ?: return StorageResult.NotFound
        val sorted = StorageListingPolicy.filterAndSort(
            children,
            { it.isDirectory },
            { it.name },
            showHidden(),
            // 目录恒报 0：文件系统给的目录大小各平台不一致，对用户也无意义。
            // 与 `ScannedEntry` / `LocalStorageBrowser` 的同名裁决一致。
            { if (it.isDirectory) 0L else it.length() },
            { it.lastModified() },
            sort,
        )
        return StorageResult.Ok(
            StorageListDto(path = normalized, entries = sorted.map { toEntry(it, normalized) }),
        )
    }

    /**
     * 流式列举：先确认路径，再分批产出条目。
     *
     * 与 [list] 用**同一个**扫描内核（[DirectoryScan]），所以两条路径给出的顺序与
     * 内容必然一致——各写一遍就会出现「流式和非流式看到的目录不一样」，
     * 而两边各自的测试都能是绿的。
     *
     * 逐批 `emit` 是取消检查点；扫描内部的紧循环没有挂起点，所以把 `ensureActive`
     * 传进去当钩子。浏览器中断连接时这条流会被取消，枚举随即停止。
     */
    override fun listStream(
        relative: String,
        sort: SortSpec,
    ): StorageResult<StorageStream> {
        val dir = StoragePathPolicy.resolve(root, relative) ?: return StorageResult.InvalidPath
        val normalized = StoragePathPolicy.relativize(root, dir)
        if (StorageListingPolicy.isRestricted(normalized)) return StorageResult.Restricted
        if (!dir.isDirectory) return StorageResult.NotFound
        val batches = flow {
            val ctx = currentCoroutineContext()
            val scanned = DirectoryScan.scan(dir, showHidden(), sort) { ctx.ensureActive() }
                ?: return@flow
            for (batch in DirectoryScan.batches(scanned)) {
                currentCoroutineContext().ensureActive()
                emit(batch.map { toEntry(it, normalized, dir) })
            }
        }
        return StorageResult.Ok(StorageStream(path = normalized, batches = batches))
    }

    /** [ScannedEntry] → DTO。`childCount` 在这里算，所以它是按批发生的。 */
    private fun toEntry(
        scanned: ScannedEntry,
        parentPath: String,
        parentDir: File,
    ): StorageEntryDto {
        val childPath =
            if (parentPath.isEmpty()) scanned.name else "$parentPath/${scanned.name}"
        val child = File(parentDir, scanned.name)
        return StorageEntryDto(
            name = scanned.name,
            isDir = scanned.isDir,
            size = scanned.size,
            mtime = scanned.mtime,
            mime = if (scanned.isDir) {
                null
            } else {
                MimeGuess.fromName(scanned.name)
            },
            // list() 而不是 listFiles()：只要个数，不需要为每个子项建 File 对象。
            childCount = if (scanned.isDir) StorageListingPolicy.visibleCount(child.list(), showHidden()) else null,
            restricted = StorageListingPolicy.isRestricted(childPath),
        )
    }

    override fun open(relative: String): StorageResult<StorageFileHandle> {
        val file = StoragePathPolicy.resolve(root, relative) ?: return StorageResult.InvalidPath
        val normalized = StoragePathPolicy.relativize(root, file)
        if (StorageListingPolicy.isRestricted(normalized)) return StorageResult.Restricted
        if (!file.isFile) return StorageResult.NotFound
        return StorageResult.Ok(
            StorageFileHandle(
                file = file,
                fileName = file.name,
                mime = guessMime(file.name) ?: "application/octet-stream",
            ),
        )
    }

    private fun toEntry(file: File, parentRelative: String): StorageEntryDto {
        val isDir = file.isDirectory
        val childRelative = if (parentRelative.isEmpty()) file.name else "$parentRelative/${file.name}"
        return StorageEntryDto(
            name = file.name,
            isDir = isDir,
            size = if (isDir) 0L else file.length(),
            mtime = file.lastModified(),
            mime = if (isDir) null else guessMime(file.name),
            childCount = if (isDir) StorageListingPolicy.visibleCount(file.list(), showHidden()) else null,
            restricted = StorageListingPolicy.isRestricted(childRelative),
        )
    }

    /**
     * 按扩展名猜 mime。用 `URLConnection.guessContentTypeFromName` 而不是 Android 的
     * `MimeTypeMap`：后者是 Android 框架类，会让本类不可在 JVM 上测。
     */
    private fun guessMime(name: String): String? = MimeGuess.fromName(name)
}
