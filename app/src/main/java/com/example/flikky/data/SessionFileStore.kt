package com.example.flikky.data

import com.example.flikky.server.routes.FileStore
import java.io.File
import java.io.InputStream

/**
 * 按会话分目录的文件存储：filesDir/sessions/{sessionId}/files/{fileId}
 * 单设备单用户，不需要 concurrent map；Kotlin 类的方法线程安全由 JVM 文件系统保证。
 */
class SessionFileStore(
    private val filesDir: File,
) : FileStore {

    override fun fileDir(sessionId: Long): File =
        File(File(filesDir, "sessions/$sessionId"), "files").apply { mkdirs() }

    /**
     * 从 InputStream 同步拷贝到 sessions/{sessionId}/files/{fileId}。
     * 流会被 close；抛异常时目标文件可能不完整，调用方需决定是否删除。
     */
    fun archiveFromStream(sessionId: Long, fileId: String, source: InputStream): File {
        val target = File(fileDir(sessionId), fileId)
        source.use { input ->
            target.outputStream().use { out -> input.copyTo(out) }
        }
        return target
    }

    fun deleteSessionDir(sessionId: Long): Boolean {
        val dir = File(filesDir, "sessions/$sessionId")
        return !dir.exists() || dir.deleteRecursively()
    }

    /** 枚举 filesDir/sessions 下的所有 sessionId 目录（用于孤儿清理）。 */
    fun listSessionDirs(): List<Long> {
        val root = File(filesDir, "sessions")
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.isDirectory }
            ?.mapNotNull { it.name.toLongOrNull() }
            ?: emptyList()
    }
}
