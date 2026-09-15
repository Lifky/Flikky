package com.example.flikky.ui.files

import com.example.flikky.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileCategoryUiTest {
    @Test
    fun audioCategoryUsesAudioFileSymbol() {
        val resourceName = R.drawable::class.java.fields
            .single { it.getInt(null) == FileCategory.AUDIO.iconResource() }
            .name

        assertEquals("ic_audio_file", resourceName)
    }

    @Test
    fun everyCategoryHasALabelAndIcon() {
        FileCategory.entries.forEach { category ->
            assertTrue("${category.name} has no label", category.labelResource() != 0)
            assertTrue("${category.name} has no icon", category.iconResource() != 0)
        }
    }

    @Test
    fun archiveCategoryUsesFolderZipInsteadOfDraft() {
        assertNotEquals(R.drawable.ic_draft, FileCategory.ARCHIVE.iconResource())
        val resourceName = R.drawable::class.java.fields
            .single { it.getInt(null) == FileCategory.ARCHIVE.iconResource() }
            .name

        assertEquals("ic_folder_zip", resourceName)
    }

    @Test
    fun categoryMapsOneToOneToLeadingTypeExceptAll() {
        assertNull(FileCategory.ALL.leadingType())
        assertEquals("image", FileCategory.IMAGE.leadingType()?.id)
        assertEquals("video", FileCategory.VIDEO.leadingType()?.id)
        assertEquals("audio", FileCategory.AUDIO.leadingType()?.id)
        assertEquals("document", FileCategory.DOCUMENT.leadingType()?.id)
        assertEquals("archive", FileCategory.ARCHIVE.leadingType()?.id)
        assertEquals("apk", FileCategory.APK.leadingType()?.id)
        assertEquals("other", FileCategory.OTHER.leadingType()?.id)
    }

    @Test
    fun fileOverviewRendersOneChipPerCategory() {
        assertEquals(8, FileCategory.entries.size)
        val source = stripSourceNoise(sourceFile("ui/files/FilesScreen.kt").readText())
        assertTrue(Regex("""items\s*\(\s*FileCategory\.entries\s*,""").containsMatchIn(source))
    }

    private fun sourceFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            val fromModule = File(dir, "src/main/java/com/example/flikky/$relative")
            if (fromModule.isFile) return fromModule
            val fromRoot = File(dir, "app/src/main/java/com/example/flikky/$relative")
            if (fromRoot.isFile) return fromRoot
            dir = dir.parentFile
        }
        error("cannot locate $relative")
    }

    private fun stripSourceNoise(source: String): String = source
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("//.*"), "")
        .lineSequence()
        .filterNot { it.trimStart().startsWith("import ") }
        .joinToString("\n")
}
