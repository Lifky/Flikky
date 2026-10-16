package com.example.flikky.export

import com.example.flikky.session.Origin
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SessionDevicesArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `devices and sender devices survive a format and parse round trip`() {
        val session = SessionExport(
            id = 1, name = "s", startedAt = 1L, endedAt = 2L, pinned = false,
            messages = listOf(MessageExport.Text(ts = 1L, origin = Origin.BROWSER, content = "hi", senderDeviceId = "dev-1")),
            devices = listOf(SessionDeviceExport(deviceId = "dev-1", name = "张三的笔记本", defaultLabel = "Chrome · Windows", avatarKey = "icon:star", firstSeenAt = 1L)),
        )
        val zip = File(tmp.root, "a.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("sessions/1/messages.json"))
            out.write(MessagesJsonFormatter.format(session).toByteArray())
            out.closeEntry()
        }
        val parsed = ZipFile(zip).use { ZipImporter.parse(it) }.single()
        assertEquals("张三的笔记本", parsed.devices.single().name)
        assertEquals("dev-1", (parsed.messages.single() as ParsedMessage.Text).senderDeviceId)
    }

    @Test fun `an archive written before v1_23 parses with no devices`() {
        val zip = File(tmp.root, "old.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("sessions/1/messages.json"))
            out.write("""{"sessionId":1,"name":"s","startedAt":1,"endedAt":2,"pinned":false,"messages":[{"type":"text","ts":1,"origin":"BROWSER","content":"hi"}]}""".toByteArray())
            out.closeEntry()
        }
        val parsed = ZipFile(zip).use { ZipImporter.parse(it) }.single()
        assertEquals(emptyList<SessionDeviceExport>(), parsed.devices)
        assertEquals(null, (parsed.messages.single() as ParsedMessage.Text).senderDeviceId)
    }
}
