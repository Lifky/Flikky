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
        /**
         * Triggers that keep `messages_fts` in sync with `messages`. Used by both
         * the migration (for upgraders) and the onCreate Callback (for fresh
         * installs), so the runtime behavior is identical regardless of how the
         * database came into existence.
         */
        private fun createFtsTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS messages_fts_ai AFTER INSERT ON messages " +
                    "WHEN new.recalledAt IS NULL " +
                    "BEGIN " +
                    "INSERT INTO messages_fts(rowid, content, fileName) " +
                    "VALUES (new.id, IFNULL(new.content, ''), IFNULL(new.fileName, '')); " +
                    "END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS messages_fts_ad AFTER DELETE ON messages " +
                    "BEGIN " +
                    "DELETE FROM messages_fts WHERE rowid = old.id; " +
                    "END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS messages_fts_au AFTER UPDATE ON messages " +
                    "BEGIN " +
                    "DELETE FROM messages_fts WHERE rowid = old.id; " +
                    "INSERT INTO messages_fts(rowid, content, fileName) " +
                    "SELECT new.id, IFNULL(new.content, ''), IFNULL(new.fileName, '') " +
                    "WHERE new.recalledAt IS NULL; " +
                    "END"
            )
        }

        /**
         * Fresh-install hook: Room creates `messages_fts` from the FTS entity's
         * `tokenizerArgs`, which can only safely include `remove_diacritics=1`
         * (the KSP-time SQLite rejects `categories='L* N* Co'`). At runtime the
         * real SQLite supports the richer arg set, so we drop Room's auto table
         * and recreate it with the full tokenizer config, then attach our triggers.
         */
        private val onCreateCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Tear down Room's auto-generated FTS triggers — they reference the
                // about-to-be-dropped table and would be left dangling otherwise.
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_messages_fts_BEFORE_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_messages_fts_BEFORE_DELETE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_messages_fts_AFTER_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_messages_fts_AFTER_INSERT")
                db.execSQL("DROP TABLE IF EXISTS messages_fts")
                db.execSQL(
                    "CREATE VIRTUAL TABLE messages_fts USING fts4(" +
                        "content, fileName, " +
                        "tokenize=unicode61 'remove_diacritics=1' \"categories=L* N* Co\")"
                )
                // No backfill needed on fresh install — there are no messages yet.
                createFtsTriggers(db)
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Add recalledAt column to messages
                db.execSQL("ALTER TABLE messages ADD COLUMN recalledAt INTEGER DEFAULT NULL")

                // 2) Create FTS4 virtual table.
                //    unicode61 with diacritic removal handles Latin scripts well, but by
                //    default treats CJK Unified Ideographs (Unicode category Lo) as
                //    separators, leaving zero searchable tokens for Chinese text. The
                //    `categories='L* N* Co'` argument adds Lo (and other letter/number
                //    classes) to the token set, giving us free single-character
                //    tokenization for CJK while still tokenizing ASCII normally.
                db.execSQL(
                    "CREATE VIRTUAL TABLE messages_fts USING fts4(" +
                        "content, fileName, " +
                        "tokenize=unicode61 'remove_diacritics=1' \"categories=L* N* Co\")"
                )

                // 3) Backfill from existing messages (skip recalled ones — there shouldn't be any at v1->v2)
                db.execSQL(
                    "INSERT INTO messages_fts(rowid, content, fileName) " +
                        "SELECT id, IFNULL(content, ''), IFNULL(fileName, '') FROM messages " +
                        "WHERE recalledAt IS NULL"
                )

                // 4) Install AFTER INSERT / DELETE / UPDATE triggers that mirror the
                //    onCreate path, so fresh-install and upgrade behavior match.
                createFtsTriggers(db)
            }
        }

        fun build(context: Context): FlikkyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FlikkyDatabase::class.java,
                "flikky.db",
            )
                .addMigrations(MIGRATION_1_2)
                .addCallback(onCreateCallback)
                .build()
    }
}
