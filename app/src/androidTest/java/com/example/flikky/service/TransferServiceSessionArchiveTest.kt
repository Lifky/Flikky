package com.example.flikky.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.di.ServiceLocator
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.util.IdGen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TransferServiceSessionArchiveTest {
    private lateinit var ctx: Context

    @Before fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        ServiceLocator.init(ctx)
    }

    @After fun cleanup() = runBlocking {
        val all = ServiceLocator.database.sessionDao().listUnfinished() +
                  ServiceLocator.database.sessionDao().nonPinnedOldestFirst()
        all.forEach { ServiceLocator.database.sessionDao().delete(it) }
    }

    @Test fun startStop_persists_one_session_with_appended_message() = runBlocking {
        val latch = CountDownLatch(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { latch.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        val intent = Intent(ctx, TransferService::class.java).apply {
            action = TransferService.ACTION_START
        }
        ctx.startForegroundService(intent)
        ctx.bindService(Intent(ctx, TransferService::class.java), conn, Context.BIND_AUTO_CREATE)
        assertTrue(latch.await(10, TimeUnit.SECONDS))

        var sid: Long? = null
        repeat(20) {
            sid = ServiceLocator.session.snapshot.value.currentSessionId
            if (sid != null) return@repeat
            Thread.sleep(200)
        }
        requireNotNull(sid)

        ServiceLocator.repository.appendMessage(sid!!, Message.Text(
            id = IdGen.newMessageId(), origin = Origin.PHONE,
            timestamp = System.currentTimeMillis(), content = "hello",
        ))

        ctx.startService(Intent(ctx, TransferService::class.java).apply {
            action = TransferService.ACTION_STOP
        })
        ctx.unbindService(conn)
        Thread.sleep(2000)

        val row = ServiceLocator.database.sessionDao().getById(sid!!)!!
        assertTrue(row.endedAt != null)
        assertTrue(row.messageCount >= 1)
        assertTrue(row.previewText == "hello")
    }
}
