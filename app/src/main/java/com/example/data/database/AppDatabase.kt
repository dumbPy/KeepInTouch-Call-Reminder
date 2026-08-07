package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.dao.ContactDao
import com.example.data.dao.InteractionLogDao
import com.example.data.dao.GroupDao
import com.example.data.model.ContactEntity
import com.example.data.model.InteractionLogEntity
import com.example.data.model.GroupEntity

@Database(
    entities = [
        ContactEntity::class,
        GroupEntity::class,
        InteractionLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun groupDao(): GroupDao
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
