package com.flow.data.repository

import com.flow.data.local.DailyProgressEntity
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    // ── Reactive queries ────────────────────────────────────────────────────
    fun getAllTasks(): Flow<List<TaskEntity>>
    fun getHistory(): Flow<List<DailyProgressEntity>>
    fun getCompletedTaskCount(): Flow<Int>
    fun getTaskStreak(taskId: Long): Flow<Int>
    fun getTaskHistory(taskId: Long): Flow<List<TaskCompletionLog>>

    /** Ratio of tasks completed today (0.0–1.0). */
    fun getTodayProgressRatio(): Flow<Float>

    /** Date-keyed map for heat-map rendering: midnight-epoch → completion count. */
    fun getHeatMapData(): Flow<Map<Long, Int>>

    // ── Commands ────────────────────────────────────────────────────────────
    suspend fun addTask(
        title: String,
        startDate: Long = System.currentTimeMillis(),
        dueDate: Long? = null,
        isRecurring: Boolean = false
    )
    suspend fun updateTask(task: TaskEntity)
    suspend fun updateTaskStatus(task: TaskEntity, newStatus: TaskStatus)
    suspend fun deleteTask(task: TaskEntity)

    // ── Aggregates ──────────────────────────────────────────────────────────
    suspend fun calculateCurrentStreak(): Int
    suspend fun refreshRecurringTasks()

    /** Count tasks completed on or before their dueDate. */
    suspend fun getCompletedOnTimeCount(): Int

    /** Count tasks whose dueDate passed and status is still not COMPLETED. */
    suspend fun getMissedDeadlineCount(): Int

    /** Best consecutive-day streak ever across all recurring tasks. */
    suspend fun getBestStreak(): Int
}
