package com.example.flikky.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        JoinRequestNotifier.update(context, listOf(JoinRequests.View("r1", "Chrome · Windows", 0L)))
        val n = shadowOf(nm).getNotification(JoinRequestNotifier.NOTIFICATION_ID)
        val title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
        assertTrue(title, title.contains("Chrome · Windows"))
        assertEquals(2, n.actions.size)
        assertTrue("allowing from the lock screen must ask to unlock first", n.actions.all { it.isAuthenticationRequired })
    }

    @Test fun `no waiting request clears the notification`() {
        JoinRequestNotifier.update(context, listOf(JoinRequests.View("r1", null, 0L)))
        JoinRequestNotifier.update(context, emptyList())
        assertNull(shadowOf(nm).getNotification(JoinRequestNotifier.NOTIFICATION_ID))
    }

    @Test fun `the buttons carry only the public request id`() {
        val intent = JoinRequestNotifier.actionIntent(context, JoinRequestNotifier.ACTION_ALLOW, "r1")
        assertEquals("r1", intent.getStringExtra(JoinRequestNotifier.EXTRA_REQUEST_ID))
        assertEquals(JoinRequestActionReceiver::class.java.name, intent.component?.className)
    }
}
