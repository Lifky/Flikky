package com.example.flikky.util

import java.io.File
import java.io.IOException

/**
 * 客户端给的相对路径能不能落到共享存储根之下。
 *
 * 只有这一个事实源：路由层与 App 端本机浏览器都调它，禁止各自再写一遍前缀比对。
 *
 * 两处容易写错、且写错后**测试很难发现**的地方：
 * 1. 必须用 `canonicalFile` 而不是 `absoluteFile`——后者既不解析 `..` 也不跟符号链接，
 *    而共享存储里存在指向 `Android/` 的链接，纯字符串守卫会放过 `link/../../..` 这类构造。
 * 2. 前缀边界必须落在路径分隔符上。`/tmp/base0abc` 字面上以 `/tmp/base` 开头，但它不是
 *    `/tmp/base` 的子路径；`startsWith(rootPath)` 会把兄弟目录当成子目录放行。
 */
object StoragePathPolicy {

    /** 解析成功返回规范化后的绝对路径；越界、非法或解析失败返回 null。 */
    fun resolve(root: File, relative: String): File? {
        val rootCanon = try { root.canonicalFile } catch (e: IOException) { return null }
        val trimmed = relative.trim().trim('/')
        if (trimmed.isEmpty() || trimmed == ".") return rootCanon
        val target = try { File(rootCanon, trimmed).canonicalFile } catch (e: IOException) { return null }
        return if (isWithin(rootCanon, target)) target else null
    }

    /** 绝对路径 → 相对于 [root] 的相对路径，根本身为 `""`；越界返回 `""`。 */
    fun relativize(root: File, target: File): String {
        val rootCanon = try { root.canonicalFile } catch (e: IOException) { return "" }
        val targetCanon = try { target.canonicalFile } catch (e: IOException) { return "" }
        if (targetCanon.path == rootCanon.path) return ""
        if (!isWithin(rootCanon, targetCanon)) return ""
        return targetCanon.path
            .removePrefix(rootCanon.path + File.separator)
            .replace(File.separatorChar, '/')
    }

    private fun isWithin(root: File, target: File): Boolean {
        val rootPath = root.path
        val targetPath = target.path
        return targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
    }
}
