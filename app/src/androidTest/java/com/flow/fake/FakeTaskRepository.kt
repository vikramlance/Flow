package com.flow.fake

import com.flow.data.local.AchievementEntity
import com.flow.data.local.DailyProgressEntity
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.local.TaskStreakEntity
import com.flow.data.repository.CurrentYearStats
import com.flow.data.repository.LifetimeStats
import com.flow.data.repository.TaskRepository
import com.flow.data.repository.TodayProgressState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake [TaskRepository] for JVM unit tests.
 * Backed by [MutableStateFlow]s — tests can push new values at any time.
 */
class FakeTaskRepository : TaskRepository {

    // ── Backing state flows ──────────────────────────────────────────────────
    val allTasksFlow          = MutableStateFlow<List<TaskEntity>>(emptyList())
    val homeScreenTasksFlow   = MutableStateFlow<List<TaskEntity>>(emptyList())
    val completedNonRecurringFlow = MutableStateFlow<List<TaskEntity>>(emptyList())
    val completedLogsFlow     = MutableStateFlow<List<TaskCompletionLog>>(emptyList())
    val progressRatioFlow     = MutableStateFlow(0f)
    val completedCountFlow    = MutableStateFlow(0)
    val todayProgressFlow     = MutableStateFlow(TodayProgressState(0, 0))
    val achievementsFlow      = MutableStateFlow<List<AchievementEntity>>(emptyList())

    // ── Reactive queries ─────────────────────────────────────────────────────
    override fun getAllTasks(): Flow<List<TaskEntity>>         = allTasksFlow
    override fun getHomeScreenTasks(): Flow<List<TaskEntity>> = homeScreenTasksFlow
    override fun getCompletedNonRecurringTasks(): Flow<List<TaskEntity>> = completedNonRecurringFlow
    override fun getAllCompletedRecurringLogs(): Flow<List<TaskCompletionLog>> = completedLogsFlow
    override fun getTodayProgressRatio(): Flow<Float>         = progressRatioFlow
    override fun getCompletedTaskCount(): Flow<Int>           = completedCountFlow
    override fun getTodayProgress(): Flow<TodayProgressState> = todayProgressFlow

    override fun getHistory(): Flow<List<DailyProgressEntity>> =
        MutableStateFlow(emptyList<DailyProgressEntity>())

    override fun getTaskStreak(taskId: Long): Flow<Int> = MutableStateFlow(0)

    override fun getTaskHistory(taskId: Long): Flow<List<TaskCompletionLog>> =
        completedLogsFlow.map { logs -> logs.filter { it.taskId == taskId } }

    override fun getHeatMapData(startMs: Long, endMs: Long): Flow<Map<Long, Int>> =
        MutableStateFlow(emptyMap())

    override fun getHeatMapData(): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())

    override fun getForestData(startMs: Long, endMs: Long): Flow<Map<Long, List<String>>> =
        MutableStateFlow(emptyMap())

    override fun getStreakForTask(taskId: Long): Flow<TaskStreakEntity?> =
        MutableStateFlow(null)

    override fun getAchievements(): Flow<List<AchievementEntity>> = achievementsFlow

    // ── Commands ─────────────────────────────────────────────────────────────
    override suspend fun addTask(
        title: String,
        startDate: Long,
        dueDate: Long?,
        isRecurring: Boolean,
        scheduleMask: Int?
    ) {
        val newTask = TaskEntity(
            id           = (allTasksFlow.value.size + 1).toLong(),
            title        = title,
            startDate    = startDate,
            dueDate      = dueDate,
            isRecurring  = isRecurring,
            scheduleMask = scheduleMask
        )
        allTasksFlow.value        = allTasksFlow.value        + newTask
        homeScreenTasksFlow.value = homeScreenTasksFlow.value + newTask
    }

    override suspend fun updateTask(task: TaskEntity) {
        allTasksFlow.value        = allTasksFlow.value.map        { if (it.id == task.id) task else it }
        homeScreenTasksFlow.value = homeScreenTasksFlow.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun updateTaskStatus(task: TaskEntity, newStatus: TaskStatus) {
        val updated = task.copy(
            status              = newStatus,
            completionTimestamp = if (newStatus == TaskStatus.COMPLETED) System.currentTimeMillis() else null
        )
        updateTask(updated)
    }

    override suspend fun deleteTask(task: TaskEntity) {
        allTasksFlow.value        = allTasksFlow.value.filter        { it.id != task.id }
        homeScreenTasksFlow.value = homeScreenTasksFlow.value.filter { it.id != task.id }
    }

    override suspend fun getTaskById(id: Long): TaskEntity? =
        allTasksFlow.value.find { it.id == id }

    override suspend fun updateLog(log: TaskCompletionLog) {
        completedLogsFlow.value = completedLogsFlow.value.map { if (it.id == log.id) log else it }
    }

    override suspend fun recalculateStreaks(taskId: Long) { /* no-op */ }

    override suspend fun checkAndAwardAchievements(taskId: Long?) { /* no-op */ }

    // ── Aggregates ────────────────────────────────────────────────────────────
    override suspend fun calculateCurrentStreak(): Int  = 0
    override suspend fun refreshRecurringTasks()        { /* no-op */ }
    override suspend fun getCompletedOnTimeCount(): Int = 0
    override suspend fun getMissedDeadlineCount(): Int  = 0
    override suspend fun getBestStreak(): Int           = 0
    override suspend fun getEarliestCompletionDate(): Long? = null

    override suspend fun getLifetimeStats(): LifetimeStats = LifetimeStats(
        totalCompleted    = 0, onTimeCompleted = 0, onTimePct = 0f,
        longestStreak     = 0, uniqueHabitsCount = 0
    )

    override suspend fun getCurrentYearStats(): CurrentYearStats = CurrentYearStats(
        completedThisYear = 0, onTimeRateThisYear = 0f, bestStreakThisYear = 0
    )
}
