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

private const val FLAG_SYSTEM = 1
private const val FLAG_UPDATED_SYSTEM_APP = 128

fun appEntryFrom(
    packageName: String,
    label: String,
    versionName: String?,
    versionCode: Long,
    sourceDir: String?,
    splitSourceDirs: Array<String>?,
    appFlags: Int,
    apkBytes: Long,
): AppEntry? {
    val path = sourceDir?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val preinstalled = appFlags and FLAG_SYSTEM != 0
    val updated = appFlags and FLAG_UPDATED_SYSTEM_APP != 0
    return AppEntry(
        packageName, label.trim().ifEmpty { packageName }, versionName, versionCode,
        path, splitSourceDirs?.size ?: 0, apkBytes, preinstalled && !updated,
    )
}

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
