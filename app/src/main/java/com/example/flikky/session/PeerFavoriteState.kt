package com.example.flikky.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * D78：推给浏览器的「本会话里手机已收藏的消息」。允许对端收藏（两轴都开）时是真实集合，
 * 否则为空 —— 关着时手机收藏了什么不外发。只在集合真的变了才发出。
 */
fun peerFavoritedIds(gate: Flow<Boolean>, ids: Flow<List<Long>>): Flow<List<Long>> =
    combine(gate.distinctUntilChanged(), ids) { on, list -> if (on) list else emptyList() }
        .distinctUntilChanged()
