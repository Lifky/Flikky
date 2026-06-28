package com.example.flikky.ui.components

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

/**
 * 用新版 [Clipboard]（`LocalClipboard`）复制纯文本——替代已弃用的 `LocalClipboardManager.setText`。
 * 新 API 是 suspend，这里把 [ClipData] / [ClipEntry] 样板收拢，调用方只需
 * `scope.launch { clipboard.setPlainText(text) }`。
 */
suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText("flikky", text)))
}
