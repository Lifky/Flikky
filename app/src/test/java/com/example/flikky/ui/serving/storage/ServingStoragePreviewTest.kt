package com.example.flikky.ui.serving.storage

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServingStoragePreviewTest {
    private fun projectFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            File(dir, relative).takeIf { it.isFile }?.let { return it }
            File(dir, "app/$relative").takeIf { it.isFile }?.let { return it }
            dir = dir.parentFile
        }
        error("cannot locate $relative")
    }

    private fun source(relative: String): String =
        projectFile("src/main/java/com/example/flikky/$relative").readText(Charsets.UTF_8)

    private fun scrub(value: String): String = value
        .replace(Regex("(?s)/\\*.*?\\*/"), "")
        .replace(Regex("(?m)^\\s*//.*$"), "")
        .replace(Regex("(?m)^\\s*import\\s+.*$"), "")

    private fun functionBody(value: String, signature: String): String {
        val start = value.indexOf(signature)
        if (start < 0) return ""
        val opening = value.indexOf('{', start)
        if (opening < 0) return ""
        var depth = 0
        for (index in opening until value.length) {
            when (value[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return value.substring(start, index + 1)
                }
            }
        }
        return ""
    }

    @Test
    fun `file leading click callback is optional for every existing caller`() {
        val leading = scrub(source("ui/components/FileLeadingVisual.kt"))
        val start = leading.indexOf("fun FileLeadingVisual(")
        val end = leading.indexOf(") {", start)
        assertTrue("sanity: FileLeadingVisual declaration is missing", start >= 0 && end > start)
        val declaration = leading.substring(start, end)
        assertTrue(
            "the new callback must default to null so untouched callers keep compiling: $declaration",
            Regex("onClick:\\s*\\(\\(\\)\\s*->\\s*Unit\\)\\?\\s*=\\s*null")
                .containsMatchIn(declaration),
        )
    }

    @Test
    fun `storage media leading gets preview while non-media leading stays inert`() {
        val tab = scrub(source("ui/serving/storage/ServingStorageTab.kt"))
        val row = functionBody(tab, "private fun StorageEntryRow(")
        assertTrue("sanity: StorageEntryRow source slice is missing", row.isNotEmpty())
        assertTrue("the row must receive the shared preview action", row.contains("onPreview: (LocalEntry) -> Unit"))
        val clickStart = row.indexOf("onClick = if (FilesListBuilder.isMedia(entry.mime))")
        val clickEnd = row.indexOf("\n            )", clickStart)
        assertTrue(
            "sanity: the media onClick argument source slice is missing",
            clickStart >= 0 && clickEnd > clickStart,
        )
        val clickArgument = row.substring(clickStart, clickEnd)
        assertTrue(
            "non-media leading must receive null: $clickArgument",
            Regex("else\\s*\\{\\s*null\\s*}").containsMatchIn(clickArgument),
        )
        assertEquals(
            "the callback must occur only in the media branch: $clickArgument",
            1,
            Regex("onPreview\\(entry\\)").findAll(clickArgument).count(),
        )
    }

    @Test
    fun `storage images reuse the one ImagePreviewDialog implementation`() {
        val dialogFile = projectFile("src/main/java/com/example/flikky/ui/components/ImagePreviewDialog.kt")
        val javaRoot = requireNotNull(requireNotNull(requireNotNull(dialogFile.parentFile).parentFile).parentFile)
        val definitions = javaRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { Regex("\\bfun\\s+ImagePreviewDialog\\s*\\(").findAll(scrub(it.readText())).count() }
        assertEquals("there must be one ImagePreviewDialog definition", 1, definitions)

        val tab = scrub(source("ui/serving/storage/ServingStorageTab.kt"))
        assertTrue("sanity: the shared dialog definition must be callable", tab.contains("ImagePreviewDialog("))
        assertFalse(
            "the storage tab must not add a second raw Dialog preview",
            Regex("\\bDialog\\s*\\(").containsMatchIn(tab),
        )
    }

    @Test
    fun `storage video and non-image media open in the system viewer`() {
        val tab = scrub(source("ui/serving/storage/ServingStorageTab.kt"))
        val open = functionBody(tab, "fun openOrPreview(")
        assertTrue("sanity: the storage openOrPreview branch is missing", open.isNotEmpty())
        assertTrue(open.contains("categoryOf(entry.mime) == FileCategory.IMAGE"))
        assertTrue(open.contains("previewImage = file"))
        assertTrue("the else branch must hand video to ACTION_VIEW", open.contains("openResolvedFile("))
        assertFalse("video must not enter the image dialog", open.contains("FileCategory.VIDEO"))

        val paths = projectFile("src/main/res/xml/file_paths.xml").readText(Charsets.UTF_8)
        assertTrue(
            "FileProvider must be able to grant the selected shared-storage file to the system viewer",
            paths.contains("<external-path name=\"shared_storage\" path=\".\" />"),
        )
    }
}
