package com.vikra.willard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the summary for a specific day to power the "GitHub-style" heatmap.
 * date: Represents the start of the day (midnight) in millis.
 */
@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey
    val date: Long, 
    val tasksCompletedCount: Int = 0,
    val tasksTotalCount: Int = 0
)
