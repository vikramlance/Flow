package com.vikra.willard.data.repository

import com.vikra.willard.data.local.DailyProgressEntity
import com.vikra.willard.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getAllTasks(): Flow<List<TaskEntity>>
    fun getHistory(): Flow<List<DailyProgressEntity>>
    
    suspend fun addTask(title: String, startDate: Long = System.currentTimeMillis(), dueDate: Long? = null, isRecurring: Boolean = false)
    suspend fun updateTask(task: TaskEntity)
    suspend fun updateTaskStatus(task: TaskEntity, newStatus: com.vikra.willard.data.local.TaskStatus)
    suspend fun deleteTask(task: TaskEntity)
    
    // Stats
    fun getCompletedTaskCount(): Flow<Int>
    suspend fun calculateCurrentStreak(): Int
    fun getTaskStreak(taskId: Long): Flow<Int>
    fun getTaskHistory(taskId: Long): Flow<List<com.vikra.willard.data.local.TaskCompletionLog>>
    suspend fun refreshRecurringTasks()
}
