package com.example.flikky.util

import java.security.MessageDigest

/**
 * Derives a stable, file-name-safe storage thumbnail key. Metadata is part of
 * the identity so replacing a file naturally invalidates its old thumbnail.
 */
fun thumbnailCacheKey(absolutePath: String, mtime: Long, size: Long): String {
    val identity = buildString {
        append(absolutePath)
        append('\u0000')
        append(mtime)
        append('\u0000')
        append(size)
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
