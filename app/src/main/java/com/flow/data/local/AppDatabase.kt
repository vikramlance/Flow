package com.flow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, DailyProgressEntity::class, TaskCompletionLog::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun taskCompletionLogDao(): TaskCompletionLogDao

    companion object {
        /**
         * v4 → v5: Add unique index on task_logs(taskId, date) to prevent
         * duplicate completion rows per task per calendar day.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_task_logs_task_date
                    ON task_logs(taskId, date)
                    """.trimIndent()
                )
            }
        }
    }
}
