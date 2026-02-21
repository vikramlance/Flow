package com.flow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_logs")
data class TaskCompletionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val date: Long, // Midnight timestamp representing the day
    val isCompleted: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
