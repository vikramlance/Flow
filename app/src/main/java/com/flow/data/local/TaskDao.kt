package com.flow.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY status ASC, createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'COMPLETED'")
    fun getCompletedTaskCount(): Flow<Int>

    /** Tasks whose startDate is on or before today's midnight — active for today's progress. */
    @Query("SELECT * FROM tasks WHERE startDate <= :todayEnd ORDER BY status ASC, createdAt DESC")
    fun getTasksActiveToday(todayEnd: Long): Flow<List<TaskEntity>>

    /** Tasks that are overdue: dueDate in the past and not yet COMPLETED. */
    @Query("SELECT * FROM tasks WHERE dueDate IS NOT NULL AND dueDate < :now AND status != 'COMPLETED'")
    fun getOverdueTasks(now: Long): Flow<List<TaskEntity>>

    /** Count tasks completed (status=COMPLETED) whose dueDate is not null + completionTimestamp <= dueDate. */
    @Query("SELECT COUNT(*) FROM tasks WHERE completionTimestamp IS NOT NULL AND dueDate IS NOT NULL AND completionTimestamp <= dueDate")
    suspend fun getCompletedOnTimeCount(): Int

    /** Count tasks whose dueDate has passed and status is still not COMPLETED. */
    @Query("SELECT COUNT(*) FROM tasks WHERE dueDate IS NOT NULL AND dueDate < :now AND status != 'COMPLETED'")
    suspend fun getMissedDeadlineCount(now: Long): Int
}
