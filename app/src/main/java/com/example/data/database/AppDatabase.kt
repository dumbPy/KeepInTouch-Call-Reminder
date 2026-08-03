package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.TagDao
import com.example.data.model.ContactEntity
import com.example.data.model.ContactTagCrossRef
import com.example.data.model.InteractionLogEntity
import com.example.data.model.TagEntity

@Database(
    entities = [
        ContactEntity::class,
        TagEntity::class,
        ContactTagCrossRef::class,
        InteractionLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun tagDao(): TagDao
    abstract fun interactionLogDao(): InteractionLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_reminder_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
