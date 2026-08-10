package com.example.flikky.ui.files

import com.example.flikky.data.db.FileOverviewRow

enum class FileCategory { ALL, IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }

enum class FileSort { TIME, SIZE }

data class FileStats(
    val count: Int,
    val totalBytes: Long,
)

/** Pure list shaping for the cross-session files overview. */
object FilesListBuilder {
    private val documentMimes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )

    fun categoryOf(mime: String?): FileCategory {
        val normalized = mime.orEmpty()
        return when {
            // SVG 系统层面不算媒体（BitmapFactory 解不出、相册不收录），归 OTHER。
            normalized == "image/svg+xml" -> FileCategory.OTHER
            normalized.startsWith("image/") -> FileCategory.IMAGE
            normalized.startsWith("video/") -> FileCategory.VIDEO
            normalized.startsWith("audio/") -> FileCategory.AUDIO
            normalized.startsWith("text/") || normalized in documentMimes -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    /** Shared thumbnail and gallery predicate. */
    fun isMedia(mime: String?): Boolean =
        categoryOf(mime).let { it == FileCategory.IMAGE || it == FileCategory.VIDEO }

    fun build(
        rows: List<FileOverviewRow>,
        category: FileCategory,
        query: String,
        sort: FileSort,
    ): List<FileOverviewRow> {
        val normalizedQuery = query.trim()
        val filtered = rows.filter { row ->
            (category == FileCategory.ALL || categoryOf(row.fileMime) == category) &&
                (normalizedQuery.isEmpty() ||
                    row.fileName.orEmpty().contains(normalizedQuery, ignoreCase = true))
        }
        return when (sort) {
            FileSort.TIME -> filtered.sortedByDescending { it.timestamp }
            FileSort.SIZE -> filtered.sortedByDescending { it.fileSize ?: 0L }
        }
    }

    fun stats(rows: List<FileOverviewRow>): FileStats = FileStats(
        count = rows.size,
        totalBytes = rows.sumOf { it.fileSize ?: 0L },
    )
}
