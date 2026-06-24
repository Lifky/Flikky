package com.example.flikky.ui.favorites

import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.data.db.entities.GroupEntity

internal fun FavoriteGroupEntity.toGroupEntity(): GroupEntity =
    GroupEntity(id = id, name = name, sortOrder = sortOrder, createdAt = createdAt)

internal fun List<FavoriteGroupEntity>.toGroupEntities(): List<GroupEntity> =
    map { it.toGroupEntity() }

internal fun GroupEntity.toFavoriteGroup(groups: List<FavoriteGroupEntity>): FavoriteGroupEntity? =
    groups.firstOrNull { it.id == id }
