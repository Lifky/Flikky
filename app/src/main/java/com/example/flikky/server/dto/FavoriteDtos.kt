package com.example.flikky.server.dto

import kotlinx.serialization.Serializable

/**
 * v1.19.0 浏览器端收藏 tab 的只读响应契约。
 *
 * 刻意不含的字段：depot `fileId`（落盘内部 id）、`sourceSessionId` / `sourceMessageId`（内部主键）、
 * `origin`（导入来源）。浏览器按收藏行 id 取文件即可，少暴露一个 id 就少一条被拿来当授权凭据的路径
 * ——安全红线明确「文件 ID 不能代替授权」。
 */
@Serializable
data class FavoriteItemDto(
    val id: Long,
    /** "TEXT" 或 "FILE"，逐字沿用 FavoriteEntity.kind */
    val kind: String,
    val text: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mime: String? = null,
    val groupId: Long? = null,
    val createdAt: Long,
)

@Serializable
data class FavoriteGroupDto(
    val id: Long,
    val name: String,
    val sortOrder: Int,
)

@Serializable
data class FavoritesResponseDto(
    val groups: List<FavoriteGroupDto>,
    val items: List<FavoriteItemDto>,
)
