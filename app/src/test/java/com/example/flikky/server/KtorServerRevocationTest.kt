package com.example.flikky.server

import com.example.flikky.server.routes.AUTH_COOKIE
import com.example.flikky.server.routes.FileStore
import com.example.flikky.server.routes.WS_CLOSE_REVOKED
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

/**
 * v1.24 D82：在真实 [KtorServer] 上验证断开即时生效的接线 ——
 * 文件下载被掐断；WebSocket 不走统一取消，仍由 `closeDevice` 先推 `device_revoked` 再以 4403 关闭。
 */
class KtorServerRevocationTest {
    private val dir: File = Files.createTempDirectory("flikky-revoke").toFile()
    private val store = object : FileStore {
        override fun fileDir(sessionId: Long): File = File(dir, "files").apply { mkdirs() }
        override fun thumbFile(sessionId: Long, fileId: String): File = File(dir, "$fileId.jpg")
    }
    private var server: KtorServer? = null

    @After fun tearDown() {
        server?.stop()
        server = null
        dir.deleteRecursively()
    }

    private fun start(registry: DeviceRegistry): Pair<KtorServer, Int> {
        val s = KtorServer(
            host = "127.0.0.1",
            startPort = 18120,
            endPort = 18139,
            pinAuth = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" }),
            deviceRegistry = registry,
            session = SessionState(nowMs = { 0L }),
            stats = TransferStats(nowMs = { 0L }),
            fileStore = store,
            assetLoader = { byteArrayOf() },
            currentSessionId = { 1L },
            onPersistMessage = { _ -> },
        )
        server = s
        return s to s.start()
    }

    @Test fun `disconnecting a device cuts its file download short`() = runBlocking {
        val size = 64L * 1024 * 1024
        RandomAccessFile(File(store.fileDir(1L), "big"), "rw").use { it.setLength(size) }
        val registry = DeviceRegistry()
        val device = registry.issue(null, viaPin = true)
        val (_, port) = start(registry)
        HttpClient(CIO).use { client ->
            val read = withTimeout(20_000) {
                client.prepareGet("http://127.0.0.1:$port/api/files/big") {
                    header(HttpHeaders.Cookie, "$AUTH_COOKIE=${device.token}")
                }.execute { resp ->
                    val ch = resp.bodyAsChannel()
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    try {
                        while (true) {
                            val n = ch.readAvailable(buf, 0, buf.size)
                            if (n < 0) break
                            if (total == 0L && n > 0) registry.revoke(device.device.deviceId)
                            total += n
                        }
                    } catch (_: Exception) {
                        // 被掐断。
                    }
                    total
                }
            }
            assertTrue("download must stop well before the end, read $read of $size", read < size / 2)
        }
    }

    @Test fun `a disconnected websocket still hears why before it closes`() = runBlocking {
        val registry = DeviceRegistry()
        val device = registry.issue(null, viaPin = true)
        val (s, port) = start(registry)
        HttpClient(CIO) { install(WebSockets) }.use { client ->
            withTimeout(10_000) {
                client.webSocket("ws://127.0.0.1:$port/ws", request = {
                    header(HttpHeaders.Cookie, "$AUTH_COOKIE=${device.token}")
                }) {
                    // 连上即推名册；收到后再断开，与 TransferController.revokeDevice 同序：先删登记、再 closeDevice。
                    val first = (incoming.receive() as Frame.Text).readText()
                    assertTrue(first, first.startsWith("""{"type":"peers""""))
                    registry.revoke(device.device.deviceId)
                    s.wsHub.closeDevice(device.device.deviceId)
                    var sawRevoked = false
                    for (frame in incoming) {
                        if (frame is Frame.Text && frame.readText().contains("\"device_revoked\"")) sawRevoked = true
                    }
                    assertTrue("the browser must be told it was disconnected", sawRevoked)
                    assertEquals(WS_CLOSE_REVOKED, closeReason.await()!!.code)
                }
            }
        }
    }
}
