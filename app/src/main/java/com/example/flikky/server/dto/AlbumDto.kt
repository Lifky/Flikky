package com.example.flikky.server.dto

import kotlinx.serialization.Serializable

/**
 * 相册里的一项。
 *
 * 走 NDJSON 时**必须用 [WireJson]**：裸 `Json` 会把等于默认值的字段整个省掉，
 * `durationMs = 0` 与空的 [dateKey] 正是这类字段。v1.19.0 的四个
 * 「同步只单向生效」缺陷就是这么来的。
 */
@Serializable data class AlbumItemDto(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long = 0,
    val takenAtMs: Long = 0,
    val durationMs: Long = 0,
    /**
     * 拍摄日期，`yyyy-MM-dd`，**按手机时区算好后下发**。
     *
     * v1.21.0 装机验收（Screenshot_12 / 13）暴露过双端不一致：手机与浏览器各自从
     * [takenAtMs] 算日期，而两台设备时区不同，于是跨午夜的照片在两端掉进不同分组。
     * 裁决（D65）是日期以拍摄设备为准、服务端算一次，所以这个字段是那条契约的载体。
     * 浏览器端**只消费它，不做日期运算**。
     */
    val dateKey: String = "",
    /**
     * 所在相册簿（MediaStore 的 `BUCKET_DISPLAY_NAME`），如 `Camera` / `Screenshots`。
     *
     * 时间线的分组头顺带显示它（与 Android 系统相册的「日期 ｜ 文件夹」一致），
     * 相册簿视图则按它聚合。取不到时为空串，UI 侧回落到「未知相册」。
     */
    val bucketName: String = "",
)

/**
 * NDJSON 流的首行。
 *
 * 带 [total] 是因为相册可能上万项，浏览器要在第一批到达前把滚动条撑到大致高度。
 *
 * [todayKey] / [yesterdayKey] 让浏览器能渲染「今天 / 昨天」而**不必知道手机的时区**
 * —— 它只把每项的 `dateKey` 与这两个值比一下。这是 D65 那条「日期只算一次」的另一半：
 * 不下发它们的话，浏览器还得自己算今天是哪天，接缝就又回来了。
 */
@Serializable data class AlbumStreamHeadDto(
    val total: Int,
    val todayKey: String = "",
    val yesterdayKey: String = "",
)

/** 相册簿视图的一项。`coverId` 供缩略图接口使用，与 [AlbumItemDto.id] 同一套编码。 */
@Serializable data class AlbumBucketDto(
    val name: String,
    val count: Int,
    val coverId: String,
)

/** 403 时的机器可读原因。前端据此在「去手机授权」与其它引导间选择。 */
@Serializable data class AlbumErrorDto(val code: String)
