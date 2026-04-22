package com.example.flikky.service

import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.MessageExport
import com.example.flikky.export.SessionExport
import com.example.flikky.session.Origin
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportNotificationTextTest {

    @Test
    fun `empty snapshot reads 0 sessions 0 MB`() {
        val snap = ExportSnapshot(sessions = emptyList(), exportedAt = 0L)
        assertEquals("0 个会话 / 0 MB 可下载", ExportNotificationText.body(snap))
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
        assertEquals("1 个会话 / 0 MB 可下载", ExportNotificationText.body(snap))
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
        assertEquals("2 个会话 / 5 MB 可下载", ExportNotificationText.body(snap))
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
        assertEquals("1 个会话 / 500 KB 可下载", ExportNotificationText.body(snap))
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
        assertEquals("1 个会话 / 1.6 MB 可下载", ExportNotificationText.body(snap))
    }

    @Test
    fun `title constant is stable`() {
        assertEquals("Flikky 正在提供导出", ExportNotificationText.TITLE)
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
