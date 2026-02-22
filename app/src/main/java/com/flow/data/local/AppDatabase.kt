package com.flow.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        DailyProgressEntity::class,
        TaskCompletionLog::class,
        TaskStreakEntity::class,
        AchievementEntity::class,
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(AchievementTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun dailyProgressDao(): DailyProgressDao
    abstract fun taskCompletionLogDao(): TaskCompletionLogDao
    abstract fun taskStreakDao(): TaskStreakDao
    abstract fun achievementDao(): AchievementDao

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

        /**
         * v5 → v6: Three additive schema changes:
         *  1. `scheduleMask` column on `tasks` (nullable, DAILY when null)
         *  2. `task_streaks` table — per-recurring-task streak state
         *  3. `achievements` table + unique index — permanently earned badges
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Recurring schedule bitmask
                db.execSQL(
                    "ALTER TABLE tasks ADD COLUMN scheduleMask INTEGER DEFAULT NULL"
                )
                // 2. Task streaks
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_streaks (
                        taskId INTEGER NOT NULL PRIMARY KEY,
                        currentStreak INTEGER NOT NULL DEFAULT 0,
                        longestStreak INTEGER NOT NULL DEFAULT 0,
                        longestStreakStartDate INTEGER,
                        lastUpdated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // 3. Achievements
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievements (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL,
                        taskId INTEGER,
                        earnedAt INTEGER NOT NULL,
                        periodLabel TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_achievements_unique
                    ON achievements(type, taskId, periodLabel)
                    """.trimIndent()
                )
            }
        }

        /**
         * v6 → v7: Repair the `idx_achievements_unique` index — the v5→6 migration
         * originally used `COALESCE()` in the index expression which does not match
         * the simple-column index that Room generates from `@Index(unique=true)`.
         * Drop and recreate with plain column names so Room schema validation passes.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS idx_achievements_unique")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_achievements_unique
                    ON achievements(type, taskId, periodLabel)
                    """.trimIndent()
                )
            }
        }
    }
}
