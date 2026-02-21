package com.flow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyProgressDao {
    @Query("SELECT * FROM daily_progress ORDER BY date DESC")
    fun getAllHistory(): Flow<List<DailyProgressEntity>>

    @Query("SELECT * FROM daily_progress WHERE date = :date")
    suspend fun getProgressForDate(date: Long): DailyProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: DailyProgressEntity)
    
    @Query("UPDATE daily_progress SET tasksCompletedCount = tasksCompletedCount + 1 WHERE date = :date")
    suspend fun incrementCompletedCount(date: Long)
}
