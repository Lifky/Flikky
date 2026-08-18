package com.example.flikky.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 源码级守卫，配合 TransferControllerRebindReferenceTest 的同一思路：
 * 收藏 provider 必须以 lambda 注入，且只在 Transfer 模式注册路由。
 */
class KtorServerFavoritesWiringTest {

    private fun read(path: String): String {
        val candidates = listOf(
            File(path),
            File("app/$path"),
        )
        val f = candidates.firstOrNull { it.isFile }
            ?: error("找不到 $path from ${File(".").absolutePath}")
        return f.readText()
    }

    @Test
    fun `favorite routes are registered only in transfer mode`() {
        val src = read("src/main/java/com/example/flikky/server/KtorServer.kt")
        val transferBlock = src
            .substringAfter("private fun Route.installTransferRoutes")
            .substringBefore("private fun Route.installExportRoutes")
        assertTrue("Transfer 模式应注册 favoriteRoutes", transferBlock.contains("favoriteRoutes("))

        val exportBlock = src.substringAfter("private fun Route.installExportRoutes")
        assertEquals(
            "Export 模式不应暴露收藏接口（导出页无收藏 tab）",
            false,
            exportBlock.contains("favoriteRoutes("),
        )
    }

    @Test
    fun `favorites provider is injected as a lambda so it survives wifi rebind`() {
        val src = read("src/main/java/com/example/flikky/server/KtorServer.kt")
        assertTrue(
            "favoritesProvider 必须是 suspend lambda，不能直接持有 repository 实例",
            src.contains("favoritesProvider: suspend () -> FavoritesResponseDto"),
        )
        assertTrue(
            "favoriteRowFileResolver 必须是 suspend lambda",
            src.contains("favoriteRowFileResolver: suspend (Long) -> java.io.File?"),
        )
    }

    @Test
    fun `TransferService injects favorites via lambdas not captured server members`() {
        val src = read("src/main/java/com/example/flikky/service/TransferService.kt")
        assertTrue(
            "TransferService 应注入 favoritesProvider",
            src.contains("favoritesProvider ="),
        )
        assertTrue(
            "TransferService 应注入 favoriteRowFileResolver",
            src.contains("favoriteRowFileResolver ="),
        )
    }
}
