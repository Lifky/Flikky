package com.example.flikky.data

import android.content.pm.PackageManager
import com.example.flikky.util.AppEntry
import com.example.flikky.util.appEntryFrom
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppScanner(private val packageManager: PackageManager) {
    suspend fun scan(): List<AppEntry> = withContext(Dispatchers.IO) {
        packageManager.getInstalledPackages(0).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            appEntryFrom(
                info.packageName,
                runCatching { app.loadLabel(packageManager).toString() }.getOrDefault(info.packageName),
                info.versionName,
                info.longVersionCode,
                app.sourceDir,
                app.splitSourceDirs,
                app.flags,
                runCatching { File(app.sourceDir).length() }.getOrDefault(0L),
            )
        }
    }
}
