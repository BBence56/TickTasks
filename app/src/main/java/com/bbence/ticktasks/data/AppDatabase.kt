package com.bbence.ticktasks.data
// Room database setup
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val inst = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "ticktasks.db")
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = inst
                inst
            }
        }
    }
}

