package com.example.flikky.data

import com.example.flikky.server.dto.StorageEntryDto
import com.example.flikky.server.dto.StorageListDto
import com.example.flikky.server.routes.StorageBrowser
import com.example.flikky.server.routes.StorageFileHandle
import com.example.flikky.server.routes.StorageResult
import com.example.flikky.util.StorageListingPolicy
import com.example.flikky.util.StoragePathPolicy
import java.io.File
import java.net.URLConnection

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
class SharedStorageBrowser(private val root: File) : StorageBrowser {

    override fun list(relative: String): StorageResult<StorageListDto> {
        val dir = StoragePathPolicy.resolve(root, relative) ?: return StorageResult.InvalidPath
        val normalized = StoragePathPolicy.relativize(root, dir)
        if (StorageListingPolicy.isRestricted(normalized)) return StorageResult.Restricted
        if (!dir.isDirectory) return StorageResult.NotFound
        val children = dir.listFiles()?.toList() ?: return StorageResult.NotFound
        val sorted = StorageListingPolicy.filterAndSort(children, { it.isDirectory }, { it.name })
        return StorageResult.Ok(
            StorageListDto(path = normalized, entries = sorted.map { toEntry(it, normalized) }),
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
            childCount = if (isDir) file.list()?.size else null,
            restricted = StorageListingPolicy.isRestricted(childRelative),
        )
    }

    /**
     * 按扩展名猜 mime。用 `URLConnection.guessContentTypeFromName` 而不是 Android 的
     * `MimeTypeMap`：后者是 Android 框架类，会让本类不可在 JVM 上测。
     */
    private fun guessMime(name: String): String? = URLConnection.guessContentTypeFromName(name)
}
