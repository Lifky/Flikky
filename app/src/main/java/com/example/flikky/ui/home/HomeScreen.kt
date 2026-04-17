package com.example.flikky.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    onStarted: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current

    fun startAndNavigate() {
        viewModel.startService()
        onStarted()
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "通知权限被拒：服务仍运行，但通知栏不会显示状态", Toast.LENGTH_LONG).show()
        }
        startAndNavigate()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Flikky", style = MaterialTheme.typography.displayMedium)
        Text(
            "局域网文件与消息传输",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) startAndNavigate()
                    else notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startAndNavigate()
                }
            },
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text("启动服务")
        }
    }
}
