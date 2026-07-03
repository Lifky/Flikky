package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.session.Message
import com.example.flikky.session.Origin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FavoritesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: FlikkyDatabase
    private lateinit var sessionFileStore: SessionFileStore
    private lateinit var favoriteFileStore: FavoriteFileStore
    private lateinit var repo: FavoritesRepository

    private var clock = 1_000L

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionFileStore = SessionFileStore(filesDir = tmp.root)
        favoriteFileStore = FavoriteFileStore(filesDir = tmp.root)
        repo = FavoritesRepository(
            favoriteDao = db.favoriteDao(),
            favoriteGroupDao = db.favoriteGroupDao(),
            sessionFileStore = sessionFileStore,
            favoriteFileStore = favoriteFileStore,
            now = { clock },
            depotIdFactory = { "depot-$clock" },
            localSourceMessageIdFactory = { -clock },
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun favoriteText_persists_snapshot_and_favorited_ids_are_session_scoped() = runTest {
        clock = 10L
        val favoriteId = repo.favoriteText(
            sid = 1L,
            sessionName = "chat A",
            msg = Message.Text(id = 5L, origin = Origin.PHONE, timestamp = 7L, content = "remember this"),
            groupId = null,
        )
        repo.favoriteText(
            sid = 2L,
            sessionName = "chat B",
            msg = Message.Text(id = 5L, origin = Origin.BROWSER, timestamp = 8L, content = "same message id"),
            groupId = null,
        )

        val row = db.favoriteDao().getById(favoriteId)!!
        assertEquals("TEXT", row.kind)
        assertEquals("remember this", row.textContent)
        assertEquals("chat A", row.sourceSessionName)
        assertEquals("PHONE", row.origin)
        assertEquals(10L, row.createdAt)

        repo.observeFavoritedIds(1L).test {
            assertEquals(listOf(5L), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repo.isFavorited(1L, 5L))
        assertTrue(repo.isFavorited(2L, 5L))
        assertFalse(repo.isFavorited(3L, 5L))
    }

    @Test fun favoriteFile_copies_bytes_to_independent_depot_file() = runTest {
        val source = sessionFileStore.archiveFromStream(
            sessionId = 9L,
            fileId = "source-file",
            source = "payload".byteInputStream(),
        )
        clock = 20L

        val favoriteId = repo.favoriteFile(
            sid = 9L,
            sessionName = "files",
            msg = Message.File(
                id = 99L,
                origin = Origin.BROWSER,
                timestamp = 1L,
                fileId = "source-file",
                name = "report.pdf",
                sizeBytes = 7L,
                mime = "application/pdf",
                status = Message.File.Status.COMPLETED,
            ),
            groupId = 3L,
        )

        val row = db.favoriteDao().getById(favoriteId)!!
        assertEquals("FILE", row.kind)
        assertEquals("depot-20", row.fileId)
        assertEquals("report.pdf", row.fileName)
        assertEquals(7L, row.fileSize)
        assertEquals("application/pdf", row.fileMime)
        assertEquals(3L, row.groupId)
        assertArrayEquals("payload".toByteArray(), favoriteFileStore.resolve("depot-20").readBytes())

        source.writeText("changed")
        assertArrayEquals("payload".toByteArray(), favoriteFileStore.resolve("depot-20").readBytes())
        sessionFileStore.deleteSessionDir(9L)
        assertTrue(favoriteFileStore.resolve("depot-20").exists())
    }

    @Test fun favoriteFile_fails_when_source_file_is_missing() = runTest {
        val result = runCatching {
            repo.favoriteFile(
                sid = 1L,
                sessionName = "missing",
                msg = Message.File(
                    id = 2L,
                    origin = Origin.PHONE,
                    timestamp = 1L,
                    fileId = "missing-file",
                    name = "lost.bin",
                    sizeBytes = 1L,
                    mime = "application/octet-stream",
                    status = Message.File.Status.COMPLETED,
                ),
                groupId = null,
            )
        }

        assertTrue(result.isFailure)
        assertTrue(repo.observeFavorites().first().isEmpty())
    }

    @Test fun addLocalText_persists_standalone_text_snapshot() = runTest {
        clock = 60L

        val favoriteId = repo.addLocalText("  local note  ", groupId = 4L)

        val row = db.favoriteDao().getById(favoriteId)!!
        assertEquals("TEXT", row.kind)
        assertEquals("local note", row.textContent)
        assertEquals(FavoritesRepository.LOCAL_SOURCE_SESSION_ID, row.sourceSessionId)
        assertEquals(-60L, row.sourceMessageId)
        assertEquals(4L, row.groupId)
        assertEquals(60L, row.createdAt)
        assertEquals("本地添加", row.sourceSessionName)
        assertEquals("PHONE", row.origin)
    }

    @Test fun addLocalFile_copies_stream_to_independent_depot_file() = runTest {
        clock = 70L

        val favoriteId = repo.addLocalFile(
            name = "local.txt",
            sizeBytes = null,
            mime = "text/plain",
            groupId = null,
            source = "payload".byteInputStream(),
        )

        val row = db.favoriteDao().getById(favoriteId)!!
        assertEquals("FILE", row.kind)
        assertEquals(FavoritesRepository.LOCAL_SOURCE_SESSION_ID, row.sourceSessionId)
        assertEquals(-70L, row.sourceMessageId)
        assertEquals("depot-70", row.fileId)
        assertEquals("local.txt", row.fileName)
        assertEquals(7L, row.fileSize)
        assertEquals("text/plain", row.fileMime)
        assertEquals("本地添加", row.sourceSessionName)
        assertArrayEquals("payload".toByteArray(), favoriteFileStore.resolve("depot-70").readBytes())
    }

    @Test fun unfavoriteBySource_deletes_row_and_depot_file_idempotently() = runTest {
        sessionFileStore.archiveFromStream(1L, "source-file", "payload".byteInputStream())
        clock = 30L
        repo.favoriteFile(
            sid = 1L,
            sessionName = "files",
            msg = Message.File(
                id = 3L,
                origin = Origin.PHONE,
                timestamp = 1L,
                fileId = "source-file",
                name = "a.bin",
                sizeBytes = 7L,
                mime = "application/octet-stream",
                status = Message.File.Status.COMPLETED,
            ),
            groupId = null,
        )

        assertTrue(favoriteFileStore.resolve("depot-30").exists())

        repo.unfavoriteBySource(1L, 3L)
        repo.unfavoriteBySource(1L, 3L)

        assertFalse(repo.isFavorited(1L, 3L))
        assertTrue(!favoriteFileStore.resolve("depot-30").exists())
    }

    @Test fun deleteGroup_rehomes_favorites_and_restore_rebinds_members() = runTest {
        val groupId = repo.createGroup(" Ammo ")
        val first = repo.favoriteText(1L, "chat", Message.Text(1L, Origin.PHONE, 1L, "one"), groupId)
        val second = repo.favoriteText(1L, "chat", Message.Text(2L, Origin.PHONE, 2L, "two"), groupId)

        val deleted = repo.deleteGroup(groupId)!!

        assertEquals("Ammo", deleted.first.name)
        assertEquals(listOf(first, second), deleted.second)
        assertNull(db.favoriteGroupDao().getById(groupId))
        assertNull(db.favoriteDao().getById(first)!!.groupId)
        assertNull(db.favoriteDao().getById(second)!!.groupId)

        val restored = repo.restoreGroup(deleted.first, deleted.second)

        assertNotEquals(groupId, restored)
        assertEquals(restored, db.favoriteDao().getById(first)!!.groupId)
        assertEquals(restored, db.favoriteDao().getById(second)!!.groupId)
    }

    @Test fun deleteFavorite_deleteFavorites_move_reorder_and_search() = runTest {
        val work = repo.createGroup("Work")
        val personal = repo.createGroup("Personal")
        val text = repo.favoriteText(1L, "chat", Message.Text(1L, Origin.PHONE, 1L, "Alpha Note"), null)
        sessionFileStore.archiveFromStream(1L, "source-file", "payload".byteInputStream())
        clock = 40L
        val file = repo.favoriteFile(
            sid = 1L,
            sessionName = "chat",
            msg = Message.File(
                id = 2L,
                origin = Origin.PHONE,
                timestamp = 2L,
                fileId = "source-file",
                name = "Beta.pdf",
                sizeBytes = 7L,
                mime = "application/pdf",
                status = Message.File.Status.COMPLETED,
            ),
            groupId = work,
        )

        repo.moveFavoritesToGroup(listOf(text, file), personal)
        assertEquals(listOf(text, file), db.favoriteDao().memberIds(personal))

        repo.reorderGroups(listOf(personal, work))
        assertEquals(listOf(personal, work), repo.observeGroups().first().map { it.id })

        assertEquals(listOf(text), repo.search(repo.observeFavorites().first(), "alpha").map { it.id })
        assertEquals(listOf(file), repo.search(repo.observeFavorites().first(), "BETA").map { it.id })
        assertEquals(repo.observeFavorites().first().map { it.id }, repo.search(repo.observeFavorites().first(), "").map { it.id })

        repo.deleteFavorite(file)
        assertTrue(!favoriteFileStore.resolve("depot-40").exists())

        repo.deleteFavorites(listOf(text))
        assertTrue(repo.observeFavorites().first().isEmpty())
    }

    @Test fun deleteSourceSession_does_not_touch_favorite_snapshot_or_depot_file() = runTest {
        val sessionRepo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = sessionFileStore,
            now = { clock },
        )
        val sid = sessionRepo.beginSession("source", startedAt = 1L)
        sessionRepo.appendMessage(sid, Message.File(
            id = 10L,
            origin = Origin.PHONE,
            timestamp = 2L,
            fileId = "source-file",
            name = "keep.bin",
            sizeBytes = 7L,
            mime = "application/octet-stream",
            status = Message.File.Status.COMPLETED,
        ))
        sessionFileStore.archiveFromStream(sid, "source-file", "payload".byteInputStream())
        clock = 50L
        repo.favoriteFile(
            sid = sid,
            sessionName = "source",
            msg = Message.File(
                id = 10L,
                origin = Origin.PHONE,
                timestamp = 2L,
                fileId = "source-file",
                name = "keep.bin",
                sizeBytes = 7L,
                mime = "application/octet-stream",
                status = Message.File.Status.COMPLETED,
            ),
            groupId = null,
        )

        sessionRepo.deleteSession(sid)

        assertEquals(1, repo.observeFavorites().first().size)
        assertTrue(favoriteFileStore.resolve("depot-50").exists())
        assertArrayEquals("payload".toByteArray(), favoriteFileStore.resolve("depot-50").readBytes())
    }
}
