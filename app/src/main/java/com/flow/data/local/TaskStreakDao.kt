package com.flow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * T007 — DAO for [TaskStreakEntity].
 */
@Dao
interface TaskStreakDao {
    /** Insert or replace streak state for a task. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreak(streak: TaskStreakEntity)

    /** Reactive stream of streak state for a single task; null if no streak computed yet. */
    @Query("SELECT * FROM task_streaks WHERE taskId = :taskId")
    fun getStreakForTask(taskId: Long): Flow<TaskStreakEntity?>

    /** Remove streak record when the owning task is deleted. */
    @Query("DELETE FROM task_streaks WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Long)
}
