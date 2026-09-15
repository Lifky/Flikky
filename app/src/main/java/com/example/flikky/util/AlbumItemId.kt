package com.example.flikky.util

enum class AlbumMediaKind { IMAGE, VIDEO }

data class AlbumItemId(val kind: AlbumMediaKind, val mediaStoreId: Long) {
    fun format(): String = "${if (kind == AlbumMediaKind.IMAGE) "img" else "vid"}:$mediaStoreId"

    companion object {
        fun parse(raw: String): AlbumItemId? {
            val cut = raw.indexOf(':')
            if (cut <= 0 || cut == raw.lastIndex || raw.indexOf(':', cut + 1) >= 0) return null
            val kind = when (raw.substring(0, cut)) { "img" -> AlbumMediaKind.IMAGE; "vid" -> AlbumMediaKind.VIDEO; else -> return null }
            val digits = raw.substring(cut + 1)
            if (digits.any { it !in '0'..'9' } || (digits.length > 1 && digits[0] == '0')) return null
            val value = digits.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return AlbumItemId(kind, value)
        }
    }
}
