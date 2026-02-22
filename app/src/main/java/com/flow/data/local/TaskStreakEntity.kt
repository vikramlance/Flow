package com.flow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * T004 — Persisted streak state per recurring task.
 *
 * Upserted by [com.flow.data.repository.TaskRepositoryImpl.recalculateStreaks] after
 * every completion event or history edit. Not user-editable.
 */
@Entity(tableName = "task_streaks")
data class TaskStreakEntity(
    @PrimaryKey
    val taskId: Long,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    /** Midnight-epoch of the first day in the longest streak window; null when empty. */
    val longestStreakStartDate: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
