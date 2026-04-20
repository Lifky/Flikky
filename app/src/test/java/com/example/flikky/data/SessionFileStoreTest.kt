package com.example.flikky.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class SessionFileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = SessionFileStore(filesDir = tmp.root)

    @Test fun fileDir_creates_sessions_subdir() {
        val dir = store().fileDir(sessionId = 42L)
        assertTrue(dir.exists() && dir.isDirectory)
        assertTrue(dir.absolutePath.endsWith(File.separator + "sessions" + File.separator + "42" + File.separator + "files"))
    }

    @Test fun archive_copies_stream_to_session_files() {
        val payload = "hello".toByteArray()
        val target = store().archiveFromStream(
            sessionId = 7L, fileId = "abc",
            source = ByteArrayInputStream(payload),
        )
        assertTrue(target.exists())
        assertArrayEquals(payload, target.readBytes())
    }

    @Test fun deleteSessionDir_removes_files_recursively() {
        val s = store()
        val f = s.archiveFromStream(sessionId = 1L, fileId = "x",
            source = ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        assertTrue(f.exists())
        s.deleteSessionDir(sessionId = 1L)
        assertTrue(!f.exists())
        assertTrue(!(f.parentFile?.exists() ?: false))
    }
}
