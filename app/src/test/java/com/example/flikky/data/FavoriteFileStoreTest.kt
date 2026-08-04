package com.example.flikky.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class FavoriteFileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = FavoriteFileStore(filesDir = tmp.root)

    @Test fun copyIn_copies_source_to_flat_favorites_dir() {
        val source = tmp.newFile("source.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val target = store().copyIn("depot-id", source)

        assertTrue(target.exists())
        assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        assertTrue(target.absolutePath.endsWith(File.separator + "favorites" + File.separator + "depot-id"))
    }

    @Test fun delete_is_idempotent() {
        val store = store()
        val source = tmp.newFile("source.bin").apply { writeBytes(byteArrayOf(1)) }
        val target = store.copyIn("depot-id", source)

        assertTrue(target.exists())
        assertTrue(store.delete("depot-id"))
        assertTrue(!target.exists())
        assertTrue(store.delete("depot-id"))
    }

    @Test fun deleteAll_removes_whole_favorites_tree() {
        val store = store()
        store.copyIn("d1", ByteArrayInputStream(byteArrayOf(1)))
        store.copyIn("d2", ByteArrayInputStream(byteArrayOf(2)))

        assertTrue(store.deleteAll())
        assertTrue(!File(tmp.root, "favorites").exists())
    }
}
