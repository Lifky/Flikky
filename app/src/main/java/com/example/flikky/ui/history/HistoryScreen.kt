package com.example.flikky.ui.history

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.MessageBubble
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    sessionId: Long,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(
            app = ctx.applicationContext as android.app.Application,
            sessionId = sessionId,
        ),
    )
    val session by viewModel.session.collectAsState(initial = null)
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val inProgress = session?.endedAt == null && session != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: "会话") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }, enabled = !inProgress) {
                        Text("⋮")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        val pinned = session?.pinned == true
                        DropdownMenuItem(
                            text = { Text(if (pinned) "取消置顶" else "置顶") },
                            onClick = { menuExpanded = false; viewModel.setPinned(!pinned) },
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = { menuExpanded = false; showRename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = { menuExpanded = false; showDelete = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(
                    msg = msg,
                    onClick = {
                        if (msg is Message.File) openFile(ctx, sessionId, msg)
                    },
                )
            }
        }
    }

    if (showRename) {
        var draft by remember { mutableStateOf(session?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { showRename = false; viewModel.rename(draft) }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("取消") }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除会话") },
            text = { Text("将删除此会话的所有消息与文件。该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false; viewModel.delete(); onBack()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            },
        )
    }
}

private fun openFile(ctx: Context, sessionId: Long, msg: Message.File) {
    if (msg.origin != Origin.BROWSER) return
    val f = File(File(File(ctx.filesDir, "sessions/$sessionId"), "files"), msg.fileId)
    if (!f.exists()) {
        Toast.makeText(ctx, "文件不存在", Toast.LENGTH_SHORT).show(); return
    }
    val authority = "${ctx.packageName}.fileprovider"
    val uri = FileProvider.getUriForFile(ctx, authority, f, msg.name)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, msg.mime.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        ctx.startActivity(Intent.createChooser(intent, "打开文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "没有可以打开此类型文件的应用", Toast.LENGTH_SHORT).show()
    }
}
