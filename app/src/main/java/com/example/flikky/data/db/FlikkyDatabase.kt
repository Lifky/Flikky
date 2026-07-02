package com.example.flikky.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.MessageFtsEntity
import com.example.flikky.data.db.entities.FavoriteEntity
import com.example.flikky.data.db.entities.FavoriteGroupEntity
import com.example.flikky.data.db.entities.GroupEntity
import com.example.flikky.data.db.entities.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        GroupEntity::class,
        FavoriteEntity::class,
        FavoriteGroupEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class FlikkyDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun favoriteGroupDao(): FavoriteGroupDao

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
         * Fresh-install hook. Room自动用 [MessageFtsEntity.tokenizerArgs] 建好
         * `messages_fts` 表，tokenizer 是 `unicode61 'remove_diacritics=1'`——
         * 这是 Android SQLite 唯一安全支持的参数集（v1.3 装机崩溃证明：
         * `categories='L* N* Co'` 等高级参数需要带 ICU 的 SQLite，Android 内置
         * SQLite 不支持）。
         *
         * Room 还会自动生成 content-sync triggers 把 messages 表的变化镜像到
         * messages_fts；但 Room 的 trigger 不知道 recalledAt 字段，撤回的消息
         * 仍会留在 FTS 中。本回调动态扫描并 DROP 所有 Room 自动 trigger，
         * 换成我们自己的 messages_fts_ai/ad/au，让撤回时同步从 FTS 移除。
         */
        private val onCreateCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                dropRoomAutoFtsTriggers(db)
                createFtsTriggers(db)
            }
        }

        /**
         * 动态扫 sqlite_master 找 Room 自动生成的 fts content-sync triggers
         * 并 DROP。命名前缀 `room_fts_content_sync_` 但具体 phase 后缀依
         * Room 版本而变，硬编码不稳，dynamic query 最稳。
         */
        private fun dropRoomAutoFtsTriggers(db: SupportSQLiteDatabase) {
            val names = mutableListOf<String>()
            db.query(
                "SELECT name FROM sqlite_master WHERE type='trigger' " +
                    "AND name LIKE 'room_fts_content_sync_%'"
            ).use { c ->
                while (c.moveToNext()) names.add(c.getString(0))
            }
            for (n in names) db.execSQL("DROP TRIGGER IF EXISTS `$n`")
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Add recalledAt + senderId columns to messages
                db.execSQL("ALTER TABLE messages ADD COLUMN recalledAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN senderId TEXT DEFAULT NULL")

                // 2) Create FTS4 virtual table. Only `remove_diacritics=1` is portable
                //    across Android SQLite builds — `categories='L* N* Co'` requires
                //    SQLite built with ICU, which Android does NOT ship. unicode61
                //    treats CJK characters as separators by default; that means
                //    Chinese queries get zero FTS tokens. SessionRepository.search
                //    routes any non-ASCII query through the LIKE fallback to cover
                //    this limitation — see search() for the branch.
                db.execSQL(
                    "CREATE VIRTUAL TABLE messages_fts USING fts4(" +
                        "content, fileName, tokenize=unicode61 'remove_diacritics=1')"
                )

                // 3) Backfill from existing messages (skip recalled ones — there shouldn't be any at v1->v2)
                db.execSQL(
                    "INSERT INTO messages_fts(rowid, content, fileName) " +
                        "SELECT id, IFNULL(content, ''), IFNULL(fileName, '') FROM messages " +
                        "WHERE recalledAt IS NULL"
                )

                // 4) Install our recalledAt-aware triggers. Upgrade path doesn't run
                //    onCreate so it never sees Room's auto triggers — only the fresh
                //    install Callback has to drop those.
                createFtsTriggers(db)
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sessions ADD COLUMN peerAvatarId INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_groups` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE sessions ADD COLUMN groupId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_favorites_groupId` ON `favorites` (`groupId`)"
                )
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sessions ADD COLUMN peerAvatarKey TEXT NOT NULL DEFAULT 'icon:desktop_windows'"
                )
            }
        }

        fun build(context: Context): FlikkyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FlikkyDatabase::class.java,
                "flikky.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(onCreateCallback)
                .build()
    }
}
