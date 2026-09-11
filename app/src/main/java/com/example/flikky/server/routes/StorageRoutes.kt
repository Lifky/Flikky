package com.example.flikky.server.routes

import com.example.flikky.server.dto.StorageEntryDto
import com.example.flikky.server.dto.StorageErrorDto
import com.example.flikky.server.dto.StorageListDto
import com.example.flikky.server.dto.WireJson
import com.example.flikky.server.dto.StorageStreamHeadDto
import com.example.flikky.util.SortSpec
import com.example.flikky.util.ThumbnailDiskCache
import com.example.flikky.util.thumbnailCacheKey
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 存储访问的结果，四态与 spec 4.4 的错误表 1:1 对应。
 *
 * 不用 `T?` 加异常：null 无法区分「路径非法」「不存在」「系统限制」，而这三者在前端要给
 * 不同的引导文案。把区分做成类型，调用方就不可能忘记处理其中一种。
 */
sealed interface StorageResult<out T> {
    data class Ok<T>(val value: T) : StorageResult<T>
    data object InvalidPath : StorageResult<Nothing>
    data object NotFound : StorageResult<Nothing>
    data object Restricted : StorageResult<Nothing>
}

/** 下载一个文件所需的全部信息。没有消费者的字段就是负担，故只有这三个。 */
data class StorageFileHandle(val file: File, val fileName: String, val mime: String)

/**
 * 共享存储的只读视图。
 *
 * 只认相对路径与自有 DTO，**不认 `Context`、不认 `Uri`** —— 红线「不把 Android Context
 * 穿透到 server/ 包」。实现在 `data/SharedStorageBrowser`，由 `di/ServiceLocator` 装配。
 */
/**
 * 一次流式列举：路径已确认可读，条目分批产出。
 *
 * 路径校验与内容产出分开，是为了让 HTTP 状态码还能用：校验同步完成（返回
 * [StorageResult]，路由据此发 400/403/404），确认可读之后才开始写响应体。
 * 一旦开始写就没法再改状态码了。
 */
data class StorageStream(
    val path: String,
    val batches: Flow<List<StorageEntryDto>>,
)

interface StorageBrowser {
    fun list(
        relative: String,
        sort: SortSpec = SortSpec.NameAsc,
    ): StorageResult<StorageListDto>

    /** 下载一个文件与顺序无关，故不收 [SortSpec]。 */
    fun open(relative: String): StorageResult<StorageFileHandle>

    /**
     * 流式列举。返回 Ok 时**尚未**产生任何条目——[StorageStream.batches] 被收集时才枚举。
     * 校验失败的语义与 [list] 完全一致。
     */
    fun listStream(
        relative: String,
        sort: SortSpec = SortSpec.NameAsc,
    ): StorageResult<StorageStream>
}

/**
 * v1.20.0 浏览器端「文件」tab 的只读接口。
 *
 * 判断顺序是本函数的核心，**不可调整**：
 *   1. 鉴权（401）—— 恒在最前。排在开关之后，未授权方就能从 404 推出开关状态。
 *   2. 主开关（404）—— 照 [favoriteRoutes] 先例：功能关闭 = 该 endpoint 视同不存在。
 *   3. 系统权限（403 + code）—— 前端据此渲染「请在手机上完成存储授权」而非报错。
 *   4. 路径与业务（400 / 403 / 404 / 200）。
 *
 * [browser] 是 `() -> StorageBrowser?` 而非 `StorageBrowser`：跨 Wi-Fi rebind 时 KtorServer
 * 整组被替换，直接持有实例会变成死引用（CLAUDE.md 的 rebind 引用规范，本项目踩过三次）。
 *
 * **刻意不提供 `?inline=1`**：存储面板首版只有下载、没有预览/lightbox（spec 5.3），
 * 所以 inline 没有任何消费者，而它会把「用什么 Content-Type 渲染」的选择权交给调用方，
 * 等于白送一个同源 XSS 面。与 [favoriteRoutes] 同一裁决。
 *
 * `?sort=` 是只读视图参数：在门禁之后解析，非法即回落默认顺序，**不产生新的状态码分支**。
 */
