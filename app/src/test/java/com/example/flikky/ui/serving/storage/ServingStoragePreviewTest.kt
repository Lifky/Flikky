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

    /**
     * 阶段二为了让系统播放器能打开共享存储里的文件，给 FileProvider 加了
     * `<external-path name="shared_storage" path="." />` —— 那一行把**整个外置存储根**
     * 纳入了这个 provider 可以授权的范围。plan 里没有这一项，是执行期的最小适配。
     *
     * 这个放宽本身是必要的（功能就是「浏览共享存储」，能开的文件不可能预先枚举），
     * 而且实现是对的：`openResolvedFile` 逐个文件构造 URI、只在用户点击时触发。
     *
     * 但原守卫只断言了「那行存在」—— 它把放宽钉在原地，却没有守住真正要紧的两条性质。
     * 这两条才是这个面安全与否的分界：
     *
     *   1. provider 不可被外部应用直接访问（`exported="false"`）；
     *   2. 授权**只读**，任何地方都不许给外置存储文件发写权限。
     *
     * 少了第 2 条，一个 `FLAG_GRANT_WRITE_URI_PERMISSION` 就能把「让别的 App 看一眼」
     * 变成「让别的 App 改用户相册」，而没有任何测试会红。
     */
    @Test
    fun `the widened file provider stays unexported and read-only`() {
        val manifest = projectFile("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)
        val at = manifest.indexOf("androidx.core.content.FileProvider")
        assertTrue("sanity: 清单里找不到 FileProvider 声明", at > 0)
        // 切到 **<provider> 元素本身**，不要按字符数开窗口：
        // 上一版取了 at-200..at+400，那个范围把紧邻的 `<service android:exported="false" />`
        // 圈了进来，于是把 provider 改成 exported="true" 都不会红（逼红实测零条）。
        // 与 Codex 在 Task 12 记的「源码切片过宽 → 假绿」同一族。
        val open = manifest.lastIndexOf("<provider", at)
        val close = manifest.indexOf("</provider>", at)
        assertTrue("sanity: 切不出 <provider> 元素", open in 0 until at && close > at)
        val decl = manifest.substring(open, close)
        assertFalse(
            "sanity: 切片里混进了别的组件 —— 判据会失效",
            decl.contains("<service") || decl.contains("<activity"),
        )
        assertTrue(
            "FileProvider 必须 exported=\"false\" —— 它现在能授权整个外置存储根",
            decl.contains("android:exported=\"false\""),
        )
        assertTrue(
            "FileProvider 必须靠逐 URI 授权（grantUriPermissions=\"true\"）而不是常开",
            decl.contains("android:grantUriPermissions=\"true\""),
        )
    }

    @Test
    fun `no code path grants write access to a shared-storage file`() {
        // 扫整个 ui/ 包（剥注释后）：本项目对外发 URI 只有 FileIntents 那几处，
        // 但守卫要挡的是「以后有人在别处加一个写权限」，所以扫的是包不是文件。
        val offenders = mutableListOf<String>()
        // projectFile 只认文件（takeIf { it.isFile }），所以从一个已知文件反推目录，
        // 而不是把目录路径传进去 —— 第一版就是这么写红的。
        val root = projectFile("src/main/java/com/example/flikky/ui/components/FileIntents.kt")
            .parentFile.parentFile
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                val src = scrub(f.readText(Charsets.UTF_8))
                if (src.contains("FLAG_GRANT_WRITE_URI_PERMISSION")) offenders += f.name
            }
        assertEquals(
            "有地方给文件 URI 发了写权限。这个 provider 现在覆盖整个外置存储根，" +
                "写权限等于让接收方改用户的相册/文档 —— 打开文件只需要读：",
            emptyList<String>(),
            offenders,
        )
    }
}
