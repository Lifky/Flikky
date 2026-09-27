package com.example.flikky.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D78：手机上的收藏变化（含在 App 里取消）要推给浏览器，星标才会变回空心。
 * 推送必须经 `ktor?.wsHub` 现取（CLAUDE.md 跨 rebind 引用规范），任务随服务停止取消。
 */
class TransferServicePeerFavoriteSourceTest {
    private val service: String by lazy {
        var dir: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (dir != null) {
            listOf("src/main/java", "app/src/main/java")
                .map { File(dir, "$it/com/example/flikky/service/TransferService.kt") }
                .firstOrNull { it.isFile }?.let { return@lazy it.readText() }
            dir = dir.parentFile
        }
        error("missing TransferService.kt")
    }

    @Test fun `favorite changes are pushed through the current hub`() {
        assertTrue(service.contains("peerFavoritedIds("))
        assertTrue(service.contains("ktor?.wsHub?.broadcast(\"favorites_state\""))
    }

    @Test fun `the push stops with the service`() {
        assertTrue(service.contains("peerFavoritesJob?.cancel(); peerFavoritesJob = null"))
    }

    @Test fun `history reads the favorited ids of the running session`() {
        assertTrue(service.contains("favoritedIds = { ServiceLocator.favoritesRepository.favoritedIds(currentSessionId) }"))
    }
}
