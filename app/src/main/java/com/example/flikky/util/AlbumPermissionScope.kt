package com.example.flikky.util

enum class AlbumAccess { None, Partial, Full }

fun albumAccess(manageAllFiles: Boolean, readImages: Boolean, readVideo: Boolean, userSelected: Boolean): AlbumAccess = when {
    manageAllFiles -> AlbumAccess.Full
    readImages && readVideo -> AlbumAccess.Full
    readImages || readVideo || userSelected -> AlbumAccess.Partial
    else -> AlbumAccess.None
}
