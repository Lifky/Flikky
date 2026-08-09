package com.example.flikky.util

/** Parses release tags like "v1.17.0" and compares x.y.z numerically. Pure Kotlin. */
object UpdateVersion {

    /** Returns [major, minor, patch] or null for an invalid v-optional x.y.z tag. */
    fun parse(raw: String?): List<Int>? {
        val trimmed = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        val parts = trimmed.split('.')
        if (parts.size != 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it < 0 }) return null
        return nums
    }

    /** True only when both sides parse and [remoteTag] is strictly greater. */
    fun isNewer(remoteTag: String?, currentName: String?): Boolean {
        val remote = parse(remoteTag) ?: return false
        val current = parse(currentName) ?: return false
        for (i in 0..2) {
            if (remote[i] != current[i]) return remote[i] > current[i]
        }
        return false
    }
}
