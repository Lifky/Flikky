package com.example.flikky.ui.components

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.flikky.R
import com.example.flikky.di.ServiceLocator
import java.io.File

fun sessionFile(sessionId: Long, fileId: String): File =
    ServiceLocator.fileStore.messageFile(sessionId, fileId)

fun openStoredFile(
    context: Context,
    sessionId: Long,
    fileId: String,
    displayName: String,
    mime: String?,
    onMissing: () -> Unit = {},
): Boolean {
    val file = sessionFile(sessionId, fileId)
    if (!file.exists()) {
        Toast.makeText(context, R.string.file_missing, Toast.LENGTH_SHORT).show()
        onMissing()
        return false
    }
    val uri = try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
            displayName,
        )
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, R.string.file_provider_unavailable, Toast.LENGTH_SHORT).show()
        return false
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime?.ifBlank { null } ?: "application/octet-stream")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.file_open_chooser)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.file_no_handler, Toast.LENGTH_SHORT).show()
        false
    }
}

/**
 * Saves a local image or video into the system gallery. Call from an IO dispatcher.
 * Pending MediaStore rows are published only after the full payload is written.
 */
fun saveToGallery(
    context: Context,
    file: File,
    displayName: String,
    mime: String,
): Boolean {
    if (!file.exists()) return false
    val isImage = mime.startsWith("image/")
    val isVideo = mime.startsWith("video/")
    if (!isImage && !isVideo) return false

    val collection = if (isImage) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
    val relativePath = if (isImage) {
        Environment.DIRECTORY_PICTURES + "/Flikky"
    } else {
        Environment.DIRECTORY_MOVIES + "/Flikky"
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
    return runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("openOutputStream returned null")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    }.getOrElse {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
}

/**
 * FileProvider + ACTION_SEND sharing shared by session and favorite file stores.
 * Callers resolve the file from the appropriate storage root.
 */
fun shareFile(context: Context, file: File, displayName: String, mime: String?): Boolean {
    if (!file.exists()) {
        Toast.makeText(context, R.string.file_missing, Toast.LENGTH_SHORT).show()
        return false
    }
    val uri = try {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
            displayName,
        )
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, R.string.file_provider_unavailable, Toast.LENGTH_SHORT).show()
        return false
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime?.ifBlank { null } ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.files_action_share)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.file_no_handler, Toast.LENGTH_SHORT).show()
        false
    }
}

fun shareStoredFile(
    context: Context,
    sessionId: Long,
    fileId: String,
    displayName: String,
    mime: String?,
): Boolean = shareFile(context, sessionFile(sessionId, fileId), displayName, mime)
