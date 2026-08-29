package com.example.flikky.server

import com.example.flikky.server.dto.PeerInfoDto
import com.example.flikky.server.dto.StorageEntryDto
import com.example.flikky.server.dto.StorageListDto
import com.example.flikky.server.routes.FileStore
import com.example.flikky.server.routes.StorageBrowser
import com.example.flikky.server.routes.StorageFileHandle
import com.example.flikky.server.routes.StorageResult
import com.example.flikky.session.SessionState
import com.example.flikky.session.TransferStats
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * spec 4.4 的错误矩阵逐行一个用例，外加两条顺序性断言。
 *
 * 端口用 18100-18119：18080-18099 被 KtorServerExportModeTest 占着，撞端口会让两个套件
 * 在同一次运行里随机红。
 */
class StorageRoutesTest {

    private class FakeStore : FileStore {
        private val dir: File = Files.createTempDirectory("flikky-storage").toFile()
        override fun fileDir(sessionId: Long): File =
            File(File(dir, "sessions/$sessionId"), "files").apply { mkdirs() }
        override fun thumbFile(sessionId: Long, fileId: String): File =
            File(File(dir, "sessions/$sessionId/thumbs"), "$fileId.jpg")
    }

    /** 记录被调用过没有——用于断言「门禁在业务之前」。 */
    private class SpyBrowser(
        private val listResult: StorageResult<StorageListDto> =
            StorageResult.Ok(StorageListDto("", emptyList())),
        private val openResult: StorageResult<StorageFileHandle> = StorageResult.NotFound,
    ) : StorageBrowser {
        var listCalls = 0
        var openCalls = 0
        override fun list(relative: String): StorageResult<StorageListDto> {
            listCalls++
            return listResult
        }
        override fun open(relative: String): StorageResult<StorageFileHandle> {
            openCalls++
            return openResult
        }
    }

    private var server: KtorServer? = null

    @After
    fun tearDown() {
        server?.stop()
        server = null
    }

    private fun buildServer(
        browser: StorageBrowser,
        storageEnabled: Boolean = true,
        hasPermission: Boolean = true,
    ): KtorServer {
        val pin = PinAuth(nowMs = { 0L }, pinSupplier = { "000000" }, tokenSupplier = { "TOK" })
        return KtorServer(
            host = "127.0.0.1",
            startPort = 18100,
            endPort = 18119,
            pinAuth = pin,
            session = SessionState(nowMs = { 0L }),
            stats = TransferStats(nowMs = { 0L }),
            fileStore = FakeStore(),
            assetLoader = { byteArrayOf() },
            currentSessionId = { 1L },
            onPersistMessage = { _ -> },
            nowMs = { 1_700_000_000_000L },
            mode = ServiceMode.Transfer,
            peerInfoProvider = {
                PeerInfoDto(
                    deviceName = "Flikky",
                    phoneAvatarId = 0,
                    backgroundMode = "DEFAULT",
                    recallEnabled = false,
                )
            },
            storageBrowserProvider = { browser },
            storageBrowsingEnabled = { storageEnabled },
            hasStoragePermission = { hasPermission },
        )
    }

    private fun client() = HttpClient(CIO) { install(HttpCookies) }