fun Route.storageRoutes(
    authGate: AuthGate,
    enabled: suspend () -> Boolean,
    hasPermission: () -> Boolean,
    browser: () -> StorageBrowser?,
    storageThumbFile: ((String) -> File)? = null,
    thumbnailer: ThumbnailGenerator = ThumbnailGenerator { _, _, _ -> false },
    thumbnailCacheMaxBytes: () -> Long = { 100L * 1024L * 1024L },
) {
    val thumbnailCache = storageThumbFile?.let { provider ->
        ThumbnailDiskCache(
            directory = provider("0".repeat(64)).parentFile ?: File("."),
            maxBytes = thumbnailCacheMaxBytes,
        )
    }

    suspend fun ApplicationCall.passesGate(): Boolean {
        if (!authGate.isAuthorized(request.cookies[AUTH_COOKIE])) {
            respond(HttpStatusCode.Unauthorized)
            return false
        }
        if (!enabled()) {
            respond(HttpStatusCode.NotFound)
            return false
        }
        if (!hasPermission()) {
            respond(HttpStatusCode.Forbidden, StorageErrorDto("storage_permission_required"))
            return false
        }
        return true
    }

    suspend fun ApplicationCall.respondFailure(result: StorageResult<*>) = when (result) {
        // 400 而不是 404：前端要能区分「路径非法」（退回根目录）与「不存在」（重拉当前目录）。
        StorageResult.InvalidPath -> respond(HttpStatusCode.BadRequest)
        StorageResult.Restricted ->
            respond(HttpStatusCode.Forbidden, StorageErrorDto("storage_restricted"))
        else -> respond(HttpStatusCode.NotFound)
    }

    /**
     * NDJSON 流式列举：`?stream=1`。
     *
     * 一行一个 JSON 对象：首行 `{"path":"..."}`，随后每行一个条目，末行 `{"done":true}`。
     *
     * **末行的 `done` 不是装饰**：HTTP 状态码在第一个字节发出后就定了，之后中断
     * （手机休眠、Wi-Fi 切换、目录读到一半失败）在客户端看起来与「正常读完」一样。
     * 有了这一行，客户端能区分「读完了」和「被截断了」——没有它，用户会把半个目录
     * 当成完整目录，而这是静默的。
     *
     * **每批 flush 也不是装饰**：不 flush 的话数据攒在缓冲区里，等攒满或流关闭才发出，
     * 客户端仍然是「等半天然后一次性收到全部」——流式就没了。
     */
    get("/api/storage/list") {
        if (!call.passesGate()) return@get
        val b = browser() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        // 只读视图参数，形态就是 SortSpec.format() 的输出（客户端把 localStorage 里存的
        // 字符串原样传过来），所以两端共用同一个解析器、零转换。
        //
        // **解析失败回落默认值，不返回 400**：它不是业务输入，是「怎么看」。
        // 因为一个存坏了的偏好就把整个文件面板锁死，代价与收益完全不成比例。
        val sort = SortSpec.parse(call.request.queryParameters["sort"]) ?: SortSpec.NameAsc
        if (call.request.queryParameters["stream"] == "1") {
            val requested = call.request.queryParameters["path"].orEmpty()
            // 校验同步完成，状态码还能用；确认可读之后才开始写响应体。
            val opened = withContext(Dispatchers.IO) { b.listStream(requested, sort) }
            if (opened !is StorageResult.Ok) {
                call.respondFailure(opened)
                return@get
            }
            val stream = opened.value
            call.respondOutputStream(contentType = NDJSON, status = HttpStatusCode.OK) {
                val out = this
                fun line(text: String) {
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.write(LF)
                    // 每行写完就 flush。见本路由 KDoc：不 flush 就不是流式。
                    out.flush()
                }
                line(WireJson.encodeToString(
                        StorageStreamHeadDto.serializer(),
                        StorageStreamHeadDto(path = stream.path),
                    ))
                stream.batches.collect { batch ->
                    batch.forEach { line(WireJson.encodeToString(StorageEntryDto.serializer(), it)) }
                }
                line(DONE_LINE)
            }
            return@get
        }
        // 列举是阻塞 I/O（listFiles + 每条目 3 次 stat + 每个子目录一次 readdir 算项数）。
        // 大目录里这是几百毫秒到几秒；留在请求协程的默认调度器上会占住事件循环线程，
        // 拖慢同一时刻的其它请求（消息、文件流）。
        val listed = withContext(Dispatchers.IO) {
            b.list(call.request.queryParameters["path"].orEmpty(), sort)
        }
        when (val result = listed) {
            is StorageResult.Ok -> call.respond(result.value)
            else -> call.respondFailure(result)
        }
    }

    get("/api/storage/file") {
        if (!call.passesGate()) return@get
        val b = browser() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        when (val result = b.open(call.request.queryParameters["path"].orEmpty())) {
            is StorageResult.Ok -> call.respondStorageFile(result.value)
            else -> call.respondFailure(result)
        }
    }

    get("/api/storage/thumb") {
        if (!call.passesGate()) return@get
        val provider = storageThumbFile ?: run {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val cache = thumbnailCache ?: run {
            call.respond(HttpStatusCode.ServiceUnavailable)
            return@get
        }
        val b = browser() ?: run { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
        val opened = withContext(Dispatchers.IO) {
            b.open(call.request.queryParameters["path"].orEmpty())
        }
        if (opened !is StorageResult.Ok) {
            call.respondFailure(opened)
            return@get
        }
        val handle = opened.value
        if (!isMediaMime(handle.mime) || !handle.file.isFile) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val key = thumbnailCacheKey(
            absolutePath = handle.file.absolutePath,
            mtime = handle.file.lastModified(),
            size = handle.file.length(),
        )
        val target = provider(key)
        val cached = cache.get(target.name)
        if (cached != null && cached.isFile && cached.length() > 0L) {
            call.respondBytes(cached.readBytes(), ContentType.Image.JPEG)
            return@get
        }
        cached?.delete()

        if (thumbnailCacheMaxBytes() <= 0L) {
            val parent = target.parentFile ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            if (!parent.exists() && !parent.mkdirs()) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val temp = runCatching { File.createTempFile(".thumb-", ".tmp", parent) }
                .getOrNull() ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            try {
                val generated = runCatching {
                    thumbnailer.generate(handle.file, handle.mime, temp)
                }.getOrDefault(false)
                if (!generated || !temp.isFile || temp.length() == 0L) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respondBytes(temp.readBytes(), ContentType.Image.JPEG)
            } finally {
                temp.delete()
            }
            return@get
        }

        val generated = cache.put(target.name) { file ->
            thumbnailer.generate(handle.file, handle.mime, file)
        }
        if (generated == null || !generated.isFile || generated.length() == 0L) {
            generated?.delete()
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.respondBytes(generated.readBytes(), ContentType.Image.JPEG)
    }
}

/**
 * 下载一个存储文件。header 与 64KB 泵**逐行照 [favoriteRoutes] 的下载那半**，
 * 不新写一份——两份下载实现迟早会在 header 细节上分叉。
 *
 * 与那边一致：永远 attachment + octet-stream，调用方不能选择渲染类型。
 * 文件名沿用项目既有的 `ContentDisposition.withParameter` 写法（本项目三处下载路由
 * 都是这一套；只在这一处改成 RFC 5987 反而会制造不一致）。
 */
private suspend fun ApplicationCall.respondStorageFile(handle: StorageFileHandle) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment
            .withParameter(ContentDisposition.Parameters.FileName, handle.fileName)
            .toString(),
    )
    response.header(HttpHeaders.ContentLength, handle.file.length().toString())
    respondOutputStream(contentType = ContentType.Application.OctetStream, status = HttpStatusCode.OK) {
        handle.file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                write(buf, 0, n)
            }
            flush()
        }
    }
}

/** NDJSON 的 content type。`application/x-ndjson` 是这个格式的事实标准。 */
private val NDJSON = ContentType("application", "x-ndjson")

private val LF = byteArrayOf(10)

/** 流正常结束的标记行。客户端没收到它就该判定为截断，见 storageRoutes 的 KDoc。 */
private const val DONE_LINE = "{\"done\":true}"
