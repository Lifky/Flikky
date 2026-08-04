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

    @Test fun messageFile_resolves_same_path_as_fileDir() {
        val s = store()
        val f = s.messageFile(sessionId = 42L, fileId = "abc")
        assertTrue(f.absolutePath == File(s.fileDir(42L), "abc").absolutePath)
    }

    @Test fun thumbFile_resolves_under_session_thumbs_dir() {
        val f = store().thumbFile(sessionId = 42L, fileId = "abc")
        assertTrue(
            f.absolutePath.endsWith(
                File.separator + "sessions" + File.separator + "42" +
                    File.separator + "thumbs" + File.separator + "abc.jpg"
            )
        )
        assertTrue(f.parentFile!!.isDirectory)
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

    @Test fun deleteMessageFile_also_removes_thumb() {
        val s = store()
        s.archiveFromStream(1L, "x", ByteArrayInputStream(byteArrayOf(1)))
        val thumb = s.thumbFile(1L, "x").apply { writeBytes(byteArrayOf(9)) }
        assertTrue(s.deleteMessageFile(1L, "x"))
        assertTrue(!thumb.exists())
    }

    @Test fun deleteSessionDir_removes_thumbs_too() {
        val s = store()
        val thumb = s.thumbFile(7L, "y").apply { writeBytes(byteArrayOf(9)) }
        s.deleteSessionDir(7L)
        assertTrue(!thumb.exists())
    }

    @Test fun deleteAllSessionDirs_removes_whole_sessions_tree() {
        val s = store()
        s.archiveFromStream(1L, "a", ByteArrayInputStream(byteArrayOf(1)))
        s.archiveFromStream(2L, "b", ByteArrayInputStream(byteArrayOf(2)))

        assertTrue(s.deleteAllSessionDirs())
        assertTrue(!File(tmp.root, "sessions").exists())
    }
}
