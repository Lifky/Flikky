package com.example.flikky.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FlikkyDatabaseMigrationTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migration3To4AddsGroupsTableAndNullableSessionGroupId() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).use { db ->
            createV3Schema(db)
            db.execSQL(
                "INSERT INTO sessions (id, startedAt, endedAt, name, pinned, messageCount, fileCount, totalBytes, previewText, peerAvatarId) " +
                    "VALUES (1, 100, 200, 'old', 0, 0, 0, 0, NULL, 0)"
            )
            db.version = 3
        }

        val roomDb = Room.databaseBuilder(context, FlikkyDatabase::class.java, TEST_DB)
            .addMigrations(FlikkyDatabase.MIGRATION_1_2, FlikkyDatabase.MIGRATION_2_3, FlikkyDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val db = roomDb.openHelper.writableDatabase
            assertTrue(hasTable(db, "session_groups"))
            assertTrue(hasColumn(db, "sessions", "groupId"))
            db.query("SELECT groupId FROM sessions WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        } finally {
            roomDb.close()
        }
    }

    private fun createV3Schema(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, " +
                "`name` TEXT NOT NULL, " +
                "`pinned` INTEGER NOT NULL, " +
                "`messageCount` INTEGER NOT NULL, " +
                "`fileCount` INTEGER NOT NULL, " +
                "`totalBytes` INTEGER NOT NULL, " +
                "`previewText` TEXT, " +
                "`peerAvatarId` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`origin` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`content` TEXT, " +
                "`fileId` TEXT, " +
                "`fileName` TEXT, " +
                "`fileSize` INTEGER, " +
                "`fileMime` TEXT, " +
                "`fileStatus` TEXT, " +
                "`recalledAt` INTEGER, " +
                "`senderId` TEXT, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_sessionId` ON `messages` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_timestamp` ON `messages` (`timestamp`)")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `messages_fts` USING FTS4(" +
                "`content` TEXT, `fileName` TEXT, content=`messages`, tokenize=unicode61 `remove_diacritics=1`)"
        )
    }

    private fun hasTable(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            it.moveToFirst()
        }

    private fun hasColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == column) return true
            }
            false
        }

    private companion object {
        const val TEST_DB = "flikky-migration-test"
    }
}
