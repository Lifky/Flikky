package com.example.flikky.ui.files

import com.example.flikky.R
import com.example.flikky.util.LeadingVisualCatalog
import com.example.flikky.util.MimeGuess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCategoryApkTest {
    @Test fun `apk mime lands in the apk category`() { assertEquals(FileCategory.APK, FilesListBuilder.categoryOf(MimeGuess.APK_MIME)) }
    @Test fun `apk is not treated as media`() { assertFalse(FilesListBuilder.isMedia(MimeGuess.APK_MIME)) }
    @Test fun `apk category has its own label and the official symbol`() {
        assertEquals(R.string.files_filter_apk, FileCategory.APK.labelResource()); assertEquals(R.drawable.ic_apk_document, FileCategory.APK.iconResource())
    }
    @Test fun `apk category maps onto a leading type`() {
        val type = FileCategory.APK.leadingType(); assertNotNull(type); assertEquals("apk", type!!.id); assertEquals("apk_document", type.symbol)
    }
    @Test fun `apk sits between archive and other in the catalogue`() {
        val ids = LeadingVisualCatalog.types.map { it.id }; assertEquals("other", ids.last()); assertEquals(ids.indexOf("archive") + 1, ids.indexOf("apk"))
    }
    @Test fun `apk category sits between archive and other`() {
        val categories = FileCategory.entries
        assertEquals(categories.indexOf(FileCategory.ARCHIVE) + 1, categories.indexOf(FileCategory.APK))
        assertEquals(categories.indexOf(FileCategory.APK) + 1, categories.indexOf(FileCategory.OTHER))
    }
    @Test fun `apk hue does not collide with the five existing semantic hues`() {
        val apk = LeadingVisualCatalog.types.single { it.id == "apk" }; assertEquals(324, apk.hueShift)
        assertEquals(listOf(0, 72, 144, 216, 288), LeadingVisualCatalog.types.filterNot { it.id == "apk" || it.id == "other" }.map { it.hueShift })
    }
    @Test fun `every category except ALL still resolves to a leading type`() {
        FileCategory.entries.filterNot { it == FileCategory.ALL }.forEach { assertTrue("${it.name} missing", it.leadingType() != null) }
    }
}
