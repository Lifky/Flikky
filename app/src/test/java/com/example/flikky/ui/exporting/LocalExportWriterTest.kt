package com.example.flikky.ui.exporting

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.export.ExportScope
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.SettingsExport
import com.example.flikky.export.ZipImporter
import java.util.zip.ZipFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocalExportWriterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun write_creates_roundtrip_zip_at_document_uri() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = tmp.newFile("settings.zip")
        val snapshot = ExportSnapshot(
            exportedAt = 123L,
            scope = ExportScope.SETTINGS,
            settings = SettingsExport(deviceName = "Local backup"),
        )

        LocalExportWriter.write(
            context = context,
            uri = Uri.fromFile(target),
            snapshot = snapshot,
            sessionFileResolver = { _, _ -> null },
            favoriteFileResolver = { null },
        )

        ZipFile(target).use { zip ->
            assertNotNull(zip.getEntry("manifest.json"))
            val parsed = ZipImporter.parseBackup(zip)
            assertEquals(ExportScope.SETTINGS, parsed.scope)
            assertEquals("Local backup", parsed.settings?.deviceName)
        }
    }
}
