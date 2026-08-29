package com.example.flikky.server.dto

import kotlinx.serialization.Serializable

/**
 * 共享存储里的一项。
 *
 * HTTP 下发走 `KtorServer` 的 ContentNegotiation（已装 `encodeDefaults = true`），字段齐全。
 * 但若将来这些 DTO 也经 WebSocket 手动序列化，**必须走 [WireJson]**：裸 `Json` 会把等于
 * 默认值的字段整个省掉，`isDir = false` 与 `restricted = false` 正是这类字段。v1.19.0 的四个
 * 「同步只单向生效」缺陷就是这么来的，而同一批字段走 HTTP 一直是齐的——「手动刷新页面就
 * 好了」这个现象的成因就是两条通道编码策略不一致（见 WireJson 的 KDoc）。
 */
@Serializable
data class StorageEntryDto(
    val name: String,
    val isDir: Boolean = false,
    /** 目录为 0。 */
    val size: Long = 0L,
    /** epoch millis。 */
    val mtime: Long = 0L,
    /** 目录与未知类型为 null，按扩展名猜。 */
    val mime: String? = null,
    /** 仅目录；不可读时 null。不递归统计——那会在大目录上卡住一次请求。 */
    val childCount: Int? = null,
    /** Android/data、Android/obb 及其子层级：系统锁死，两端置灰。 */
    val restricted: Boolean = false,
)

@Serializable
data class StorageListDto(
    /** 规范化后的相对路径，`""` 表示根。 */
    val path: String,
    val entries: List<StorageEntryDto>,
)

/** 403 时的机器可读原因。前端据此在「去手机授权」和「系统限制」两种引导间选择。 */
@Serializable
data class StorageErrorDto(val code: String)
