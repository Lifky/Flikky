package com.example.flikky.server.dto

import kotlinx.serialization.Serializable

@Serializable data class AlbumItemDto(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long = 0,
    val takenAtMs: Long = 0,
    val durationMs: Long = 0,
)
@Serializable data class AlbumStreamHeadDto(val total: Int)
@Serializable data class AlbumErrorDto(val code: String)
