package com.example.flikky.ui.serving

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.session.Message
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

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(ui.messages) { msg ->
                when (msg) {
                    is Message.Text -> Text("${msg.origin}: ${msg.content}")
                    is Message.File -> Text("${msg.origin} 文件: ${msg.name} (${msg.sizeBytes} B)")
                }
                Spacer(Modifier.height(4.dp))
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
