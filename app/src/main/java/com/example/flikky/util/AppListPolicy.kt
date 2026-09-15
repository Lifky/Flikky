package com.example.flikky.util

data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val sourceApkPath: String,
    val splitCount: Int,
    val apkBytes: Long,
    val isSystem: Boolean,
)

object AppListPolicy {
    fun shape(all: List<AppEntry>, includeSystem: Boolean, query: String): List<AppEntry> {
        val needle = query.trim()
        return all.asSequence()
            .filter { includeSystem || !it.isSystem }
            .filter { needle.isEmpty() || it.label.contains(needle, true) || it.packageName.contains(needle, true) }
            .sortedWith(compareBy(NAME_ORDER) { it.label })
            .toList()
    }
}
