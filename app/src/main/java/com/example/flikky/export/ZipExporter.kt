package com.example.flikky.export

import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Streams an [ExportSnapshot] into a single zip archive following the v1.2
 * design spec (§4.1 / §4.2).
 *
 * Pure Kotlin / java.util.zip; zero Android dependencies. The caller owns the
 * lifecycle of [out] — this object writes a complete zip stream and finishes
 * (`ZipOutputStream.finish()`) but does **not** close the underlying
 * [OutputStream].
 *
 * Layout produced:
 * ```
 * README.txt
 * sessions/{id}_{safeName}/
 *     messages.txt
 *     messages.json
 *     files/{originalName}     (with _2/_3 suffixes on intra-session collisions)
 * ```
 *
 * Trade-offs (intentional for v1.2):
 * - `messages.json`'s `relativePath` field is computed by
 *   [MessagesJsonFormatter] before de-duplication, so JSON values may not
 *   match the actual zip entry path when names collide. v1.3 will unify.
 * - Missing files (resolver returns `null` or file does not exist) are written
 *   as `files/MISSING_{fileId}.txt` placeholders rather than aborting the
 *   whole export. A diagnostic line goes to stderr.
 */
object ZipExporter {

    private const val README_PATH = "README.txt"
    private const val SESSIONS_DIR = "sessions/"
    private const val INVALID_NAME_CHARS = "/\\*?\"<>|\n\r"
    private const val SAFE_NAME_MAX = 40
    private const val README_TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss"
    private const val BUFFER_SIZE = 8 * 1024

    fun write(
        out: OutputStream,
        snapshot: ExportSnapshot,
        fileResolver: (sessionId: Long, fileId: String) -> File?,
        timeZone: TimeZone = TimeZone.getDefault(),
    ) {
        val zip = ZipOutputStream(out, Charsets.UTF_8)
        try {
            writeReadme(zip, snapshot, timeZone)
            for (session in snapshot.sessions) {
                writeSession(zip, session, fileResolver, timeZone)
            }
            zip.finish()
            zip.flush()
        } catch (t: Throwable) {
            // Don't close `out`; caller owns it. Just rethrow.
            throw t
        }
    }

    // --- README ---

    private fun writeReadme(zip: ZipOutputStream, snapshot: ExportSnapshot, timeZone: TimeZone) {
        val totalMessages = snapshot.sessions.sumOf { it.messages.size }
        val totalFiles = snapshot.sessions.sumOf { s -> s.messages.count { it is MessageExport.File } }
        val totalBytes = snapshot.sessions.sumOf { s ->
            s.messages.filterIsInstance<MessageExport.File>().sumOf { it.sizeBytes }
        }
        val sessionCount = snapshot.sessions.size

        val df = SimpleDateFormat(README_TIMESTAMP_PATTERN, Locale.US).apply {
            this.timeZone = timeZone
        }
        val tz = SimpleDateFormat("Z", Locale.US).apply { this.timeZone = timeZone }
        val exportedStr = df.format(Date(snapshot.exportedAt))
        val tzStr = tz.format(Date(snapshot.exportedAt))

        val sb = StringBuilder()
        sb.append("Flikky Export\n")
        sb.append("Version: 1.4\n")
        sb.append("Exported at: ").append(exportedStr).append(" (").append(tzStr).append(")\n")
        sb.append("Sessions: ").append(sessionCount).append('\n')
        sb.append("Total messages: ").append(totalMessages).append('\n')
        sb.append("Total files: ").append(totalFiles).append('\n')
        sb.append("Total bytes: ").append(totalBytes).append('\n')
        sb.append('\n')
        sb.append("此 zip 包含你从 Flikky 手机端勾选导出的 ").append(sessionCount).append(" 条会话。\n")
        sb.append("每个会话一个子目录，内含 messages.txt（纯文本消息日志）、messages.json（结构化数据）和 files/（原始附件）。\n")

        writeTextEntry(zip, README_PATH, sb.toString())
    }

    // --- per-session ---

    private fun writeSession(
        zip: ZipOutputStream,
        session: SessionExport,
        fileResolver: (Long, String) -> File?,
        timeZone: TimeZone,
    ) {
        val dir = SESSIONS_DIR + session.id + "_" + safeName(session.name) + "/"
        val fileMessages = session.messages.filterIsInstance<MessageExport.File>()

        // Pre-compute deduplicated names so messages.json relativePath matches zip entries
        val seen = mutableMapOf<String, Int>()
        val fileIdToRelativePath = mutableMapOf<String, String>()
        val fileIdToEntryName = mutableMapOf<String, String>()
        for (fm in fileMessages) {
            val entryName = nextUniqueName(seen, fm.name)
            fileIdToRelativePath[fm.fileId] = "files/$entryName"
            fileIdToEntryName[fm.fileId] = entryName
        }

        writeTextEntry(zip, dir + "messages.txt", MessagesTextFormatter.format(session, timeZone))
        writeTextEntry(zip, dir + "messages.json",
            MessagesJsonFormatter.format(session, fileIdToRelativePath = fileIdToRelativePath))

        for (fm in fileMessages) {
            val entryName = fileIdToEntryName[fm.fileId] ?: fm.name
            val path = dir + "files/" + entryName
            val resolved = fileResolver(session.id, fm.fileId)
            if (resolved == null || !resolved.exists() || !resolved.isFile) {
                System.err.println("ZipExporter: missing file ${fm.fileId} for session ${session.id}")
                writeTextEntry(
                    zip,
                    dir + "files/MISSING_${fm.fileId}.txt",
                    "文件不存在 / File missing at export time"
                )
            } else {
                writeFileEntry(zip, path, resolved)
            }
        }
    }

    // --- low-level helpers ---

    private fun writeTextEntry(zip: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeFileEntry(zip: ZipOutputStream, name: String, source: File) {
        val entry = ZipEntry(name)
        zip.putNextEntry(entry)
        source.inputStream().use { input ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                zip.write(buf, 0, n)
            }
        }
        zip.closeEntry()
    }

    // --- naming ---

    /**
     * Per-session intra-`files/` de-duplication. First occurrence keeps the
     * original name; subsequent collisions get `_2`, `_3`, ... before the
     * extension (or appended if no extension).
     *
     * Multi-dot names use last `.` as the extension separator, so
     * `archive.tar.gz` becomes `archive.tar_2.gz` on collision (simple,
     * predictable; v1.3 may revisit).
     */
    internal fun nextUniqueName(seen: MutableMap<String, Int>, original: String): String {
        val count = (seen[original] ?: 0) + 1
        seen[original] = count
        if (count == 1) return original
        val dot = original.lastIndexOf('.')
        return if (dot <= 0) {
            "${original}_$count"
        } else {
            val base = original.substring(0, dot)
            val ext = original.substring(dot + 1)
            "${base}_$count.$ext"
        }
    }

    /**
     * Build a filesystem-safe directory segment from a session name.
     * - Replaces `:` with `·` (U+00B7) to keep visual separation.
     * - Replaces other invalid chars (`/ \ * ? " < > | \n \r`) with `_`.
     * - Truncates to 40 chars.
     */
    internal fun safeName(name: String): String {
        val sb = StringBuilder(name.length)
        for (c in name) {
            when {
                c == ':' -> sb.append('·')
                INVALID_NAME_CHARS.contains(c) -> sb.append('_')
                else -> sb.append(c)
            }
        }
        return sb.toString().take(SAFE_NAME_MAX)
    }
}
