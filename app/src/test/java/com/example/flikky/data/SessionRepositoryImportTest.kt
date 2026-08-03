package com.example.flikky.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.flikky.data.db.FlikkyDatabase
import com.example.flikky.export.MessageDto
import com.example.flikky.export.SessionDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * v1.4.0 B8 回归保护：SessionRepository.importSessions 的正常导入、重复检测、
 * FIFO sweep、文件提取、聚合字段。Robolectric + in-memory Room DB。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionRepositoryImportTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: FlikkyDatabase
    private lateinit var repo: SessionRepository
    private lateinit var store: SessionFileStore

    private var zipSeq = 0

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlikkyDatabase::class.java)
            .allowMainThreadQueries().build()
        store = SessionFileStore(filesDir = tmp.root)
        repo = SessionRepository(
            sessionDao = db.sessionDao(),
            messageDao = db.messageDao(),
            groupDao = db.groupDao(),
            fileStore = store,
            now = { 1_000L },
            retainLimitProvider = { 20 },
        )
    }

    @After fun tearDown() { db.close() }

    /** A session to encode into the test zip. */
    private data class TestSession(
        val id: Long,
        val name: String,
        val startedAt: Long,
        val pinned: Boolean = false,
        val texts: List<String> = emptyList(),
        val files: List<Pair<String, ByteArray>> = emptyList(), // name → content
        val deletedFiles: List<String> = emptyList(),
    )

    private fun buildZip(
        sessions: List<TestSession>,
        version: String = "1.4",
        encodeDefaults: Boolean = true,
    ): File {
        val archiveJson = Json { prettyPrint = true; this.encodeDefaults = encodeDefaults }
        val f = tmp.newFile("import-${zipSeq++}.zip")
        ZipOutputStream(f.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write("Flikky Export\nVersion: $version\n".toByteArray())
            zip.closeEntry()

            for (s in sessions) {
                val dir = "sessions/${s.id}_${s.name}/"
                val messages = mutableListOf<MessageDto>()
                var ts = s.startedAt
                for (t in s.texts) {
                    messages.add(MessageDto.TextDto(ts = ts++, origin = "PHONE", content = t))
                }
                for ((fname, content) in s.files) {
                    messages.add(MessageDto.FileDto(
                        ts = ts++, origin = "BROWSER", fileId = "orig-$fname",
                        name = fname, mime = "application/octet-stream",
                        sizeBytes = content.size.toLong(), relativePath = "files/$fname",
                    ))
                }
                for (fname in s.deletedFiles) {
                    messages.add(MessageDto.FileDto(
                        ts = ts++, origin = "BROWSER", fileId = "orig-$fname",
                        name = fname, mime = "application/octet-stream",
                        sizeBytes = 10L, relativePath = "files/$fname", status = "DELETED",
                    ))
                }
                val dto = SessionDto(
                    sessionId = s.id, name = s.name, startedAt = s.startedAt,
                    endedAt = s.startedAt + 100, pinned = s.pinned, messages = messages,
                )
                zip.putNextEntry(ZipEntry("${dir}messages.json"))
                zip.write(archiveJson.encodeToString(SessionDto.serializer(), dto).toByteArray())
                zip.closeEntry()

                for ((fname, content) in s.files) {
                    zip.putNextEntry(ZipEntry("${dir}files/$fname"))
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }
        return f
    }

    @Test fun normal_import_creates_session_messages_and_extracts_files() = runTest {
        val content = "file body".toByteArray()
        val zip = buildZip(listOf(
            TestSession(
                id = 1, name = "Imported", startedAt = 500L,
                texts = listOf("hello", "world"),
                files = listOf("doc.txt" to content),
            ),
        ))

        val result = repo.importSessions(zip)

        assertEquals(1, result.imported.size)
        assertEquals(0, result.skipped.size)
        assertEquals(0, result.errors.size)

        val imported = result.imported.single()
        assertEquals(1L, imported.originalId)
        assertEquals("Imported", imported.originalName)
        assertEquals(3, imported.messageCount)   // 2 text + 1 file
        assertEquals(1, imported.fileCount)

        // Session persisted with correct aggregates.
        val row = db.sessionDao().getById(imported.newId)!!
        assertEquals("Imported", row.name)
        assertEquals(500L, row.startedAt)
        assertEquals(3, row.messageCount)
        assertEquals(1, row.fileCount)
        assertEquals(content.size.toLong(), row.totalBytes)

        // Messages persisted with fresh ids under the new session.
        val messages = db.messageDao().listBySession(imported.newId)
        assertEquals(3, messages.size)
        val fileMsg = messages.single { it.kind == "FILE" }
        // File extracted to disk under the NEW fileId (not the original "orig-doc.txt").
        val onDisk = File(store.fileDir(imported.newId), fileMsg.fileId!!)
        assertTrue("extracted file should exist", onDisk.exists())
        assertEquals("file body", onDisk.readText())
        assertEquals("COMPLETED", fileMsg.fileStatus)
    }

    @Test fun deleted_file_import_keeps_message_without_blob_and_old_archive_defaults_completed() = runTest {
        val deletedZip = buildZip(listOf(
            TestSession(
                id = 1L,
                name = "Deleted",
                startedAt = 500L,
                deletedFiles = listOf("gone.bin"),
            ),
        ))

        val deletedResult = repo.importSessions(deletedZip)

        val deletedSessionId = deletedResult.imported.single().newId
        val deletedMessage = db.messageDao().listBySession(deletedSessionId).single()
        assertEquals("DELETED", deletedMessage.fileStatus)
        assertTrue(!File(store.fileDir(deletedSessionId), deletedMessage.fileId!!).exists())

        val legacyContent = "legacy".toByteArray()
        val legacyZip = buildZip(
            sessions = listOf(
                TestSession(
                    id = 2L,
                    name = "Legacy",
                    startedAt = 700L,
                    files = listOf("old.bin" to legacyContent),
                ),
            ),
            encodeDefaults = false,
        )

        val legacyResult = repo.importSessions(legacyZip)

        val legacySessionId = legacyResult.imported.single().newId
        val legacyMessage = db.messageDao().listBySession(legacySessionId).single()
        assertEquals("COMPLETED", legacyMessage.fileStatus)
        assertTrue(File(store.fileDir(legacySessionId), legacyMessage.fileId!!).exists())
    }

    @Test fun duplicate_import_skips_existing_sessions() = runTest {
        val zip = buildZip(listOf(
            TestSession(id = 1, name = "Dup", startedAt = 500L, texts = listOf("x")),
        ))

        val first = repo.importSessions(zip)
        assertEquals(1, first.imported.size)
        assertEquals(0, first.skipped.size)

        // Re-import the same zip (must rebuild — ZipFile consumed). buildZip is deterministic by name+startedAt.
        val zip2 = buildZip(listOf(
            TestSession(id = 1, name = "Dup", startedAt = 500L, texts = listOf("x")),
        ))
        val second = repo.importSessions(zip2)
        assertEquals(0, second.imported.size)
        assertEquals(1, second.skipped.size)
        assertEquals("Dup", second.skipped.single().name)
        assertEquals(1L, second.skipped.single().originalId)
        assertEquals(first.imported.single().newId, second.skipped.single().existingId)

        // Only one session exists in the DB.
        val count = db.sessionDao().nonPinnedOldestFirst().count { it.name == "Dup" }
        assertEquals(1, count)
    }

    @Test fun peek_import_conflicts_reports_existing_sessions_without_importing() = runTest {
        repo.importSessions(buildZip(listOf(
            TestSession(id = 1, name = "Dup", startedAt = 500L, texts = listOf("x")),
        )))

        val zip = buildZip(listOf(
            TestSession(id = 1, name = "Dup", startedAt = 500L, texts = listOf("x")),
            TestSession(id = 2, name = "Fresh", startedAt = 600L, texts = listOf("y")),
        ))
        assertEquals(listOf("Dup"), repo.peekImportConflicts(zip))

        // 纯只读预检：Fresh 不应被导入。
        assertTrue(db.sessionDao().nonPinnedOldestFirst().none { it.name == "Fresh" })
    }

    @Test fun peek_import_conflicts_empty_when_nothing_matches() = runTest {
        val zip = buildZip(listOf(
            TestSession(id = 1, name = "Solo", startedAt = 500L, texts = listOf("x")),
        ))
        assertTrue(repo.peekImportConflicts(zip).isEmpty())
    }

    @Test fun overwrite_import_replaces_existing_session_rows_and_files() = runTest {
        val first = repo.importSessions(buildZip(listOf(
            TestSession(
                id = 1, name = "Dup", startedAt = 500L,
                texts = listOf("old"), files = listOf("a.bin" to "old body".toByteArray()),
            ),
        )))
        val oldId = first.imported.single().newId
        val oldFileId = db.messageDao().listBySession(oldId).single { it.kind == "FILE" }.fileId!!

        // 模拟用户删掉一条消息后重新导入并选择覆盖。
        db.messageDao().deleteById(db.messageDao().listBySession(oldId).first { it.kind == "TEXT" }.id)

        val second = repo.importSessions(
            buildZip(listOf(
                TestSession(
                    id = 1, name = "Dup", startedAt = 500L,
                    texts = listOf("old"), files = listOf("a.bin" to "old body".toByteArray()),
                ),
            )),
            overwriteExisting = true,
        )

        assertEquals(1, second.imported.size)
        assertEquals(0, second.skipped.size)

        // 旧会话（行 + 文件目录）被替换为归档内容：被删的消息补回来了。
        val rows = db.sessionDao().nonPinnedOldestFirst().filter { it.name == "Dup" }
        assertEquals(1, rows.size)
        val newId = second.imported.single().newId
        assertTrue(newId != oldId)
        assertEquals(2, db.messageDao().listBySession(newId).size)
        assertTrue(db.messageDao().listBySession(oldId).isEmpty())
        assertTrue(!File(store.fileDir(oldId), oldFileId).exists())
    }

    @Test fun default_import_still_skips_existing_sessions() = runTest {
        repo.importSessions(buildZip(listOf(
            TestSession(id = 1, name = "Dup", startedAt = 500L, texts = listOf("x")),
        )))
        val second = repo.importSessions(buildZip(listOf(
            TestSession(id = 1, name = "Dup", startedAt = 500L, texts = listOf("x")),
        )))
        assertEquals(0, second.imported.size)
        assertEquals(1, second.skipped.size)
    }

    @Test fun fifo_sweep_runs_after_import_keeping_retain_limit() = runTest {
        // Import 25 distinct non-pinned sessions; retainLimit = 20 → 5 oldest swept.
        val sessions = (1..25).map {
            TestSession(id = it.toLong(), name = "S$it", startedAt = it.toLong(), texts = listOf("m$it"))
        }
        val zip = buildZip(sessions)

        val result = repo.importSessions(zip)
        assertEquals(25, result.imported.size)

        // After FIFO sweep, at most 20 non-pinned finished sessions remain.
        val remaining = db.sessionDao().nonPinnedOldestFirst()
        assertEquals(20, remaining.size)
        // Oldest (smallest startedAt) were evicted: S1..S5 gone, S6..S25 kept.
        assertTrue(remaining.none { it.name == "S1" })
        assertTrue(remaining.any { it.name == "S25" })
    }

    @Test fun pinned_session_retains_pinned_flag() = runTest {
        val zip = buildZip(listOf(
            TestSession(id = 1, name = "Pinned", startedAt = 500L, pinned = true, texts = listOf("x")),
        ))

        val result = repo.importSessions(zip)
        val row = db.sessionDao().getById(result.imported.single().newId)!!
        assertTrue("pinned flag should be preserved", row.pinned)
    }
}
