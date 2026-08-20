package com.example.flikky.server

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 源码级守卫，配合 TransferControllerRebindReferenceTest 的同一思路：收藏 provider 必须以
 * lambda 注入，保证 Wi-Fi rebind 后不指向已废弃的实例。
 *
 * v1.19.0 fix wave: "只在 Transfer 模式注册路由" 这条不再靠切源码文本断言——那是脆弱的字符串
 * 匹配，源码稍微重排就会假绿/假红。真行为验证见 KtorServerExportModeTest 的
 * `export mode does not mount favorites route`（真起服务器打 HTTP，断言 404）。
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
    fun `favorites provider is injected as a lambda so it survives wifi rebind`() {
        val src = read("src/main/java/com/example/flikky/server/KtorServer.kt")
        assertTrue(
            "favoritesProvider 必须是 suspend lambda，不能直接持有 repository 实例",
            src.contains("favoritesProvider: suspend () -> FavoritesResponseDto"),
        )
        assertTrue(
            "favoriteRowFileResolver 必须是 suspend lambda",
            src.contains(
                "favoriteRowFileResolver: suspend (Long) -> " +
                    "com.example.flikky.server.routes.FavoriteFileHandle?",
            ),
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
