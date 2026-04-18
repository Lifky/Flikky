package com.example.flikky.ui.serving

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.ui.components.StatusBar

@Composable
fun ServingScreen(
    onStopped: () -> Unit,
    viewModel: ServingViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var draft by remember { mutableStateOf("") }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.offerFile(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("在电脑浏览器打开：", style = MaterialTheme.typography.bodyMedium)
            Text(ui.url, style = MaterialTheme.typography.headlineSmall)
            Text("PIN  ${ui.pin}", style = MaterialTheme.typography.displaySmall)
            Text(
                if (ui.clientConnected) "已连接" else "等待连接…",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(ui.messages) { msg ->
                MessageBubble(
                    msg = msg,
                    onClick = { if (msg is Message.File) viewModel.openFile(msg) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("输入消息") },
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { pickFile.launch("*/*") }) { Text("附件") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    viewModel.sendText(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && ui.clientConnected,
            ) { Text("发送") }
        }

        StatusBar(
            uptimeSeconds = ui.uptimeSeconds,
            fileCount = ui.fileCount,
            bytesPerSecond = ui.bytesPerSecond,
        )
        TextButton(
            onClick = { viewModel.stopService(); onStopped() },
            modifier = Modifier.padding(16.dp),
        ) { Text("停止服务") }
    }
}

@Composable
private fun MessageBubble(msg: Message, onClick: () -> Unit) {
    val mine = msg.origin == Origin.PHONE
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
    )
    val bg = if (mine) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(bg)
                .clickable(enabled = msg is Message.File) { onClick() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when (msg) {
                is Message.Text -> Text(
                    text = msg.content,
                    color = fg,
                    style = MaterialTheme.typography.bodyLarge,
                )
                is Message.File -> FileBubbleContent(msg = msg, fg = fg, mine = mine)
            }
        }
    }
}

@Composable
private fun FileBubbleContent(msg: Message.File, fg: Color, mine: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "📄", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = msg.name,
                color = fg,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append(formatSize(msg.sizeBytes))
                    if (!mine && msg.status == Message.File.Status.COMPLETED) append("  ·  点击打开")
                    else if (mine) append("  ·  已发送")
                },
                color = fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes >= 1024L * 1024L) return "%.1f MB".format(bytes / 1048576.0)
    if (bytes >= 1024L) return "%.1f KB".format(bytes / 1024.0)
    return "$bytes B"
}
