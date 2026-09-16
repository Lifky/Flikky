package com.example.flikky.data

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.flikky.server.routes.AlbumResult
import com.example.flikky.util.AlbumItemId
import java.time.ZoneId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreLibraryTest {
    private lateinit var provider: AlbumProvider
    private lateinit var library: MediaStoreLibrary

    @Before fun setUp() {
        provider = AlbumProvider()
        provider.attachInfo(ApplicationProvider.getApplicationContext(), ProviderInfo().apply {
            authority = "media"
        })
        library = MediaStoreLibrary(ContentResolver.wrap(provider)) { ZoneId.of("UTC") }
    }

    @After fun tearDown() { provider.database.close() }

    @Test fun listingsCountsAndBucketsExcludeUnfinishedTrashedAndEmptyMedia() = runBlocking {
        val items = library.listStream().toList().flatten()
        assertEquals(setOf("img:1", "vid:1"), items.map { it.id }.toSet())
        assertEquals(2, library.count())
        assertEquals(2, library.count("Camera"))
        assertEquals(listOf("Camera"), library.buckets().map { it.name })
        assertEquals(2, library.buckets().single().count)
        assertEquals("img:1", library.buckets().single().coverId)
    }

    @Test fun publishedRecordsAppearAfterPendingWriteCompletes() = runBlocking {
        assertTrue(library.listStream().toList().flatten().none { it.id == "img:2" || it.id == "img:4" })
        provider.database.execSQL("UPDATE images SET is_pending = 0 WHERE _id = 2")
        assertTrue(library.listStream().toList().flatten().any { it.id == "img:2" })
        provider.database.execSQL("UPDATE images SET _size = 20 WHERE _id = 4")
        assertTrue(library.listStream().toList().flatten().any { it.id == "img:4" })
    }

    @Test fun openingMissingOriginalReturnsNotFoundAndQueriesOnlyItsId() {
        assertEquals(AlbumResult.NotFound, library.open(AlbumItemId.parse("img:1")!!))
        assertEquals(listOf("content://media/external/images/media/1"), provider.queries.map { it.toString() })
    }

    /** Real SQLite selection semantics, with deliberately unfiltered provider defaults. */
    private class AlbumProvider : ContentProvider() {
        val database = SQLiteDatabase.create(null)
        val queries = mutableListOf<Uri>()

        override fun onCreate(): Boolean {
            for (table in listOf("images", "video")) {
                database.execSQL("""
                    CREATE TABLE $table (_id INTEGER PRIMARY KEY, _display_name TEXT, mime_type TEXT,
                        _size INTEGER, date_modified INTEGER, datetaken INTEGER, bucket_display_name TEXT,
                        duration INTEGER, is_pending INTEGER, is_trashed INTEGER)
                """.trimIndent())
                val mime = if (table == "images") "image/jpeg" else "video/mp4"
                database.execSQL("INSERT INTO $table VALUES (1, 'published', '$mime', 100, 1000, 1000000, 'Camera', 0, 0, 0)")
                database.execSQL("INSERT INTO $table VALUES (2, 'pending', '$mime', 100, 1000, 1000000, 'Unfinished', 0, 1, 0)")
                database.execSQL("INSERT INTO $table VALUES (3, 'trashed', '$mime', 100, 1000, 1000000, 'Trash', 0, 0, 1)")
                database.execSQL("INSERT INTO $table VALUES (4, 'empty', '$mime', 0, 1000, 1000000, 'Empty', 0, 0, 0)")
            }
            return true
        }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            queries += uri
            val table = if (uri.pathSegments.contains("images")) "images" else "video"
            val id = uri.lastPathSegment?.toLongOrNull()
            val clauses = listOfNotNull(selection, id?.let { "_id = $it" }).joinToString(" AND ")
            return database.query(table, projection, clauses.ifEmpty { null }, selectionArgs, null, null, sortOrder)
        }

        override fun getType(uri: Uri): String = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
