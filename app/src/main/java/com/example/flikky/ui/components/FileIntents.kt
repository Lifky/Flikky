package com.example.flikky.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.flikky.R
import java.io.File

fun sessionFile(context: Context, sessionId: Long, fileId: String): File =
    File(File(File(context.filesDir, "sessions/$sessionId"), "files"), fileId)

fun openStoredFile(
    context: Context,
    sessionId: Long,
    fileId: String,
    displayName: String,
    mime: String?,
    onMissing: () -> Unit = {},
): Boolean {
    val file = sessionFile(context, sessionId, fileId)
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

fun shareStoredFile(
    context: Context,
    sessionId: Long,
    fileId: String,
    displayName: String,
    mime: String?,
): Boolean {
    val file = sessionFile(context, sessionId, fileId)
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
