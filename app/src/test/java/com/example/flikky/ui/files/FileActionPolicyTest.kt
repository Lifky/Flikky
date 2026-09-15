package com.example.flikky.ui.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileActionPolicyTest {

    private fun actions(entries: List<RowMenuEntry>) = entries.map { it.action }

    @Test
    fun `rowMenu media file in ended session shows all six enabled`() {
        val menu = FileActionPolicy.rowMenu(
            mime = "image/png",
            sessionEnded = true,
            favoritesEnabled = true,
        )
        assertEquals(
            listOf(
                RowAction.FAVORITE, RowAction.SHARE, RowAction.GALLERY,
                RowAction.SAVE_AS, RowAction.OPEN_IN_SESSION, RowAction.DELETE,
            ),
            actions(menu),
        )
        assertTrue(menu.all { it.enabled })
    }

    @Test
    fun `rowMenu non media hides gallery entirely`() {
        val menu = FileActionPolicy.rowMenu(
            mime = "application/pdf",
            sessionEnded = true,
            favoritesEnabled = true,
        )
        assertEquals(
            listOf(
                RowAction.FAVORITE, RowAction.SHARE,
                RowAction.SAVE_AS, RowAction.OPEN_IN_SESSION, RowAction.DELETE,
            ),
            actions(menu),
        )
    }

    @Test
    fun `rowMenu hides favorite when favorites feature is disabled`() {
        val menu = FileActionPolicy.rowMenu(
            mime = "image/png",
            sessionEnded = true,
            favoritesEnabled = false,
        )
        assertEquals(
            listOf(
                RowAction.SHARE, RowAction.GALLERY,
                RowAction.SAVE_AS, RowAction.OPEN_IN_SESSION, RowAction.DELETE,
            ),
            actions(menu),
        )
    }

    @Test
    fun `rowMenu active session disables open-in-session and delete but keeps them visible`() {
        val menu = FileActionPolicy.rowMenu(
            mime = "video/mp4",
            sessionEnded = false,
            favoritesEnabled = true,
        )
        val byAction = menu.associateBy { it.action }
        assertFalse(byAction.getValue(RowAction.OPEN_IN_SESSION).enabled)
        assertFalse(byAction.getValue(RowAction.DELETE).enabled)
        assertTrue(byAction.getValue(RowAction.FAVORITE).enabled)
        assertTrue(byAction.getValue(RowAction.SHARE).enabled)
        assertTrue(byAction.getValue(RowAction.GALLERY).enabled)
        assertTrue(byAction.getValue(RowAction.SAVE_AS).enabled)
    }

    @Test
    fun `batchShareMime uses shared major type wildcard`() {
        assertEquals("image/*", FileActionPolicy.batchShareMime(listOf("image/png", "image/jpeg")))
        assertEquals("application/*", FileActionPolicy.batchShareMime(listOf("application/pdf")))
    }

    @Test
    fun `batchShareMime falls back to star-star on mixed null blank or empty`() {
        assertEquals("*/*", FileActionPolicy.batchShareMime(listOf("image/png", "video/mp4")))
        assertEquals("*/*", FileActionPolicy.batchShareMime(listOf("image/png", null)))
        assertEquals("*/*", FileActionPolicy.batchShareMime(listOf("image/png", "")))
        assertEquals("*/*", FileActionPolicy.batchShareMime(emptyList()))
    }
    @Test fun `apk rows offer install as their first action`() { val m=FileActionPolicy.rowMenu("application/vnd.android.package-archive",true,false); assertEquals(RowAction.INSTALL,m.first().action); assertTrue(m.first().enabled) }
    @Test fun `non apk rows never offer install`() { listOf("image/jpeg","video/mp4","application/pdf","application/zip",null).forEach { assertTrue(FileActionPolicy.rowMenu(it,true,false).none { e -> e.action==RowAction.INSTALL }) } }
    @Test fun `apk rows do not offer save to gallery`() { assertTrue(FileActionPolicy.rowMenu("application/vnd.android.package-archive",true,true).none { it.action==RowAction.GALLERY }) }
    @Test fun `install stays available while the session is still running`() { assertTrue(FileActionPolicy.rowMenu("application/vnd.android.package-archive",false,false).single { it.action==RowAction.INSTALL }.enabled) }
    @Test fun `install checks unknown source permission before launching`() {
        val f=File("src/main/java/com/example/flikky/ui/components/FileIntents.kt").takeIf{it.isFile}?:File("app/src/main/java/com/example/flikky/ui/components/FileIntents.kt")
        val block=f.readText().substringAfter("fun installApk").substringBefore("fun saveToGallery")
        assertTrue(block.contains("canRequestPackageInstalls"))
        val bad="fun installApk() { startActivity(intent) }"; assertFalse(bad.contains("canRequestPackageInstalls"))
    }
}
