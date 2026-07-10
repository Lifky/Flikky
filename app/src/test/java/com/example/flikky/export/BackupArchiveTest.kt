package com.example.flikky.export

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `all archive roundtrips favorites settings and favorite files`() {
        val favoriteFile = tmp.newFile("favorite-source").apply { writeText("favorite payload") }
        val snapshot = ExportSnapshot(
            sessions = emptyList(),
            exportedAt = 1_700_000_000_000L,
            scope = ExportScope.ALL,
            favoriteGroups = listOf(
                FavoriteGroupExport(id = 7L, name = "Work", sortOrder = 0, createdAt = 10L),
            ),
            favorites = listOf(
                FavoriteExport(
                    id = 11L,
                    sourceSessionId = 21L,
                    sourceMessageId = 22L,
                    kind = "TEXT",
                    textContent = "remember",
                    groupId = 7L,
                    createdAt = 30L,
                    sourceSessionName = "Session",
                    origin = "PHONE",
                ),
                FavoriteExport(
                    id = 12L,
                    sourceSessionId = 21L,
                    sourceMessageId = 23L,
                    kind = "FILE",
                    fileId = "depot-id",
                    fileName = "report.txt",
                    fileSize = favoriteFile.length(),
                    fileMime = "text/plain",
                    groupId = 7L,
                    createdAt = 31L,
                    sourceSessionName = "Session",
                    origin = "BROWSER",
                ),
            ),
            settings = SettingsExport(
                themeMode = "PRESET",
                presetTheme = "ANAN_BLUE",
                contrastLevel = "HIGH",
                darkMode = "DARK",
                deviceName = "Backup phone",
                requirePin = false,
                historyRetainLimit = -1,
            ),
        )

        val bytes = ByteArrayOutputStream().also { out ->
            ZipExporter.write(
                out = out,
                snapshot = snapshot,
                fileResolver = { _, _ -> null },
                favoriteFileResolver = { fileId ->
                    favoriteFile.takeIf { fileId == "depot-id" }
                },
            )
        }.toByteArray()

        val zipPath = tmp.newFile("backup.zip").apply { writeBytes(bytes) }
        ZipFile(zipPath).use { zip ->
            assertNotNull(zip.getEntry("manifest.json"))
            assertNotNull(zip.getEntry("favorites/favorites.json"))
            assertNotNull(zip.getEntry("settings/settings.json"))
            val readme = zip.getInputStream(zip.getEntry("README.txt")).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            assertEquals(true, readme.contains("Total bytes: ${favoriteFile.length()}"))

            val parsed = ZipImporter.parseBackup(zip)
            assertEquals(ExportScope.ALL, parsed.scope)
            assertEquals("Work", parsed.favoriteGroups.single().name)
            assertEquals("remember", parsed.favorites.first().textContent)
            assertEquals("Backup phone", parsed.settings?.deviceName)

            val entry = ZipImporter.resolveFavoriteFileEntry(parsed.favorites[1], zip)
            assertNotNull(entry)
            assertArrayEquals(
                "favorite payload".toByteArray(),
                zip.getInputStream(entry).use { it.readBytes() },
            )
        }
    }

    @Test
    fun `favorite file resolver rejects traversal path from archive json`() {
        val malicious = FavoriteExport(
            id = 1L,
            sourceSessionId = 1L,
            sourceMessageId = 2L,
            kind = "FILE",
            fileId = "x",
            fileName = "x.txt",
            fileSize = 1L,
            fileMime = "text/plain",
            createdAt = 1L,
            relativePath = "../outside.txt",
        )
        val zipFile = tmp.newFile("empty.zip")
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { it.finish() }

        ZipFile(zipFile).use { zip ->
            assertEquals(null, ZipImporter.resolveFavoriteFileEntry(malicious, zip))
        }
    }

    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `parse backup reports malformed canonical json`() {
        val zipFile = tmp.newFile("malformed.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("settings/settings.json"))
            zip.write("not-json".toByteArray())
            zip.closeEntry()
        }

        ZipFile(zipFile).use(ZipImporter::parseBackup)
    }
}
