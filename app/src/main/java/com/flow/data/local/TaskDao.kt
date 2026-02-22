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
    @Query("SELECT * FROM tasks ORDER BY createdAt ASC")
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

    /**
     * T002 — Home-screen filtered list (4-rule logic, FR-004):
     *  1. Recurring tasks (always shown)
     *  2. Dated tasks due today
     *  3. Overdue dated tasks (due before today, not yet completed)
     *  4. General undated non-recurring tasks (incomplete, or completed today)
     */
    @Query("""
        SELECT * FROM tasks
        WHERE
            isRecurring = 1
            OR (dueDate IS NOT NULL AND dueDate >= :todayStart AND dueDate < :tomorrowStart)
            OR (dueDate IS NOT NULL AND dueDate < :todayStart AND status != 'COMPLETED')
            OR (dueDate IS NULL AND isRecurring = 0
                AND (status != 'COMPLETED'
                     OR (completionTimestamp IS NOT NULL AND completionTimestamp >= :todayStart)))
        ORDER BY createdAt ASC
    """)
    fun getHomeScreenTasks(todayStart: Long, tomorrowStart: Long): Flow<List<TaskEntity>>

    /** T003 — All completed non-recurring tasks (for global history), ordered by completion time. */
    @Query("""
        SELECT * FROM tasks
        WHERE completionTimestamp IS NOT NULL AND isRecurring = 0
        ORDER BY completionTimestamp DESC
    """)
    fun getCompletedNonRecurringTasks(): Flow<List<TaskEntity>>

    /**
     * FR-001: Tasks with dueDate exactly equal to today's midnight epoch.
     * Used by getTodayProgress() to count only tasks actually due today.
     */
    @Query("SELECT * FROM tasks WHERE dueDate = :todayMidnight")
    fun getTasksDueOn(todayMidnight: Long): Flow<List<TaskEntity>>
}
