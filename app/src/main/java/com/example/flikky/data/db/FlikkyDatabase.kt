package com.example.flikky.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.flikky.data.db.entities.MessageEntity
import com.example.flikky.data.db.entities.SessionEntity

@Database(
    entities = [SessionEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FlikkyDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao

    companion object {
        fun build(context: Context): FlikkyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FlikkyDatabase::class.java,
                "flikky.db",
            ).build()
    }
}
