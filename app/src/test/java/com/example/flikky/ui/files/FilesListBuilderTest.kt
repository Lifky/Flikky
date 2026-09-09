package com.example.flikky.ui.files

import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesListBuilderTest {
    private fun row(
        id: Long,
        name: String,
        mime: String?,
        size: Long,
        timestamp: Long,
    ) = FileOverviewRow(
        messageId = id,
        sessionId = 1L,
        sessionName = "s",
        sessionEndedAt = 9L,
        origin = "BROWSER",
        fileId = "f$id",
        fileName = name,
        fileSize = size,
        fileMime = mime,
        timestamp = timestamp,
    )

    @Test
    fun `sorts by name using the shared comparator, ascending and descending`() {
        val rows = listOf(
            row(1, "beta.txt", "text/plain", 1, 1),
            row(2, "Alpha.txt", "text/plain", 1, 2),
            row(3, "alpha.txt", "text/plain", 1, 3),
        )

        val asc = FilesListBuilder.build(
            rows, FileCategory.ALL, "", SortSpec(SortKey.NAME, descending = false),
        )
        assertEquals(listOf("Alpha.txt", "alpha.txt", "beta.txt"), asc.map { it.fileName })

        val desc = FilesListBuilder.build(
            rows, FileCategory.ALL, "", SortSpec(SortKey.NAME, descending = true),
        )
        assertEquals(listOf("beta.txt", "alpha.txt", "Alpha.txt"), desc.map { it.fileName })
    }

    @Test
    fun `direction is honoured for time and size, not just the key`() {
        // 本版之前两个键都写死降序。加了方向之后必须两个方向都真的生效。
        val rows = listOf(
            row(1, "a", "text/plain", 10, 100),
            row(2, "b", "text/plain", 20, 200),
        )

        assertEquals(
            listOf("a", "b"),
            FilesListBuilder.build(
                rows, FileCategory.ALL, "", SortSpec(SortKey.TIME, descending = false),
            ).map { it.fileName },
        )
        assertEquals(
            listOf("b", "a"),
            FilesListBuilder.build(
                rows, FileCategory.ALL, "", SortSpec(SortKey.TIME, descending = true),
            ).map { it.fileName },
        )
        assertEquals(
            listOf("a", "b"),
            FilesListBuilder.build(
                rows, FileCategory.ALL, "", SortSpec(SortKey.SIZE, descending = false),
            ).map { it.fileName },
        )
    }

    @Test
    fun `rows with an equal key keep a deterministic order via the name tie-break`() {
        // 同大小 / 同时间时若不兜底，顺序就跟着 DAO 的返回顺序变。
        val rows = listOf(
            row(1, "zeta", "text/plain", 5, 100),
            row(2, "alpha", "text/plain", 5, 100),
        )
        assertEquals(
            listOf("alpha", "zeta"),
            FilesListBuilder.build(
                rows, FileCategory.ALL, "", SortSpec(SortKey.SIZE, descending = true),
            ).map { it.fileName },
        )
    }

    @Test
    fun `categoryOf maps mime prefixes and falls back to OTHER`() {
        assertEquals(FileCategory.IMAGE, FilesListBuilder.categoryOf("image/png"))
        assertEquals(FileCategory.VIDEO, FilesListBuilder.categoryOf("video/mp4"))
        assertEquals(FileCategory.AUDIO, FilesListBuilder.categoryOf("audio/mpeg"))
        assertEquals(FileCategory.DOCUMENT, FilesListBuilder.categoryOf("application/pdf"))
        assertEquals(FileCategory.DOCUMENT, FilesListBuilder.categoryOf("text/plain"))
        assertEquals(FileCategory.ARCHIVE, FilesListBuilder.categoryOf("application/zip"))
        assertEquals(FileCategory.ARCHIVE, FilesListBuilder.categoryOf("application/vnd.rar"))
        assertEquals(FileCategory.ARCHIVE, FilesListBuilder.categoryOf("application/x-7z-compressed"))
        assertEquals(FileCategory.ARCHIVE, FilesListBuilder.categoryOf("application/x-tar"))
        assertEquals(FileCategory.ARCHIVE, FilesListBuilder.categoryOf("application/gzip"))
        assertEquals(FileCategory.OTHER, FilesListBuilder.categoryOf("application/octet-stream"))
        assertEquals(FileCategory.OTHER, FilesListBuilder.categoryOf(null))
        assertEquals(FileCategory.OTHER, FilesListBuilder.categoryOf(""))
    }

    @Test
    fun `categoryOf treats svg as OTHER despite image mime prefix`() {
        // Android 系统不把 SVG 当媒体：BitmapFactory 解不出、相册也不收录，
        // 归 IMAGE 会打开黑屏预览和假成功的存相册入口。
        assertEquals(FileCategory.OTHER, FilesListBuilder.categoryOf("image/svg+xml"))
        assertFalse(FilesListBuilder.isMedia("image/svg+xml"))
    }

    @Test
    fun `isMedia is true only for image and video`() {
        assertTrue(FilesListBuilder.isMedia("image/jpeg"))
        assertTrue(FilesListBuilder.isMedia("video/mp4"))
        assertFalse(FilesListBuilder.isMedia("application/pdf"))
        assertFalse(FilesListBuilder.isMedia("audio/mpeg"))
        assertFalse(FilesListBuilder.isMedia(null))
        assertFalse(FilesListBuilder.isMedia(""))
    }

    @Test
    fun `build filters category and name then sorts visible rows`() {
        val rows = listOf(
            row(1, "Report.PDF", "application/pdf", size = 10, timestamp = 1),
            row(2, "cat.png", "image/png", size = 30, timestamp = 2),
            row(3, "dog.png", "image/png", size = 20, timestamp = 3),
        )

        assertEquals(
            listOf(3L, 2L),
            FilesListBuilder.build(rows, FileCategory.IMAGE, "", SortSpec(SortKey.TIME, descending = true))
                .map { it.messageId },
        )
        assertEquals(
            listOf(2L, 3L),
            FilesListBuilder.build(rows, FileCategory.IMAGE, "", SortSpec(SortKey.SIZE, descending = true))
                .map { it.messageId },
        )
        assertEquals(
            listOf(1L),
            FilesListBuilder.build(rows, FileCategory.ALL, "report", SortSpec(SortKey.TIME, descending = true))
                .map { it.messageId },
        )
        assertEquals(
            emptyList<Long>(),
            FilesListBuilder.build(rows, FileCategory.VIDEO, "", SortSpec(SortKey.TIME, descending = true))
                .map { it.messageId },
        )
    }

    @Test
    fun `build combines category and query for quick sheet`() {
        val rows = listOf(
            row(1, "cat.jpg", "image/jpeg", size = 10, timestamp = 1),
            row(2, "cat.mp4", "video/mp4", size = 20, timestamp = 2),
            row(3, "dog.jpg", "image/jpeg", size = 30, timestamp = 3),
        )

        val out = FilesListBuilder.build(rows, FileCategory.IMAGE, "cat", SortSpec(SortKey.TIME, descending = true))

        assertEquals(listOf(1L), out.map { it.messageId })
    }

    @Test
    fun `stats sums visible rows treating null size as zero`() {
        val rows = listOf(
            row(1, "a", null, size = 10, timestamp = 1),
            row(2, "b", null, size = 0, timestamp = 2).copy(fileSize = null),
        )

        assertEquals(FileStats(count = 2, totalBytes = 10L), FilesListBuilder.stats(rows))
    }
}
