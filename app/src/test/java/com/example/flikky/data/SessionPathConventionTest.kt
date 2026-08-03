package com.example.flikky.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B17 守卫：文件落盘路径的唯一事实源是 [SessionFileStore]。
 * main 源码里禁止其他文件手拼 `filesDir/sessions/...` 路径——目录结构一旦调整，
 * 手拼处会静默指向错误路径（v1.16 验收在 ServingViewModel/FileIntents 各发现一处）。
 */
class SessionPathConventionTest {

    private fun mainJavaRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val fromModule = File(dir, "src/main/java/com/example/flikky")
            if (fromModule.isDirectory) return File(dir, "src/main/java")
            val fromRepo = File(dir, "app/src/main/java/com/example/flikky")
            if (fromRepo.isDirectory) return File(dir, "app/src/main/java")
            dir = dir.parentFile
        }
        error("cannot locate src/main/java from user.dir=" + System.getProperty("user.dir"))
    }

    @Test
    fun `no hand-built sessions path outside SessionFileStore`() {
        val root = mainJavaRoot()
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "SessionFileStore.kt" }
            .filter { it.readText(Charsets.UTF_8).contains("filesDir, \"sessions") }
            .map { it.relativeTo(root).path }
            .toList()
        assertTrue(
            "hand-built sessions path found (use SessionFileStore.messageFile/fileDir): $offenders",
            offenders.isEmpty(),
        )
    }
}
