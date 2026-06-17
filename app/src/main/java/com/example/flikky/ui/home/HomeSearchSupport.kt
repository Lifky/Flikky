package com.example.flikky.ui.home

import com.example.flikky.data.db.entities.SessionEntity

/**
 * 主页 SearchBar「会话」组：按名称模糊匹配（大小写不敏感、去首尾空白）。
 * 进行中会话也可命中（用户可能想跳回继续）。空/空白 query 返回空（不展示全部，避免和会话列表重复）。
 * 顺序保持入参顺序（调用方传的是已排序的会话列表）。
 */
fun matchSessionsByName(sessions: List<SessionEntity>, query: String): List<SessionEntity> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return sessions.filter { it.name.contains(q, ignoreCase = true) }
}
