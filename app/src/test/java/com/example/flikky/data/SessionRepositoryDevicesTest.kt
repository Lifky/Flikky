package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import com.example.flikky.session.PeerDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryDevicesTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: FlikkyDatabase
    private lateinit var repo: SessionRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java).allowMainThreadQueries().build()
        repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = SessionFileStore(filesDir = tmp.root),
            now = { 1_000L },
            sessionDeviceDao = db.sessionDeviceDao(),
        )
    }

    @After fun tearDown() { db.close() }

    private fun device(name: String = "") = PeerDevice(
        deviceId = "dev-1", name = name, defaultLabel = "Chrome · Windows",
        avatarKey = "icon:star", connectedAt = 500L, visible = true,
    )

    @Test fun `a device snapshot is stored per session and updated in place`() = runTest {
        val sid = repo.beginSession("s", startedAt = 1L)
        repo.upsertSessionDevice(sid, device())
        repo.upsertSessionDevice(sid, device(name = "张三的笔记本"))
        val stored = repo.observeSessionDevices(sid).first().single()
        assertEquals("张三的笔记本", stored.name)
        assertEquals(500L, stored.connectedAt)
        assertEquals("Chrome · Windows", stored.defaultLabel)
    }

    @Test fun `deleting the session drops its devices`() = runTest {
        val sid = repo.beginSession("s", startedAt = 1L)
        repo.upsertSessionDevice(sid, device())
        repo.deleteSession(sid)
        assertTrue(db.sessionDeviceDao().listBySession(sid).isEmpty())
    }

    @Test fun `a message keeps its sender device`() = runTest {
        val sid = repo.beginSession("s", startedAt = 1L)
        repo.appendMessage(sid, Message.Text(id = 42L, origin = Origin.BROWSER, timestamp = 2L, content = "hi", senderDeviceId = "dev-1"))
        assertEquals("dev-1", repo.observeMessages(sid).first().single().senderDeviceId)
    }

    @Test fun `without a device dao the repository reports no devices`() = runTest {
        val bare = SessionRepository(
            sessionDao = db.sessionDao(), messageDao = db.messageDao(), groupDao = db.groupDao(),
            fileStore = SessionFileStore(filesDir = tmp.root), now = { 1_000L },
        )
        assertTrue(bare.observeSessionDevices(1L).first().isEmpty())
    }
}
