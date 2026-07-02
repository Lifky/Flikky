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
            .addMigrations(
                FlikkyDatabase.MIGRATION_1_2,
                FlikkyDatabase.MIGRATION_2_3,
                FlikkyDatabase.MIGRATION_3_4,
                FlikkyDatabase.MIGRATION_4_5,
                FlikkyDatabase.MIGRATION_5_6,
            )
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

    @Test
    fun migration4To5AddsFavoritesTablesAndIndexes() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).use { db ->
            createV4Schema(db)
            db.version = 4
        }

        val roomDb = Room.databaseBuilder(context, FlikkyDatabase::class.java, TEST_DB)
            .addMigrations(
                FlikkyDatabase.MIGRATION_1_2,
                FlikkyDatabase.MIGRATION_2_3,
                FlikkyDatabase.MIGRATION_3_4,
                FlikkyDatabase.MIGRATION_4_5,
                FlikkyDatabase.MIGRATION_5_6,
            )
            .allowMainThreadQueries()
            .build()
        try {
            val db = roomDb.openHelper.writableDatabase
            assertTrue(hasTable(db, "favorite_groups"))
            assertTrue(hasTable(db, "favorites"))
            for (column in listOf(
                "id",
                "sourceSessionId",
                "sourceMessageId",
                "kind",
                "textContent",
                "fileId",
                "fileName",
                "fileSize",
                "fileMime",
                "groupId",
                "createdAt",
                "sourceSessionName",
                "origin",
            )) {
                assertTrue(hasColumn(db, "favorites", column))
            }
            assertTrue(hasIndex(db, "favorites", "index_favorites_sourceSessionId_sourceMessageId", unique = true))
            assertTrue(hasIndex(db, "favorites", "index_favorites_groupId", unique = false))
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun migration5To6AddsPeerAvatarKeyWithDesktopDefault() {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(TEST_DB), null).use { db ->
            createV5Schema(db)
            db.execSQL(
                "INSERT INTO sessions (id, startedAt, endedAt, name, pinned, messageCount, fileCount, totalBytes, previewText, peerAvatarId, groupId) " +
                    "VALUES (1, 100, 200, 'old', 0, 0, 0, 0, NULL, 0, NULL)"
            )
            db.version = 5
        }

        val roomDb = Room.databaseBuilder(context, FlikkyDatabase::class.java, TEST_DB)
            .addMigrations(
                FlikkyDatabase.MIGRATION_1_2,
                FlikkyDatabase.MIGRATION_2_3,
                FlikkyDatabase.MIGRATION_3_4,
                FlikkyDatabase.MIGRATION_4_5,
                FlikkyDatabase.MIGRATION_5_6,
            )
            .allowMainThreadQueries()
            .build()
        try {
            val db = roomDb.openHelper.writableDatabase
            assertTrue(hasColumn(db, "sessions", "peerAvatarKey"))
            db.query("SELECT peerAvatarKey FROM sessions WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.getString(0) == "icon:desktop_windows")
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

    private fun createV4Schema(db: SQLiteDatabase) {
        createV3Schema(db)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_groups` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE sessions ADD COLUMN groupId INTEGER DEFAULT NULL")
    }

    private fun createV5Schema(db: SQLiteDatabase) {
        createV4Schema(db)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite_groups` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorites` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sourceSessionId` INTEGER NOT NULL, " +
                "`sourceMessageId` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`textContent` TEXT, " +
                "`fileId` TEXT, " +
                "`fileName` TEXT, " +
                "`fileSize` INTEGER, " +
                "`fileMime` TEXT, " +
                "`groupId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`sourceSessionName` TEXT, " +
                "`origin` TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_favorites_sourceSessionId_sourceMessageId` " +
                "ON `favorites` (`sourceSessionId`, `sourceMessageId`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_groupId` ON `favorites` (`groupId`)")
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

    private fun hasIndex(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
        name: String,
        unique: Boolean,
    ): Boolean =
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val isUnique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                if (indexName == name && isUnique == unique) return true
            }
            false
        }

    private companion object {
        const val TEST_DB = "flikky-migration-test"
    }
}
