package com.flow.data.repository

import com.flow.data.local.AchievementEntity
import com.flow.data.local.DailyProgressEntity
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.local.TaskStreakEntity
import kotlinx.coroutines.flow.Flow

// ── Data transfer objects ────────────────────────────────────────────────────

/**
 * T014 — Contract 1 return type: today's tasks progress state.
 */
data class TodayProgressState(
    val totalToday: Int,
    val completedToday: Int,
    val hasAnyTodayTasks: Boolean = totalToday > 0
) {
    val ratio: Float get() = if (totalToday == 0) 0f else completedToday / totalToday.toFloat()
}

/**
 * T014 — All-time cumulative statistics.
 */
data class LifetimeStats(
    val totalCompleted: Int,
    val onTimeCompleted: Int,
    val onTimePct: Float,
    val longestStreak: Int,
    val uniqueHabitsCount: Int
)

/**
 * T014 — Calendar-year-scoped statistics (Jan 1 – Dec 31 of current year).
 */
data class CurrentYearStats(
    val completedThisYear: Int,
    val onTimeRateThisYear: Float,
    val bestStreakThisYear: Int
)

// ── Repository interface ─────────────────────────────────────────────────────

interface TaskRepository {
    // ── Reactive queries ────────────────────────────────────────────────────
    fun getAllTasks(): Flow<List<TaskEntity>>
    fun getHistory(): Flow<List<DailyProgressEntity>>
    fun getCompletedTaskCount(): Flow<Int>
    fun getTaskStreak(taskId: Long): Flow<Int>
    fun getTaskHistory(taskId: Long): Flow<List<TaskCompletionLog>>

    /** Today-focused progress: only tasks with dueDate = today midnight count (FR-001). */
    fun getTodayProgress(): Flow<TodayProgressState>

    /**
     * [Deprecated — FR-001 replacement] Old ratio-based today progress.
     * Retained for backwards-compatibility; delegates to [getTodayProgress] ratio.
     */
    fun getTodayProgressRatio(): Flow<Float>

    /**
     * T014 — Date-keyed heatmap data for the given epoch range.
     * ⚠ ViewModel must convert AnalyticsPeriod → (startMs, endMs) via toDateRange() before calling.
     */
    fun getHeatMapData(startMs: Long, endMs: Long): Flow<Map<Long, Int>>

    /** Legacy parameterless heatmap (rolling 52-week view; retained for non-regression). */
    fun getHeatMapData(): Flow<Map<Long, Int>>

    /** T005 — Tasks to display on the Home screen (4-rule filtered list, FR-004). */
    fun getHomeScreenTasks(): Flow<List<TaskEntity>>

    /** T005 — All completed recurring-task log entries for the global history screen. */
    fun getAllCompletedRecurringLogs(): Flow<List<TaskCompletionLog>>

    /** T005 — All completed non-recurring tasks for the global history screen. */
    fun getCompletedNonRecurringTasks(): Flow<List<TaskEntity>>

    // ── New reactive queries (T014) ──────────────────────────────────────────

    /**
     * Forest data: midnight-epoch → list of recurring task titles completed that day.
     * ⚠ ViewModel must convert AnalyticsPeriod → (startMs, endMs) via toDateRange().
     */
    fun getForestData(startMs: Long, endMs: Long): Flow<Map<Long, List<String>>>

    /** Reactive streak state for a single recurring task. */
    fun getStreakForTask(taskId: Long): Flow<TaskStreakEntity?>

    /** All earned achievement badges, newest first. */
    fun getAchievements(): Flow<List<AchievementEntity>>

    // ── Commands ────────────────────────────────────────────────────────────
    suspend fun addTask(
        title: String,
        startDate: Long = System.currentTimeMillis(),
        dueDate: Long? = null,
        isRecurring: Boolean = false,
        scheduleMask: Int? = null
    )
    suspend fun updateTask(task: TaskEntity)
    suspend fun updateTaskStatus(task: TaskEntity, newStatus: TaskStatus)
    suspend fun deleteTask(task: TaskEntity)

    /** T035 — Look up a single task by its primary key (for history-edit validation). */
    suspend fun getTaskById(id: Long): TaskEntity?

    /** T014 — Update an existing TaskCompletionLog entry (for history editing). */
    suspend fun updateLog(log: TaskCompletionLog)

    /** T014 — Recalculate streak for a task after completion or log edit. */
    suspend fun recalculateStreaks(taskId: Long)

    /**
     * T014 — Check all achievement thresholds and award badges.
     * [taskId] is the task just completed (for streak badges); pass null to check only global thresholds.
     */
    suspend fun checkAndAwardAchievements(taskId: Long?)

    // ── Aggregates ──────────────────────────────────────────────────────────
    suspend fun calculateCurrentStreak(): Int
    suspend fun refreshRecurringTasks()
    suspend fun getCompletedOnTimeCount(): Int
    suspend fun getMissedDeadlineCount(): Int
    suspend fun getBestStreak(): Int

    /** T014 — All-time cumulative stats. */
    suspend fun getLifetimeStats(): LifetimeStats

    /** T014 — Stats scoped to the current calendar year. */
    suspend fun getCurrentYearStats(): CurrentYearStats

    /** Earliest completion date across all logs; null if no data. */
    suspend fun getEarliestCompletionDate(): Long?
}
