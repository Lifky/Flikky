package com.example.flikky.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.example.flikky.data.settings.SettingsRepository
import com.example.flikky.util.AlbumAccess
import com.example.flikky.util.albumAccess

/**
 * 对端「文件」「相册」通道的系统前置条件现值。
 *
 * App 端 UI、服务端 peer-info 与 fail-closed 守卫读的是**同一份**判据 —— 三处各写一遍，
 * 迟早有一处漏掉 `READ_MEDIA_VISUAL_USER_SELECTED` 之类的分支，两端对「能不能看」
 * 各执一词。
 */
fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

fun Context.currentAlbumAccess(): AlbumAccess {
    fun granted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return albumAccess(
        manageAllFiles = hasAllFilesAccess(),
        readImages = granted(android.Manifest.permission.READ_MEDIA_IMAGES),
        readVideo = granted(android.Manifest.permission.READ_MEDIA_VIDEO),
        userSelected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
    )
}

/** 用当前系统权限态执行 [SettingsRepository.revokeUnavailablePeerGates]（D76）。 */
suspend fun SettingsRepository.revokeUnavailablePeerGates(context: Context) =
    revokeUnavailablePeerGates(
        storageAvailable = hasAllFilesAccess(),
        albumAvailable = context.currentAlbumAccess() != AlbumAccess.None,
    )
