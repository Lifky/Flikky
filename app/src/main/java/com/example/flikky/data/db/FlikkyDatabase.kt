package com.example.flikky.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.MessageFtsEntity
import com.example.flikky.data.db.entities.SessionEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class, MessageFtsEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FlikkyDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Add recalledAt column to messages
                db.execSQL("ALTER TABLE messages ADD COLUMN recalledAt INTEGER DEFAULT NULL")

                // 2) Create FTS4 virtual table (unicode61 single-char tokenization, diacritics removed)
                db.execSQL(
                    "CREATE VIRTUAL TABLE messages_fts USING fts4(" +
                        "content, fileName, " +
                        "tokenize=unicode61 'remove_diacritics=1')"
                )

                // 3) Backfill from existing messages (skip recalled ones — there shouldn't be any at v1->v2)
                db.execSQL(
                    "INSERT INTO messages_fts(rowid, content, fileName) " +
                        "SELECT id, IFNULL(content, ''), IFNULL(fileName, '') FROM messages " +
                        "WHERE recalledAt IS NULL"
                )

                // 4a) AFTER INSERT trigger: index new (non-recalled) messages
                db.execSQL(
                    "CREATE TRIGGER messages_fts_ai AFTER INSERT ON messages " +
                        "WHEN new.recalledAt IS NULL " +
                        "BEGIN " +
                        "INSERT INTO messages_fts(rowid, content, fileName) " +
                        "VALUES (new.id, IFNULL(new.content, ''), IFNULL(new.fileName, '')); " +
                        "END"
                )

                // 4b) AFTER DELETE trigger: remove FTS row
                db.execSQL(
                    "CREATE TRIGGER messages_fts_ad AFTER DELETE ON messages " +
                        "BEGIN " +
                        "DELETE FROM messages_fts WHERE rowid = old.id; " +
                        "END"
                )

                // 4c) AFTER UPDATE trigger: drop the old FTS row, then re-insert iff still not recalled
                db.execSQL(
                    "CREATE TRIGGER messages_fts_au AFTER UPDATE ON messages " +
                        "BEGIN " +
                        "DELETE FROM messages_fts WHERE rowid = old.id; " +
                        "INSERT INTO messages_fts(rowid, content, fileName) " +
                        "SELECT new.id, IFNULL(new.content, ''), IFNULL(new.fileName, '') " +
                        "WHERE new.recalledAt IS NULL; " +
                        "END"
                )
            }
        }

        fun build(context: Context): FlikkyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FlikkyDatabase::class.java,
                "flikky.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