    private suspend fun authenticate(client: HttpClient, port: Int) {
        val resp: HttpResponse = client.post("http://127.0.0.1:$port/api/auth") {
            contentType(ContentType.Application.Json)
            setBody("{\"pin\":\"000000\"}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `unauthenticated requests get 401 even when the switch is off`() = runBlocking {
        // 鉴权恒在最前。若开关判断排在前面，未授权方就能从 404 推出「开关是关的」。
        // v1.19.0 的 /app 路由踩过同一类顺序错误（模式判断排在鉴权之前）。
        val spy = SpyBrowser()
        val s = buildServer(spy, storageEnabled = false).also { server = it; it.start() }
        client().use { c ->
            val resp: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/list")
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
        assertEquals(0, spy.listCalls)
    }

    @Test
    fun `switch off returns 404 for an authenticated request`() = runBlocking {
        // 照 FavoriteRoutes 先例：功能开关关闭 = 该 endpoint 视同不存在。
        val spy = SpyBrowser()
        val s = buildServer(spy, storageEnabled = false).also { server = it; it.start() }
        client().use { c ->
            authenticate(c, s.boundPort)
            val resp: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/list")
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
        assertEquals(0, spy.listCalls)
    }

    @Test
    fun `missing system permission returns 403 with storage_permission_required`() = runBlocking {
        val spy = SpyBrowser()
        val s = buildServer(spy, hasPermission = false).also { server = it; it.start() }
        client().use { c ->
            authenticate(c, s.boundPort)
            val resp: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/list")
            assertEquals(HttpStatusCode.Forbidden, resp.status)
            assertTrue(resp.bodyAsText().contains("storage_permission_required"))
        }
        assertEquals(0, spy.listCalls)
    }

    @Test
    fun `an invalid path returns 400, distinct from not-found`() = runBlocking {
        val spy = SpyBrowser(listResult = StorageResult.InvalidPath)
        val s = buildServer(spy).also { server = it; it.start() }
        client().use { c ->
            authenticate(c, s.boundPort)
            val resp: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/list?path=../..")
            // 400 而不是 404：前端要能区分「路径非法」（退回根目录）与「文件不存在」（重拉当前目录）。
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            assertFalse(resp.bodyAsText().contains("storage_"))
        }
    }

    @Test
    fun `a restricted path returns 403 with storage_restricted`() = runBlocking {
        val spy = SpyBrowser(listResult = StorageResult.Restricted)
        val s = buildServer(spy).also { server = it; it.start() }
        client().use { c ->
            authenticate(c, s.boundPort)
            val resp: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/list?path=Android/data")
            assertEquals(HttpStatusCode.Forbidden, resp.status)
            val body = resp.bodyAsText()
            assertTrue(body.contains("storage_restricted"))
            // 两种 403 塌成一种，前端就分不出该渲染「去手机授权」还是「系统限制」。
            assertFalse(body.contains("storage_permission_required"))
        }
    }

    @Test
    fun `a missing path returns 404`() = runBlocking {
        val spy = SpyBrowser(listResult = StorageResult.NotFound)
        val s = buildServer(spy).also { server = it; it.start() }
        client().use { c ->
            authenticate(c, s.boundPort)
            val resp: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/list?path=nope")
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
    }

    @Test
    fun `a successful listing returns 200 with every field present`() = runBlocking {
        val spy = SpyBrowser(
            listResult = StorageResult.Ok(
                StorageListDto(path = "", entries = listOf(StorageEntryDto(name = "a.txt"))),
            ),
        )
        val s = buildServer(spy).also { server = it; it.start() }
        client().use { c ->
            authenticate(c, s.boundPort)
            val resp: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/list")
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            // isDir/restricted 的值等于各自默认值。一旦 ContentNegotiation 的 encodeDefaults
            // 被改回 false，它们会静默消失、浏览器读到 undefined——正是 v1.19.0 那个缺陷的形状。
            assertTrue("isDir missing: " + body, body.contains("isDir"))
            assertTrue("restricted missing: " + body, body.contains("restricted"))
        }
    }

    @Test
    fun `the file endpoint enforces the same gate order`() = runBlocking {
        // /api/storage/file 与 list 共用同一道 gate；这条防的是只给其中一个加门禁。
        val spy = SpyBrowser()
        val s = buildServer(spy, storageEnabled = false).also { server = it; it.start() }
        client().use { c ->
            val unauth: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/file?path=a.txt")
            assertEquals(HttpStatusCode.Unauthorized, unauth.status)
            authenticate(c, s.boundPort)
            val off: HttpResponse = c.get("http://127.0.0.1:${s.boundPort}/api/storage/file?path=a.txt")
            assertEquals(HttpStatusCode.NotFound, off.status)
        }
        assertEquals(0, spy.openCalls)
    }
}
