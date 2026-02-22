package com.flow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T008 — DAO for [AchievementEntity].
 *
 * Achievements are append-only: rows are never deleted.
 * Uniqueness is enforced by [idx_achievements_unique]; CONFLICT IGNORE makes
 * attempted duplicate inserts a silent no-op.
 */
@Dao
interface AchievementDao {
    /** Insert a new badge; silently a no-op if the unique index constraint fires. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOnConflictIgnore(achievement: AchievementEntity)

    /** Reactive stream of all earned badges, newest first. */
    @Query("SELECT * FROM achievements ORDER BY earnedAt DESC")
    fun getAll(): Flow<List<AchievementEntity>>

    /** All badges for a specific task (for per-task streak badges). */
    @Query("SELECT * FROM achievements WHERE taskId = :taskId ORDER BY earnedAt DESC")
    fun getByTask(taskId: Long): Flow<List<AchievementEntity>>

    /** Non-reactive: check if a badge has already been awarded (used inside transactions). */
    @Query("SELECT COUNT(*) FROM achievements WHERE type = :type AND (taskId = :taskId OR (:taskId IS NULL AND taskId IS NULL)) AND (periodLabel = :periodLabel OR (:periodLabel IS NULL AND periodLabel IS NULL))")
    suspend fun existsCount(type: String, taskId: Long?, periodLabel: String?): Int
}
