package com.example.flikky.service

import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.server.dto.FavoriteGroupDto
import com.example.flikky.server.dto.FavoriteItemDto
import com.example.flikky.server.dto.FavoritesResponseDto

/**
 * FavoriteEntity → 浏览器端 DTO 的纯映射。
 *
 * 放在 service/ 而不是 server/dto/：映射需要 import Room 实体，而项目约定
 * server/ 包不认识数据层类型（路由只接 lambda）。本函数无 Android 依赖，可在 test/ 直接跑。
 */
fun toFavoritesResponseDto(
    favorites: List<FavoriteEntity>,
    groups: List<FavoriteGroupEntity>,
): FavoritesResponseDto = FavoritesResponseDto(
    groups = groups
        .sortedBy { it.sortOrder }
        .map { FavoriteGroupDto(id = it.id, name = it.name, sortOrder = it.sortOrder) },
    items = favorites
        .sortedByDescending { it.createdAt }
        .map { row ->
            FavoriteItemDto(
                id = row.id,
                kind = row.kind,
                text = row.textContent,
                fileName = row.fileName,
                fileSize = row.fileSize,
                mime = row.fileMime,
                groupId = row.groupId,
                createdAt = row.createdAt,
            )
        },
)
