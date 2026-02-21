package com.flow.data.repository

import androidx.room.withTransaction
import com.flow.data.local.AppDatabase
import com.flow.data.local.DailyProgressDao
import com.flow.data.local.DailyProgressEntity
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskCompletionLogDao
import com.flow.data.local.TaskDao
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val taskDao: TaskDao,
    private val dailyProgressDao: DailyProgressDao,
    private val taskCompletionLogDao: TaskCompletionLogDao
) : TaskRepository {

    // ── Reactive queries ──────────────────────────────────────────────────

    override fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()

    override fun getHistory(): Flow<List<DailyProgressEntity>> = dailyProgressDao.getAllHistory()

    override fun getCompletedTaskCount(): Flow<Int> = taskDao.getCompletedTaskCount()

    override fun getTaskStreak(taskId: Long): Flow<Int> =
        taskCompletionLogDao.getLogsForTask(taskId).map { logs ->
            calculateStreakFromLogs(logs)
        }

    override fun getTaskHistory(taskId: Long): Flow<List<TaskCompletionLog>> =
        taskCompletionLogDao.getLogsForTask(taskId)

    override fun getTodayProgressRatio(): Flow<Float> {
        val todayEnd = getEndOfDay(normaliseToMidnight(System.currentTimeMillis()))
        return taskDao.getTasksActiveToday(todayEnd).map { tasks ->
            if (tasks.isEmpty()) return@map 0f
            val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }
            completedCount.toFloat() / tasks.size
        }
    }

    override fun getHeatMapData(): Flow<Map<Long, Int>> =
        dailyProgressDao.getAllHistory().map { rows ->
            rows.associate { it.date to it.tasksCompletedCount }
        }

    // ── Commands ──────────────────────────────────────────────────────────

    override suspend fun addTask(title: String, startDate: Long, dueDate: Long?, isRecurring: Boolean) {
        taskDao.insertTask(
            TaskEntity(
                title = title,
                startDate = normaliseToMidnight(startDate),
                dueDate = dueDate,
                isRecurring = isRecurring
            )
        )
    }

    /**
     * Update mutable task fields.
     * DI-001: completionTimestamp is preserved from the DB record — the caller cannot
     * overwrite it by passing a different value in the task object.
     */
    override suspend fun updateTask(task: TaskEntity) {
        val existing = taskDao.getTaskById(task.id) ?: return
        taskDao.updateTask(task.copy(completionTimestamp = existing.completionTimestamp))
    }

    /**
     * Transition task status following the FSM: TODO→IN_PROGRESS, TODO→COMPLETED,
     * IN_PROGRESS→COMPLETED, COMPLETED→TODO (undo). All other transitions are no-ops.
     * DI-001: completionTimestamp is SET once on first → COMPLETED; CLEARED on COMPLETED→TODO.
     */
    override suspend fun updateTaskStatus(task: TaskEntity, newStatus: TaskStatus) {
        val validTransition = when (task.status) {
            TaskStatus.TODO        -> newStatus == TaskStatus.IN_PROGRESS || newStatus == TaskStatus.COMPLETED
            TaskStatus.IN_PROGRESS -> newStatus == TaskStatus.COMPLETED
            TaskStatus.COMPLETED   -> newStatus == TaskStatus.TODO  // undo
        }
        if (!validTransition) return

        val justCompleted = newStatus == TaskStatus.COMPLETED
        val undone        = task.status == TaskStatus.COMPLETED && newStatus == TaskStatus.TODO

        val newTimestamp = when {
            justCompleted && task.completionTimestamp == null -> System.currentTimeMillis()
            undone                                           -> null
            else                                             -> task.completionTimestamp
        }

        taskDao.updateTask(task.copy(status = newStatus, completionTimestamp = newTimestamp))

        // Log recurring task completion / un-completion
        if (task.isRecurring) {
            val today = normaliseToMidnight(System.currentTimeMillis())
            taskCompletionLogDao.insertLog(
                TaskCompletionLog(taskId = task.id, date = today, isCompleted = justCompleted)
            )
        }

        // Keep DailyProgress in sync
        upsertDailyProgress(normaliseToMidnight(System.currentTimeMillis()))
    }

    /**
     * T017: Delete task AND all its TaskCompletionLog rows atomically in one transaction.
     */
    override suspend fun deleteTask(task: TaskEntity) {
        db.withTransaction {
            taskCompletionLogDao.deleteLogsForTask(task.id)
            taskDao.deleteTask(task)
        }
        upsertDailyProgress(normaliseToMidnight(System.currentTimeMillis()))
    }

    // ── Aggregates ────────────────────────────────────────────────────────

    override suspend fun calculateCurrentStreak(): Int {
        val history = dailyProgressDao.getAllHistory().firstOrNull() ?: return 0
        var streak = 0
        var expected = normaliseToMidnight(System.currentTimeMillis())
        for (day in history) { // already DESC
            if (day.date == expected && day.tasksCompletedCount > 0) {
                streak++
                expected -= 86_400_000L
            } else if (day.date < expected) break
        }
        return streak
    }

    override suspend fun refreshRecurringTasks() {
        val allTasks = taskDao.getAllTasks().firstOrNull() ?: return
        val today = normaliseToMidnight(System.currentTimeMillis())
        allTasks.filter { it.isRecurring && it.status == TaskStatus.COMPLETED }.forEach { task ->
            val completedDay = task.completionTimestamp?.let { normaliseToMidnight(it) } ?: 0L
            if (completedDay < today) {
                taskDao.updateTask(task.copy(status = TaskStatus.TODO, completionTimestamp = null))
            }
        }
    }

    override suspend fun getCompletedOnTimeCount(): Int = taskDao.getCompletedOnTimeCount()

    override suspend fun getMissedDeadlineCount(): Int =
        taskDao.getMissedDeadlineCount(System.currentTimeMillis())

    override suspend fun getBestStreak(): Int {
        val recurringTasks = taskDao.getAllTasks().firstOrNull()?.filter { it.isRecurring } ?: return 0
        return recurringTasks.maxOfOrNull { task ->
            val logs = taskCompletionLogDao.getLogsForTask(task.id).firstOrNull() ?: emptyList()
            calculateBestStreakFromLogs(logs)
        } ?: 0
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun upsertDailyProgress(todayMidnight: Long) {
        val allTasks = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val todayEnd = getEndOfDay(todayMidnight)
        val activeTasks = allTasks.filter { it.startDate <= todayEnd }
        dailyProgressDao.insertOrUpdateProgress(
            DailyProgressEntity(
                date = todayMidnight,
                tasksCompletedCount = activeTasks.count { it.status == TaskStatus.COMPLETED },
                tasksTotalCount = activeTasks.size
            )
        )
    }

    private fun calculateStreakFromLogs(logs: List<TaskCompletionLog>): Int {
        val completed = logs.filter { it.isCompleted }.sortedByDescending { it.date }
        if (completed.isEmpty()) return 0
        val today = normaliseToMidnight(System.currentTimeMillis())
        val yesterday = today - 86_400_000L
        if (completed.first().date != today && completed.first().date != yesterday) return 0
        var streak = 0
        var expected = completed.first().date
        for (log in completed) {
            if (log.date == expected) { streak++; expected -= 86_400_000L } else break
        }
        return streak
    }

    private fun calculateBestStreakFromLogs(logs: List<TaskCompletionLog>): Int {
        val dates = logs.filter { it.isCompleted }.map { it.date }.sorted()
        if (dates.isEmpty()) return 0
        var best = 0; var current = 0; var prev = -1L
        for (date in dates) {
            current = if (prev == -1L || date - prev == 86_400_000L) current + 1 else 1
            if (current > best) best = current
            prev = date
        }
        return best
    }

    private fun normaliseToMidnight(timestamp: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun getEndOfDay(midnight: Long): Long = midnight + 86_399_999L
}
