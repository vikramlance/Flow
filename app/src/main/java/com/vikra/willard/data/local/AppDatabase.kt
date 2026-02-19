package com.vikra.willard.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TaskEntity::class, DailyProgressEntity::class, TaskCompletionLog::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun taskCompletionLogDao(): TaskCompletionLogDao
}
