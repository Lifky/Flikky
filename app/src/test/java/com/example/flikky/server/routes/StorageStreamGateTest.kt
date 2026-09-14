package com.example.flikky.server.routes

import com.example.flikky.server.PinAuth
import com.example.flikky.server.dto.StorageListDto
import com.example.flikky.util.SortSpec
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Closing peer access must stop an in-progress download (spec section 3.3).
 *
 * The request gate runs once before streaming starts. Without another check in the write loop,
 * a large file keeps leaving the phone after the UI says peer access is closed.
 */
class StorageStreamGateTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun bigFile(): StorageFileHandle {
        val file = tmp.newFile("big.bin")
        file.writeBytes(ByteArray(64 * 1024 * 4) { (it % 251).toByte() })
        return StorageFileHandle(file, "big.bin", "application/octet-stream")
    }

    private fun app(
        handle: StorageFileHandle,
        gate: suspend () -> Boolean,
    ): io.ktor.server.application.Application.() -> Unit = {
        install(ContentNegotiation) { json() }
        routing {
            storageRoutes(
                authGate = AuthGate(
                    false,
                    PinAuth(
                        nowMs = { 0L },
                        pinSupplier = { "000000" },
                        tokenSupplier = { "TOKEN" },
                    ),
                ),
                enabled = gate,
                hasPermission = { true },
                browser = {
                    object : StorageBrowser {
                        override fun list(relative: String, sort: SortSpec) =
                            StorageResult.Ok(StorageListDto("", emptyList()))

                        override fun listStream(relative: String, sort: SortSpec) =
                            StorageResult.Ok(StorageStream("", emptyFlow()))

                        override fun open(relative: String) = StorageResult.Ok(handle)
                    }
                },
            )
        }
    }

    private suspend fun HttpClient.download(path: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        prepareGet(path).execute { response ->
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = channel.readAvailable(buffer)
                if (read < 0) break
                if (read > 0) bytes.write(buffer, 0, read)
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun `a download that stays allowed delivers the whole file`() {
        val handle = bigFile()
        testApplication {
            application(app(handle) { true })
            val bytes = client.download("/api/storage/file?path=big.bin")
            assertEquals(
                "an allowed download must deliver every byte",
                handle.file.length(),
                bytes.size.toLong(),
            )
        }
    }

    @Test
    fun `closing peer access mid-stream truncates the download`() {
        val handle = bigFile()
        val allowed = AtomicBoolean(true)
        var checks = 0
        testApplication {
            application(
                app(handle) {
                    checks += 1
                    if (checks > 2) allowed.set(false)
                    allowed.get()
                },
            )
            val bytes = client.download("/api/storage/file?path=big.bin")
            val total = handle.file.length()
            assertTrue(
                "the stream must stop when peer access closes, got all " + bytes.size +
                    " of " + total + " bytes -- the gate is only checked once per request",
                bytes.size.toLong() < total,
            )
        }
        assertTrue(
            "sanity: the gate was never re-checked inside the write loop (checks=" + checks + ")",
            checks > 2,
        )
    }
}
