package com.example.flikky.service

import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.SessionExport
import com.example.flikky.export.ExportScope
import com.example.flikky.export.FavoriteExport
import com.example.flikky.session.Origin
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportNotificationTextTest {

    @Test
    fun `empty snapshot summarizes 0 sessions and 0 MB`() {
        val snap = ExportSnapshot(sessions = emptyList(), exportedAt = 0L)
        assertEquals(
            ExportNotificationText.Summary(ExportScope.SESSIONS, 0, "0 MB"),
            ExportNotificationText.summary(snap),
        )
    }

    @Test
    fun `single session with no files still counts`() {
        val snap = ExportSnapshot(
            exportedAt = 0L,
            sessions = listOf(
                session(
                    id = 1,
                    messages = listOf(text("hi", origin = Origin.PHONE)),
                ),
            ),
        )
        assertEquals(1, ExportNotificationText.summary(snap).itemCount)
        assertEquals("0 MB", ExportNotificationText.summary(snap).formattedBytes)
    }

    @Test
    fun `two sessions aggregate file bytes and display MB`() {
        val oneMb = 1L * 1024 * 1024
        val snap = ExportSnapshot(
            exportedAt = 0L,
            sessions = listOf(
                session(
                    id = 1,
                    messages = listOf(file(sizeBytes = oneMb * 2)),
                ),
                session(
                    id = 2,
                    messages = listOf(
                        file(sizeBytes = oneMb * 3),
                        text("note"),
                    ),
                ),
            ),
        )
        assertEquals(2, ExportNotificationText.summary(snap).itemCount)
        assertEquals("5 MB", ExportNotificationText.summary(snap).formattedBytes)
    }

    @Test
    fun `sub-MB totals are shown in KB`() {
        val snap = ExportSnapshot(
            exportedAt = 0L,
            sessions = listOf(
                session(id = 1, messages = listOf(file(sizeBytes = 500L * 1024))),
            ),
        )
        // 500 KB / 1024 = 0.488 MB → shown as "500 KB"
        assertEquals("500 KB", ExportNotificationText.summary(snap).formattedBytes)
    }

    @Test
    fun `fractional MB rounds to one decimal`() {
        val snap = ExportSnapshot(
            exportedAt = 0L,
            sessions = listOf(
                session(id = 1, messages = listOf(file(sizeBytes = 1_700_000))),
            ),
        )
        // 1_700_000 bytes ≈ 1.62 MB → "1.6 MB"
        assertEquals("1.6 MB", ExportNotificationText.summary(snap).formattedBytes)
    }

    @Test
    fun `favorites snapshot describes favorite count and bytes`() {
        val snap = ExportSnapshot(
            exportedAt = 0L,
            scope = ExportScope.FAVORITES,
            favorites = listOf(
                FavoriteExport(
                    id = 1L,
                    sourceSessionId = 2L,
                    sourceMessageId = 3L,
                    kind = "FILE",
                    fileId = "x",
                    fileName = "x.bin",
                    fileSize = 2_048L,
                    fileMime = "application/octet-stream",
                    createdAt = 4L,
                )
            ),
        )
        assertEquals(
            ExportNotificationText.Summary(ExportScope.FAVORITES, 1, "2 KB"),
            ExportNotificationText.summary(snap),
        )
    }

    // ---- helpers ----

    private fun session(id: Long, messages: List<MessageExport>) = SessionExport(
        id = id,
        name = "session-$id",
        startedAt = 0L,
        endedAt = null,
        pinned = false,
        messages = messages,
    )

    private fun text(content: String, origin: Origin = Origin.PHONE) =
        MessageExport.Text(ts = 0L, origin = origin, content = content)

    private fun file(sizeBytes: Long, origin: Origin = Origin.PHONE) = MessageExport.File(
        ts = 0L,
        origin = origin,
        fileId = "f-$sizeBytes",
        name = "file-$sizeBytes.bin",
        mime = "application/octet-stream",
        sizeBytes = sizeBytes,
    )
}
