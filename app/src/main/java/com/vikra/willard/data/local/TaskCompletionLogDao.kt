package com.vikra.willard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCompletionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TaskCompletionLog)

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId ORDER BY date DESC")
    fun getLogsForTask(taskId: Long): Flow<List<TaskCompletionLog>>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId AND date = :date LIMIT 1")
    suspend fun getLogForTaskDate(taskId: Long, date: Long): TaskCompletionLog?
    
    @Query("DELETE FROM task_logs WHERE taskId = :taskId")
    suspend fun deleteLogsForTask(taskId: Long)
}
