package com.example.flikky.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.example.flikky.MainActivity
import com.example.flikky.R
import com.example.flikky.di.ServiceLocator
import com.example.flikky.server.JoinRequests

/**
 * 手机在后台时的「请求加入」通知（v1.23 多设备，用户裁决：带「允许 / 拒绝」，锁屏时要先解锁）。
 *
 * 一次只提示最早那个请求；处理掉一个，下一个顶上来。通知里只有设备名与比对码（通知规范：只放入口，不放敏感值；
 * 比对码不是凭据，知道了也进不来 —— 不放的话，从通知上允许就没法核对，D82）。
 * 按钮交给 [JoinRequestActionReceiver]，而不是去启动服务：服务已停时点一个过期通知，不该把服务拉起来。
 */
object JoinRequestNotifier {
    const val CHANNEL_ID = "flikky_join_requests"
    const val NOTIFICATION_ID = 1002
    const val ACTION_ALLOW = "com.example.flikky.action.JOIN_ALLOW"
    const val ACTION_DENY = "com.example.flikky.action.JOIN_DENY"
    const val EXTRA_REQUEST_ID = "requestId"

    /** 会话页 / 导出页就在眼前时交给页内弹窗；同时再弹横幅通知只是噪音。 */
    fun notifiable(pending: List<JoinRequests.View>, dialogShown: Boolean): List<JoinRequests.View> =
        if (dialogShown) emptyList() else pending

    fun update(context: Context, pending: List<JoinRequests.View>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val first = pending.firstOrNull()
        if (first == null) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        ensureChannel(context, nm)
        val name = first.label ?: context.getString(R.string.device_unknown_browser)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.join_request_title, name))
            .setContentText(context.getString(R.string.join_request_notification_text, first.code))
            .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.join_request_notification_big_text, first.code)))
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(action(context, ACTION_DENY, first.id, R.string.join_request_deny, requestCode = 2))
            .addAction(action(context, ACTION_ALLOW, first.id, R.string.join_request_allow, requestCode = 1))
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun actionIntent(context: Context, action: String, requestId: String): Intent =
        Intent(context, JoinRequestActionReceiver::class.java).setAction(action).putExtra(EXTRA_REQUEST_ID, requestId)

    private fun action(context: Context, action: String, requestId: String, label: Int, requestCode: Int): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            context, requestCode, actionIntent(context, action, requestId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, R.mipmap.ic_launcher), context.getString(label), pi)
            // 锁屏状态下点「允许」要先解锁：不能让拿着锁屏手机的人把设备放进来。
            .setAuthenticationRequired(true)
            .build()
    }

    private fun ensureChannel(context: Context, nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.join_request_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.join_request_channel_description) },
        )
    }
}

/** 通知上的「允许 / 拒绝」。传输与导出共用当前队列；服务已停（队列为 null）时什么也不做。 */
class JoinRequestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(JoinRequestNotifier.EXTRA_REQUEST_ID) ?: return
        val joins = ServiceLocator.currentJoinRequests ?: return
        when (intent.action) {
            JoinRequestNotifier.ACTION_ALLOW -> joins.approve(id)
            JoinRequestNotifier.ACTION_DENY -> joins.deny(id)
        }
    }
}
