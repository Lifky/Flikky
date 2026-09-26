package com.example.flikky.server

import com.example.flikky.server.routes.FileStore
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files

/**
 * spec §4.6 硬性要求：停止后立即重启必须绑回同一端口。
 * 绑不回去时扫描逻辑会悄悄换到下一个端口，用户看到的就是「刚停的端口被占用」。
 */
class KtorServerRestartPortTest {

    private class FakeStore : FileStore {
        private val dir: File = Files.createTempDirectory("flikky-port").toFile()
        override fun fileDir(sessionId: Long): File = File(dir, "s$sessionId").apply { mkdirs() }
        override fun thumbFile(sessionId: Long, fileId: String): File = File(dir, "$fileId.jpg")
    }

    private fun server(start: Int) = KtorServer(
        host = "127.0.0.1",
        startPort = start,
        endPort = start + 19,
        pinAuth = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" }),
        session = SessionState(nowMs = { 0L }),
        stats = TransferStats(nowMs = { 0L }),
        fileStore = FakeStore(),
        assetLoader = { byteArrayOf() },
        currentSessionId = { 1L },
        onPersistMessage = { _ -> },
        nowMs = { 0L },
        mode = ServiceMode.Transfer,
    )

    /**
     * 完成一次真实请求，并让**服务端先关**连接（`Connection: close`）。
     * TIME_WAIT 只落在先关的一端 —— 没有这一步，测试根本碰不到「刚停的端口绑不回去」的风险。
     */
    private fun hit(port: Int) {
        java.net.Socket("127.0.0.1", port).use { sock ->
            sock.soTimeout = 3000
            sock.getOutputStream().write("GET / HTTP/1.1\r\nHost: t\r\nConnection: close\r\n\r\n".toByteArray())
            sock.getOutputStream().flush()
            sock.getInputStream().readBytes() // 读到服务端关闭为止
        }
    }

    @Test fun `restarting right after stop binds the same port, five times in a row`() {
        val base = 18280
        var s = server(base)
        val first = s.start()
        repeat(5) {
            hit(first)
            s.stop()
            s = server(base)
            assertEquals("restart #${it + 1} moved off the port", first, s.start())
        }
        s.stop()
    }

    /**
     * 端口被别人占着时：换到下一个端口，**且不留任何未捕获异常**。
     * Ktor CIO 会在后台协程里把 BindException 再抛一次；在 Android 上，
     * 未捕获异常会杀掉整个 App 进程（2026-09-26 在 API 36 上实测到）。
     * 设备上的同一条守卫见 androidTest/.../KtorServerRestartPortInstrumentedTest。
     */
    @Test fun `a port held by someone else moves to the next one without an uncaught exception`() {
        val base = 18380
        val uncaught = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
        try {
            ServerSocket(base, 50, InetAddress.getByName("127.0.0.1")).use {
                val s = server(base)
                assertEquals(base + 1, s.start())
                Thread.sleep(500) // 给后台协程时间把异常抛出来
                s.stop()
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        assertEquals("uncaught: $uncaught", emptyList<Throwable>(), uncaught.toList())
    }

    @Test fun `the scan skips ports browsers refuse to open`() {
        // 6665–6669 都在浏览器受限端口表里：绑上了电脑也打不开，扫描得直接跳过。
        val s = server(6665)
        try {
            assertEquals(6670, s.start())
        } finally {
            s.stop()
        }
    }
}
