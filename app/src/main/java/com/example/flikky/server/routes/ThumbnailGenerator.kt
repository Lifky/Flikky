package com.example.flikky.server.routes

import java.io.File

/**
 * Thumbnail generator boundary. The server package stays free of Android framework types;
 * the android.graphics implementation is injected from the data layer.
 */
fun interface ThumbnailGenerator {
    fun generate(source: File, mime: String, target: File): Boolean
}
