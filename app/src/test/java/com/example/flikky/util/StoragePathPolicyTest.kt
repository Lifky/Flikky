package com.example.flikky.util

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoragePathPolicyTest {

    @get:Rule val tmp = TemporaryFolder()

    /** 用真实目录而不是 mock File：被测的那件事就是 canonicalFile，mock 掉它等于什么都没测。 */
    private fun root(): File = tmp.newFolder("base")

    @Test
    fun `empty and dot resolve to the root itself`() {
        val root = root()
        assertEquals(root.canonicalFile, StoragePathPolicy.resolve(root, ""))
        assertEquals(root.canonicalFile, StoragePathPolicy.resolve(root, "."))
        assertEquals(root.canonicalFile, StoragePathPolicy.resolve(root, "/"))
    }

    @Test
    fun `a plain subdirectory resolves under the root`() {
        val root = root()
        File(root, "Download").mkdirs()
        assertEquals(File(root, "Download").canonicalFile, StoragePathPolicy.resolve(root, "Download"))
    }

    @Test
    fun `dot dot escapes are rejected`() {
        val root = root()
        assertNull(StoragePathPolicy.resolve(root, "../secret"))
        assertNull(StoragePathPolicy.resolve(root, "Download/../.."))
        assertNull(StoragePathPolicy.resolve(root, "../"))
    }

    @Test
    fun `a symlink pointing outside the root is rejected`() {
        val root = root()
        val outside = tmp.newFolder("outside")
        File(outside, "secret.txt").writeText("x")
        // Windows 建符号链接需要提权或开发者模式，CI/开发机上都可能拿不到。
        // 拿不到时**显式 skip**（JUnit 报 skipped），不要 try/catch 成静默通过——
        // 那就成了「断言绿着，它命名的东西没被测」。
        //
        // 「必须用 canonicalFile 而不是 absoluteFile」这条规则本身有平台无关的守卫：
        // 见 `dot dot escapes are rejected`——absoluteFile 下 `<root>/../secret` 的字面
        // 路径仍以 root 为前缀，前缀比对会误判放行，那条会立刻转红。本条是额外的
        // 真符号链接覆盖，只在支持的平台上生效。
        try {
            Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        } catch (e: Exception) {
            org.junit.Assume.assumeNoException("symlink creation not permitted on this platform", e)
        }
        assertNull(StoragePathPolicy.resolve(root, "link/secret.txt"))
    }

    @Test
    fun `a sibling directory sharing the root name prefix is rejected`() {
        // 钉「前缀边界必须落在分隔符上」。root=<tmp>/base，兄弟目录 <tmp>/base0abc：
        // 朴素的 targetPath.startsWith(rootPath) 会判成子路径并放行。
        val root = root()
        File(tmp.root, "base0abc").mkdirs()
        assertNull(StoragePathPolicy.resolve(root, "../base0abc"))
    }

    @Test
    fun `relativize returns empty for the root and a slash path below it`() {
        val root = root()
        val nested = File(root, "DCIM/Camera").apply { mkdirs() }
        assertEquals("", StoragePathPolicy.relativize(root, root))
        assertEquals("DCIM/Camera", StoragePathPolicy.relativize(root, nested))
    }
}
