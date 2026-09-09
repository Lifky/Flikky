package com.example.flikky.ui.files

import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.util.NAME_ORDER
import com.example.flikky.util.LeadingVisualCatalog
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec

enum class FileCategory { ALL, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, OTHER }

data class FileStats(
    val count: Int,
    val totalBytes: Long,
)

/** Pure list shaping for the cross-session files overview. */
object FilesListBuilder {
    fun categoryOf(mime: String?): FileCategory {
        val type = LeadingVisualCatalog.typeOf(mime)
        return FileCategory.entries.firstOrNull { it.leadingType()?.id == type.id }
            ?: FileCategory.OTHER
    }

    /** Shared thumbnail and gallery predicate. */
    fun isMedia(mime: String?): Boolean =
        categoryOf(mime).let { it == FileCategory.IMAGE || it == FileCategory.VIDEO }

    fun build(
        rows: List<FileOverviewRow>,
        category: FileCategory,
        query: String,
        sort: SortSpec,
    ): List<FileOverviewRow> {
        val normalizedQuery = query.trim()
        val filtered = rows.filter { row ->
            (category == FileCategory.ALL || categoryOf(row.fileMime) == category) &&
                (normalizedQuery.isEmpty() ||
                    row.fileName.orEmpty().contains(normalizedQuery, ignoreCase = true))
        }
        val byKey: Comparator<FileOverviewRow> = when (sort.key) {
            SortKey.NAME -> compareBy(NAME_ORDER) { it.fileName.orEmpty() }
            SortKey.TIME -> compareBy { it.timestamp }
            SortKey.SIZE -> compareBy { it.fileSize ?: 0L }
        }
        val directed = if (sort.descending) byKey.reversed() else byKey
        // 名称兜底与 StorageListingPolicy 一致：同大小 / 同时间的行顺序仍然确定，
        // 否则它跟着 DAO 的返回顺序变。
        return filtered.sortedWith(directed.thenBy(NAME_ORDER) { it.fileName.orEmpty() })
    }

    fun stats(rows: List<FileOverviewRow>): FileStats = FileStats(
        count = rows.size,
        totalBytes = rows.sumOf { it.fileSize ?: 0L },
    )
}
