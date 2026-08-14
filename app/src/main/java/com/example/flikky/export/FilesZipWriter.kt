package com.example.flikky.export

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把一组落盘文件按原始文件名流式打包成纯文件 ZIP（不含导出 schema/manifest，
 * 与 [ZipExporter] 的会话归档是两种产物）。供 GET /api/files/archive 使用。
 */
object FilesZipWriter {

    data class Entry(val name: String, val file: File)

    private const val BUFFER_SIZE = 64 * 1024

    /** 与 ZipExporter 一致：finish/flush 但不 close [out]，流的所有权归 caller。 */
    fun write(out: OutputStream, entries: List<Entry>) {
        val names = uniqueNames(entries.map { it.name })
        val zip = ZipOutputStream(out, Charsets.UTF_8)
        entries.forEachIndexed { index, entry ->
            zip.putNextEntry(ZipEntry(names[index]))
            entry.file.inputStream().use { it.copyTo(zip, BUFFER_SIZE) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    /** 清洗路径分隔符与空名，并对重名追加 ` (n)`（保留扩展名）。 */
    internal fun uniqueNames(names: List<String>): List<String> {
        val used = HashSet<String>()
        return names.map { raw ->
            val cleaned = raw.replace('/', '_').replace('\\', '_').ifBlank { "file" }
            if (used.add(cleaned)) return@map cleaned
            val dot = cleaned.lastIndexOf('.')
            val base = if (dot > 0) cleaned.substring(0, dot) else cleaned
            val ext = if (dot > 0) cleaned.substring(dot) else ""
            var index = 1
            var candidate: String
            do {
                candidate = "$base ($index)$ext"
                index++
            } while (!used.add(candidate))
            candidate
        }
    }
}
