package com.example.flikky.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.flikky.di.ServiceLocator

/**
 * 「请求加入」在页内的宿主（v1.23 多设备）：会话页与导出页各放一个。
 * 页面在前台时登记 [ServiceLocator.joinDialogShown]（服务据此不再发横幅通知），并一次弹一个请求。
 */
@Composable
fun JoinRequestHost() {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ServiceLocator.joinDialogShown.value = true
                Lifecycle.Event.ON_PAUSE -> ServiceLocator.joinDialogShown.value = false
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            ServiceLocator.joinDialogShown.value = false
        }
    }
    val requests by ServiceLocator.session.joinRequests.collectAsState()
    // 一次处理一个：处理掉，下一个顶上来。
    requests.firstOrNull()?.let { request ->
        JoinRequestDialog(
            request = request,
            onAllow = { ServiceLocator.currentJoinRequests?.approve(request.id) },
            onDeny = { ServiceLocator.currentJoinRequests?.deny(request.id) },
        )
    }
}
