package com.example.flikky.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.di.ServiceLocator
import com.example.flikky.server.JoinRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 手机在后台时的加入请求通知（v1.23 多设备，用户裁决：带「允许 / 拒绝」按钮，锁屏时要解锁才生效）。
 * 通知只放设备名，不放任何凭据（通知规范：只放入口，不放敏感值）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JoinRequestNotifierTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    @Test fun `a waiting request shows a notification with allow and refuse`() {
        JoinRequestNotifier.update(context, listOf(JoinRequests.View("r1", "Chrome · Windows", 0L, code = "4821")))
        val n = shadowOf(nm).getNotification(JoinRequestNotifier.NOTIFICATION_ID)
        val title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
        assertTrue(title, title.contains("Chrome · Windows"))
        assertEquals(2, n.actions.size)
        assertTrue("allowing from the lock screen must ask to unlock first", n.actions.all { it.isAuthenticationRequired })
    }

    @Test fun `no waiting request clears the notification`() {
        JoinRequestNotifier.update(context, listOf(JoinRequests.View("r1", null, 0L, code = "4821")))
        JoinRequestNotifier.update(context, emptyList())
        assertNull(shadowOf(nm).getNotification(JoinRequestNotifier.NOTIFICATION_ID))
    }

    @Test fun `the buttons carry only the public request id`() {
        val intent = JoinRequestNotifier.actionIntent(context, JoinRequestNotifier.ACTION_ALLOW, "r1")
        assertEquals("r1", intent.getStringExtra(JoinRequestNotifier.EXTRA_REQUEST_ID))
        assertEquals(JoinRequestActionReceiver::class.java.name, intent.component?.className)
    }

    // 会话页就在眼前时，页内弹窗已经够了；同时再弹一条横幅通知只是噪音。
    @Test fun `the notification is left to the in-app dialog while the serving screen is visible`() {
        val pending = listOf(JoinRequests.View("r1", null, 0L, code = "4821"))
        assertEquals(emptyList<JoinRequests.View>(), JoinRequestNotifier.notifiable(pending, dialogShown = true))
        assertEquals(pending, JoinRequestNotifier.notifiable(pending, dialogShown = false))
    }

    // 导出模式没有 TransferController：通知上的「允许 / 拒绝」直接作用在当前队列上，两种模式同一条路。
    @Test fun `the notification buttons act on the current queue in either mode`() {
        val joins = JoinRequests()
        val a = joins.create("UA")!!
        val b = joins.create("UA")!!
        ServiceLocator.currentJoinRequests = joins
        try {
            val receiver = JoinRequestActionReceiver()
            receiver.onReceive(context, JoinRequestNotifier.actionIntent(context, JoinRequestNotifier.ACTION_ALLOW, a.request.id))
            receiver.onReceive(context, JoinRequestNotifier.actionIntent(context, JoinRequestNotifier.ACTION_DENY, b.request.id))
            assertEquals(JoinRequests.Status.Approved, joins.status(a.secret))
            assertEquals(JoinRequests.Status.Denied, joins.status(b.secret))
        } finally {
            ServiceLocator.currentJoinRequests = null
        }
    }
}
