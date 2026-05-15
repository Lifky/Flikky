package com.example.flikky.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

/**
 * FTS4 virtual table mirroring text-searchable columns of [MessageEntity].
 *
 * Tokenizer: unicode61 with diacritic removal. This gives us CJK
 * single-character tokenization for free, so queries like "你好" can still
 * match documents containing "你" and "好" via FTS4 phrase / token logic.
 *
 * Recalled messages (where [MessageEntity.recalledAt] is non-null) are
 * intentionally excluded by the AFTER INSERT / AFTER UPDATE triggers
 * declared in `FlikkyDatabase.MIGRATION_1_2`.
 *
 * Note: Room generates the table name as `<entityClass>_fts` if you use
 * `@Fts4(contentEntity = ...)`. To match the SQL we authored in the
 * migration (`messages_fts`), we set [tableName] explicitly.
 */
@Entity(tableName = "messages_fts")
@Fts4(
    contentEntity = MessageEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    // NOTE: we intentionally pass ONLY `remove_diacritics=1` here. The KSP-time
    // SQLite used by Room's schema verifier does not recognize the additional
    // `categories='L* N* Co'` argument, so we apply that argument at runtime
    // instead — both via MIGRATION_1_2 (for upgraders) and via the database
    // Callback.onCreate (for fresh installs). See FlikkyDatabase.
    tokenizerArgs = ["remove_diacritics=1"],
)
data class MessageFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int,
    val content: String,
    val fileName: String,
)
