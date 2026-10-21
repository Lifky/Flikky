package com.example.flikky.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * D78：推给浏览器的「本会话里手机已收藏的消息」。允许对端收藏（两轴都开）时是真实集合，
 * 否则为空 —— 关着时手机收藏了什么不外发。只在集合真的变了才发出。
 */
fun peerFavoritedIds(gate: Flow<Boolean>, ids: Flow<List<Long>>): Flow<List<Long>> =
    combine(gate.distinctUntilChanged(), ids) { on, list -> if (on) list else emptyList() }
        .distinctUntilChanged()

/**
 * v1.24 装机反馈：浏览器收藏面板不跟着更新。允许浏览器查看收藏（[gate]）时，收藏或合集（[content]）
 * 每变一次就发一个信号，浏览器据此重新拉列表 —— 内容本身不外发。
 * 开关刚打开时的初值不算变化：浏览器靠 `data-favorite-enabled` 自己会拉一次。
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> peerFavoritesChanged(gate: Flow<Boolean>, content: Flow<T>): Flow<Unit> =
    gate.distinctUntilChanged().flatMapLatest { on ->
        if (on) content.distinctUntilChanged().drop(1).map { } else emptyFlow()
    }
