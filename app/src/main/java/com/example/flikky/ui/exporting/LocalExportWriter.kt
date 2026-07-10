package com.example.flikky.ui.exporting

import android.content.Context
import android.net.Uri
import com.example.flikky.export.ExportSnapshot
import com.example.flikky.export.ZipExporter
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalExportWriter {
    suspend fun write(
        context: Context,
        uri: Uri,
        snapshot: ExportSnapshot,
        sessionFileResolver: (sessionId: Long, fileId: String) -> File?,
        favoriteFileResolver: (fileId: String) -> File?,
    ) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("无法写入所选位置")
        output.use {
            ZipExporter.write(
                out = it,
                snapshot = snapshot,
                fileResolver = sessionFileResolver,
                favoriteFileResolver = favoriteFileResolver,
            )
        }
    }
}
