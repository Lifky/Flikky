package com.example.flikky.ui.files

import com.example.flikky.data.db.FileOverviewRow
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
    fun `categoryOf maps mime prefixes and falls back to OTHER`() {
        assertEquals(FileCategory.IMAGE, FilesListBuilder.categoryOf("image/png"))
        assertEquals(FileCategory.VIDEO, FilesListBuilder.categoryOf("video/mp4"))
        assertEquals(FileCategory.AUDIO, FilesListBuilder.categoryOf("audio/mpeg"))
        assertEquals(FileCategory.DOCUMENT, FilesListBuilder.categoryOf("application/pdf"))
        assertEquals(FileCategory.DOCUMENT, FilesListBuilder.categoryOf("text/plain"))
        assertEquals(FileCategory.OTHER, FilesListBuilder.categoryOf("application/zip"))
        assertEquals(FileCategory.OTHER, FilesListBuilder.categoryOf(null))
        assertEquals(FileCategory.OTHER, FilesListBuilder.categoryOf(""))
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
            FilesListBuilder.build(rows, FileCategory.IMAGE, "", FileSort.TIME)
                .map { it.messageId },
        )
        assertEquals(
            listOf(2L, 3L),
            FilesListBuilder.build(rows, FileCategory.IMAGE, "", FileSort.SIZE)
                .map { it.messageId },
        )
        assertEquals(
            listOf(1L),
            FilesListBuilder.build(rows, FileCategory.ALL, "report", FileSort.TIME)
                .map { it.messageId },
        )
        assertEquals(
            emptyList<Long>(),
            FilesListBuilder.build(rows, FileCategory.VIDEO, "", FileSort.TIME)
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

        val out = FilesListBuilder.build(rows, FileCategory.IMAGE, "cat", FileSort.TIME)

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
