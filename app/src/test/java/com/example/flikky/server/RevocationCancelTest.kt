package com.example.flikky.server

import com.example.flikky.server.routes.AUTH_COOKIE
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.24 D82：手机断开设备即时生效。每个请求只在开始时鉴权一次 —— 没有这道挂钩，
 * 断开之后它正在进行的下载会照常跑完。用真实 CIO 引擎测，测试引擎的取消语义与线上不同。
 */
class RevocationCancelTest {
    private var server: EmbeddedServer<*, *>? = null

    @After fun tearDown() {
        server?.stop(0, 0)
        server = null
    }

    private suspend fun start(registry: DeviceRegistry): Int {
        val s = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            installRevocationCancel(registry::resolve, registry::watch)
            routing {
                get("/stream") {
                    call.respondBytesWriter {
                        repeat(CHUNKS) {
                            writeFully(ByteArray(CHUNK))
                            flush()
                            delay(5)
                        }
                    }
                }
                // ZIP、存储列表、相册等用的是阻塞写的 OutputStream，取消要经另一条路径生效。
                get("/blocking") {
                    call.respondRevocableOutputStream {
                        repeat(CHUNKS) {
                            write(ByteArray(CHUNK))
                            flush()
                            Thread.sleep(5)
                        }
                    }
                }
            }
        }.start(wait = false)
        server = s
        return s.engine.resolvedConnectors().first().port
    }

    /** 读到流结束或连接被掐断为止，返回读到的字节数。[afterFirst] 在读到第一块后调用。 */
    private suspend fun ByteReadChannel.drain(afterFirst: () -> Unit = {}): Int {
        val buf = ByteArray(CHUNK)
        var total = 0
        var first = true
        try {
            while (true) {
                val n = readAvailable(buf, 0, buf.size)
                if (n < 0) break
                total += n
                if (first && n > 0) { first = false; afterFirst() }
            }
        } catch (_: Exception) {
            // 连接被服务端掐断：正是要验证的情形。
        }
        return total
    }

    @Test fun `revoking a device cuts its download short and spares the other device`() = runBlocking {
        val registry = DeviceRegistry()
        val a = registry.issue(null, viaPin = true)
        val b = registry.issue(null, viaPin = true)
        val port = start(registry)
        HttpClient(ClientCIO).use { client ->
            withTimeout(15_000) {
                coroutineScope {
                    val other = async {
                        client.prepareGet("http://127.0.0.1:$port/stream") {
                            header(HttpHeaders.Cookie, "$AUTH_COOKIE=${b.token}")
                        }.execute { it.bodyAsChannel().drain() }
                    }
                    val revoked = client.prepareGet("http://127.0.0.1:$port/stream") {
                        header(HttpHeaders.Cookie, "$AUTH_COOKIE=${a.token}")
                    }.execute { it.bodyAsChannel().drain { registry.revoke(a.device.deviceId) } }
                    assertTrue("the revoked download must stop early, got $revoked bytes", revoked < TOTAL / 2)
                    assertEquals("the other device's download is untouched", TOTAL, other.await())
                }
            }
        }
    }

    @Test fun `a blocking output stream is cut short too`() = runBlocking {
        val registry = DeviceRegistry()
        val a = registry.issue(null, viaPin = true)
        val port = start(registry)
        HttpClient(ClientCIO).use { client ->
            val got = withTimeout(15_000) {
                client.prepareGet("http://127.0.0.1:$port/blocking") {
                    header(HttpHeaders.Cookie, "$AUTH_COOKIE=${a.token}")
                }.execute { it.bodyAsChannel().drain { registry.revoke(a.device.deviceId) } }
            }
            assertTrue("the revoked stream must stop early, got $got bytes", got < TOTAL / 2)
        }
    }

    @Test fun `a request without a device is left alone`() = runBlocking {
        val registry = DeviceRegistry()
        val port = start(registry)
        HttpClient(ClientCIO).use { client ->
            val got = withTimeout(15_000) {
                client.prepareGet("http://127.0.0.1:$port/stream").execute { it.bodyAsChannel().drain() }
            }
            assertEquals(TOTAL, got)
        }
    }

    // 线上的流式响应都必须用可撤销版本：原生 respondOutputStream 被取消后仍会写到底。
    @Test fun `server code streams only through the revocable output stream`() {
        val offenders = java.io.File("src/main/java/com/example/flikky/server").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "RevocationGuard.kt" }
            .filter { Regex("""(?<![A-Za-z])respondOutputStream\(""").containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertTrue("use respondRevocableOutputStream instead: $offenders", offenders.isEmpty())
    }

    private companion object {
        const val CHUNK = 1024
        const val CHUNKS = 200
        const val TOTAL = CHUNK * CHUNKS
    }
}
