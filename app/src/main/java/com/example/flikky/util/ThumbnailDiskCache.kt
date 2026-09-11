package com.example.flikky.util

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Disk-backed thumbnail cache with a dynamically supplied byte ceiling. */
class ThumbnailDiskCache(
    private val directory: File,
    private val maxBytes: () -> Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    @Synchronized
    fun sizeBytes(): Long = directory.listFiles()
        .orEmpty()
        .filter(File::isFile)
        .sumOf(File::length)

    /** Returns a cached file and marks it as recently used. Reads never evict. */
    @Synchronized
    fun get(key: String): File? {
        val cached = directory.resolve(key)
        if (!cached.isFile) return null
        cached.setLastModified(clock())
        return cached
    }

    /**
     * Generates a thumbnail into a sibling temporary file, atomically publishes
     * it, then evicts least-recently-used files after the write.
     */
    @Synchronized
    fun put(key: String, generate: (File) -> Boolean): File? {
        val ceiling = maxBytes().coerceAtLeast(0L)
        if (ceiling == 0L) return null
        if (!directory.exists() && !directory.mkdirs()) return null

        val target = directory.resolve(key)
        val temp = File.createTempFile(".thumb-", ".tmp", directory)
        try {
            if (!generate(temp)) return null
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                // Do not fall back to a non-atomic copy: a partial thumbnail is
                // worse than a cache miss and the caller can regenerate it.
                return null
            }
            target.setLastModified(clock())
            evictTo(ceiling)
            return target.takeIf(File::isFile)
        } finally {
            temp.delete()
        }
    }

    @Synchronized
    fun clear() {
        directory.listFiles().orEmpty().filter(File::isFile).forEach(File::delete)
    }

    private fun evictTo(ceiling: Long) {
        var total = sizeBytes()
        if (total <= ceiling) return
        directory.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .sortedBy(File::lastModified)
            .forEach { candidate ->
                if (total <= ceiling) return@forEach
                val bytes = candidate.length()
                if (candidate.delete()) total -= bytes
            }
    }
}
