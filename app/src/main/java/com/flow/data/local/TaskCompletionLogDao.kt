package com.flow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Projection DTO used by [TaskCompletionLogDao.getRecurringLogsBetween]. */
data class RecurringLogEntry(
    val completionDate: Long,   // midnight epoch of the day
    val taskTitle: String
)

@Dao
interface TaskCompletionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TaskCompletionLog)

    /** T012 — Update an existing log entry (for history editing). */
    @Update
    suspend fun updateLog(log: TaskCompletionLog)

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId ORDER BY date DESC")
    fun getLogsForTask(taskId: Long): Flow<List<TaskCompletionLog>>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId AND date = :date LIMIT 1")
    suspend fun getLogForTaskDate(taskId: Long, date: Long): TaskCompletionLog?

    @Query("DELETE FROM task_logs WHERE taskId = :taskId")
    suspend fun deleteLogsForTask(taskId: Long)

    /** T004 — All completed log entries (isCompleted=1) for global history, newest first. */
    @Query("SELECT * FROM task_logs WHERE isCompleted = 1 ORDER BY date DESC, timestamp DESC")
    fun getAllCompletedLogs(): Flow<List<TaskCompletionLog>>

    /**
     * T012 — All completed recurring-task log entries between [startMs] and [endMs] (inclusive),
     * joined with the task title. Used by [getForestData].
     */
    @Query("""
        SELECT tl.date AS completionDate, t.title AS taskTitle
        FROM task_logs tl
        INNER JOIN tasks t ON tl.taskId = t.id
        WHERE tl.isCompleted = 1
          AND t.isRecurring = 1
          AND tl.date >= :startMs
          AND tl.date <= :endMs
        ORDER BY tl.date ASC
    """)
    fun getRecurringLogsBetween(startMs: Long, endMs: Long): Flow<List<RecurringLogEntry>>

    /** All completed logs between two epoch-ms bounds (for heatmap). */
    @Query("""
        SELECT * FROM task_logs
        WHERE isCompleted = 1
          AND date >= :startMs
          AND date <= :endMs
        ORDER BY date ASC
    """)
    fun getLogsBetween(startMs: Long, endMs: Long): Flow<List<TaskCompletionLog>>

    /** Non-reactive: all completed logs for a task (used by StreakCalculator). */
    @Query("SELECT * FROM task_logs WHERE taskId = :taskId AND isCompleted = 1 ORDER BY date ASC")
    suspend fun getCompletedLogsForTask(taskId: Long): List<TaskCompletionLog>

    /** Non-reactive: earliest completion date across all tasks; null if no data. */
    @Query("SELECT MIN(date) FROM task_logs WHERE isCompleted = 1")
    suspend fun getEarliestCompletionDate(): Long?
}
