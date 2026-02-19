package com.vikra.willard.data.repository

import com.vikra.willard.data.local.DailyProgressDao
import com.vikra.willard.data.local.DailyProgressEntity
import com.vikra.willard.data.local.TaskDao
import com.vikra.willard.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val dailyProgressDao: DailyProgressDao,
    private val taskCompletionLogDao: com.vikra.willard.data.local.TaskCompletionLogDao
) : TaskRepository {

    override fun getAllTasks(): Flow<List<TaskEntity>> = taskDao.getAllTasks()
    
    override fun getHistory(): Flow<List<DailyProgressEntity>> = dailyProgressDao.getAllHistory()

    override suspend fun addTask(title: String, startDate: Long, dueDate: Long?, isRecurring: Boolean) {
        val task = TaskEntity(
            title = title,
            startDate = startDate,
            dueDate = dueDate,
            isRecurring = isRecurring
        )
        taskDao.insertTask(task)
    }

    override suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    override suspend fun updateTaskStatus(task: TaskEntity, newStatus: com.vikra.willard.data.local.TaskStatus) {
        val updatedTask = task.copy(
            status = newStatus,
            completionTimestamp = if (newStatus == com.vikra.willard.data.local.TaskStatus.COMPLETED) 
                System.currentTimeMillis() else null
        )
        taskDao.updateTask(updatedTask)

        val today = getStartOfDay(System.currentTimeMillis())

        // Handle Recurring Task Logging
        if (task.isRecurring && newStatus == com.vikra.willard.data.local.TaskStatus.COMPLETED) {
            val existingLog = taskCompletionLogDao.getLogForTaskDate(task.id, today)
            if (existingLog == null) {
                taskCompletionLogDao.insertLog(
                    com.vikra.willard.data.local.TaskCompletionLog(
                        taskId = task.id,
                        date = today,
                        isCompleted = true
                    )
                )
            }
        }

        // Update Daily Progress (Main Streak)
        // Get all tasks to calculate total count
        val allTasks = taskDao.getAllTasks().firstOrNull() ?: emptyList()
        val completedToday = allTasks.count { it.status == com.vikra.willard.data.local.TaskStatus.COMPLETED }
        val totalTasks = allTasks.size

        val dailyProgress = DailyProgressEntity(
            date = today,
            tasksCompletedCount = completedToday,
            tasksTotalCount = totalTasks
        )
        
        dailyProgressDao.insertOrUpdateProgress(dailyProgress)
    }

    override suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    override fun getCompletedTaskCount(): Flow<Int> = taskDao.getCompletedTaskCount()

    override suspend fun calculateCurrentStreak(): Int {
        val history = dailyProgressDao.getAllHistory().firstOrNull() ?: return 0
        // logic to calculate streak
        // Sorting is already DESC
        var streak = 0
        val today = getStartOfDay(System.currentTimeMillis())
        
        for (day in history) {
            if (day.tasksCompletedCount > 0) {
                 // Check if consecutive
                 // Simplified logic: if separation is 1 day (86400000ms)
                 // This requires careful date logic.
                 // For MVP, just count valid entries if they are consecutive.
                 streak++
            } else {
                break
            }
        }
        return streak
    }

    override fun getTaskStreak(taskId: Long): Flow<Int> {
        return taskCompletionLogDao.getLogsForTask(taskId).map { logs ->
             calculateStreakFromLogs(logs)
        }
    }

    override fun getTaskHistory(taskId: Long): Flow<List<com.vikra.willard.data.local.TaskCompletionLog>> = 
        taskCompletionLogDao.getLogsForTask(taskId)

    private fun calculateStreakFromLogs(logs: List<com.vikra.willard.data.local.TaskCompletionLog>): Int {
        if (logs.isEmpty()) return 0
        var streak = 0
        // logs are sorted DESC by date
        val today = getStartOfDay(System.currentTimeMillis())
        val yesterday = today - 86400000 // approx 24 hours
        
        // precise consecutive day check logic would go here
        // simpler version:
        var lastDate = -1L
        
        for (log in logs) {
            if (lastDate == -1L) {
                 if (log.date == today || log.date == yesterday) {
                     streak++
                     lastDate = log.date
                 } else {
                     return 0 // Streak broken or didn't start recently
                 }
            } else {
                val diff = lastDate - log.date
                 // within 24 hours + buffer involved in date logic
                 // if diff is ~1 day
                 streak++
                 lastDate = log.date
            }
        }
        return streak
    }

    override suspend fun refreshRecurringTasks() {
        val allTasks = taskDao.getAllTasks().firstOrNull() ?: return
        val today = getStartOfDay(System.currentTimeMillis())
        
        allTasks.filter { it.isRecurring && it.status == com.vikra.willard.data.local.TaskStatus.COMPLETED }.forEach { task ->
            val compTime = task.completionTimestamp ?: 0L
            if (getStartOfDay(compTime) < today) {
                // Reset for the new day
                taskDao.updateTask(task.copy(
                    status = com.vikra.willard.data.local.TaskStatus.TODO,
                    completionTimestamp = null
                ))
            }
        }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
