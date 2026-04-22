package com.example.flikky.export

import com.example.flikky.session.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MessagesTextFormatterTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    /** Parses "yyyy-MM-dd HH:mm:ss" in UTC to epoch millis (test helper). */
    private fun ts(literal: String): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = utc
        }
        return fmt.parse(literal)!!.time
    }

    @Test
    fun `empty session emits only header block`() {
        val session = SessionExport(
            id = 7,
            name = "empty one",
            startedAt = ts("2026-04-22 14:30:15"),
            endedAt = ts("2026-04-22 15:02:18"),
            pinned = false,
            messages = emptyList(),
        )

        val out = MessagesTextFormatter.format(session, utc)

        val expected = """
            # 会话名: empty one
            # sessionId: 7
            # 起止: 2026-04-22 14:30:15 ~ 2026-04-22 15:02:18
            # 消息: 0 条, 文件: 0 个

        """.trimIndent() + "\n"
        assertEquals(expected, out)
    }

    @Test
    fun `mixed text and file messages render both line shapes`() {
        val session = SessionExport(
            id = 12,
            name = "04-22 14:30 与小明",
            startedAt = ts("2026-04-22 14:30:15"),
            endedAt = ts("2026-04-22 15:02:18"),
            pinned = false,
            messages = listOf(
                MessageExport.Text(
                    ts = ts("2026-04-22 14:30:15"),
                    origin = Origin.PHONE,
                    content = "hello world",
                ),
                MessageExport.Text(
                    ts = ts("2026-04-22 14:31:02"),
                    origin = Origin.BROWSER,
                    content = "收到，我看看",
                ),
                MessageExport.File(
                    ts = ts("2026-04-22 14:35:40"),
                    origin = Origin.BROWSER,
                    fileId = "f1",
                    name = "doc.pdf",
                    mime = "application/pdf",
                    sizeBytes = 1_258_291L, // 1.2 MB
                ),
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        val expected = """
            # 会话名: 04-22 14:30 与小明
            # sessionId: 12
            # 起止: 2026-04-22 14:30:15 ~ 2026-04-22 15:02:18
            # 消息: 3 条, 文件: 1 个

            [2026-04-22 14:30:15] [PHONE  ] hello world
            [2026-04-22 14:31:02] [BROWSER] 收到，我看看
            [2026-04-22 14:35:40] [BROWSER] [文件] doc.pdf (1.2 MB)
        """.trimIndent() + "\n"
        assertEquals(expected, out)
    }

    @Test
    fun `PHONE origin tag is padded to 7 chars to align with BROWSER`() {
        val session = oneMessageSession(
            MessageExport.Text(
                ts = ts("2026-04-22 10:00:00"),
                origin = Origin.PHONE,
                content = "x",
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue("PHONE tag must be padded", out.contains("[PHONE  ] x"))
        // Origin column must be the same width as "[BROWSER]" (9 chars).
        val line = out.lines().first { it.startsWith("[2026") }
        val afterTs = line.substringAfter("] ")
        val originCol = afterTs.take(9)
        assertEquals("[PHONE  ]", originCol)
    }

    @Test
    fun `BROWSER origin tag has no padding`() {
        val session = oneMessageSession(
            MessageExport.Text(
                ts = ts("2026-04-22 10:00:00"),
                origin = Origin.BROWSER,
                content = "y",
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue(out.contains("[BROWSER] y"))
    }

    @Test
    fun `null endedAt renders header as in-progress`() {
        val session = SessionExport(
            id = 1,
            name = "live",
            startedAt = ts("2026-04-22 14:30:15"),
            endedAt = null,
            pinned = false,
            messages = emptyList(),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue(
            "header should show 进行中 when endedAt is null, got:\n$out",
            out.contains("# 起止: 2026-04-22 14:30:15 ~ 进行中"),
        )
    }

    @Test
    fun `text content with newlines is preserved verbatim`() {
        val session = oneMessageSession(
            MessageExport.Text(
                ts = ts("2026-04-22 10:00:00"),
                origin = Origin.PHONE,
                content = "line1\nline2\nline3",
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue(
            "multi-line content should be kept as-is",
            out.contains("[PHONE  ] line1\nline2\nline3\n"),
        )
    }

    @Test
    fun `file size under 1 KB formats as bytes`() {
        val session = oneMessageSession(
            MessageExport.File(
                ts = ts("2026-04-22 10:00:00"),
                origin = Origin.PHONE,
                fileId = "a",
                name = "tiny.txt",
                mime = "text/plain",
                sizeBytes = 500L,
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue(out.contains("[文件] tiny.txt (500 B)"))
    }

    @Test
    fun `file size under 1 MB formats as KB`() {
        val session = oneMessageSession(
            MessageExport.File(
                ts = ts("2026-04-22 10:00:00"),
                origin = Origin.PHONE,
                fileId = "a",
                name = "mid.bin",
                mime = "application/octet-stream",
                sizeBytes = 500L * 1024L, // exactly 500 KB
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue(
            "500 KB file should render as KB, got:\n$out",
            out.contains("[文件] mid.bin (500.0 KB)"),
        )
    }

    @Test
    fun `file size at or above 1 MB formats as MB`() {
        val session = oneMessageSession(
            MessageExport.File(
                ts = ts("2026-04-22 10:00:00"),
                origin = Origin.PHONE,
                fileId = "a",
                name = "big.bin",
                mime = "application/octet-stream",
                sizeBytes = 5L * 1024L * 1024L, // exactly 5 MB
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue(
            "5 MB file should render as MB, got:\n$out",
            out.contains("[文件] big.bin (5.0 MB)"),
        )
    }

    @Test
    fun `messages render in input order even if timestamps are out of order`() {
        val later = ts("2026-04-22 10:00:05")
        val earlier = ts("2026-04-22 10:00:01")
        val session = SessionExport(
            id = 3,
            name = "unsorted",
            startedAt = ts("2026-04-22 09:00:00"),
            endedAt = ts("2026-04-22 11:00:00"),
            pinned = false,
            messages = listOf(
                MessageExport.Text(ts = later, origin = Origin.PHONE, content = "second in time"),
                MessageExport.Text(ts = earlier, origin = Origin.BROWSER, content = "first in time"),
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        val bodyLines = out.lines().filter { it.startsWith("[2026") }
        assertEquals(2, bodyLines.size)
        assertTrue(
            "first output line should come from the first input message",
            bodyLines[0].contains("second in time"),
        )
        assertTrue(
            "second output line should come from the second input message",
            bodyLines[1].contains("first in time"),
        )
    }

    @Test
    fun `statistics line counts all messages and only files`() {
        val session = SessionExport(
            id = 99,
            name = "stats",
            startedAt = ts("2026-04-22 00:00:00"),
            endedAt = ts("2026-04-22 01:00:00"),
            pinned = false,
            messages = listOf(
                MessageExport.Text(ts("2026-04-22 00:00:01"), Origin.PHONE, "a"),
                MessageExport.Text(ts("2026-04-22 00:00:02"), Origin.PHONE, "b"),
                MessageExport.File(
                    ts = ts("2026-04-22 00:00:03"),
                    origin = Origin.BROWSER,
                    fileId = "x",
                    name = "x.bin",
                    mime = "application/octet-stream",
                    sizeBytes = 10L,
                ),
                MessageExport.File(
                    ts = ts("2026-04-22 00:00:04"),
                    origin = Origin.BROWSER,
                    fileId = "y",
                    name = "y.bin",
                    mime = "application/octet-stream",
                    sizeBytes = 20L,
                ),
            ),
        )

        val out = MessagesTextFormatter.format(session, utc)

        assertTrue(
            "stats line should count 4 messages and 2 files, got:\n$out",
            out.contains("# 消息: 4 条, 文件: 2 个"),
        )
    }

    private fun oneMessageSession(msg: MessageExport): SessionExport = SessionExport(
        id = 1,
        name = "s",
        startedAt = ts("2026-04-22 10:00:00"),
        endedAt = ts("2026-04-22 10:00:10"),
        pinned = false,
        messages = listOf(msg),
    )
}
