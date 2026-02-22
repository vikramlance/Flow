package com.flow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val dueDate: Long? = null,
    val startDate: Long = System.currentTimeMillis(), // Default to now
    val isRecurring: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completionTimestamp: Long? = null,
    /** Bitmask of scheduled days (bit 0=Mon … bit 6=Sun). Null = DAILY (all days). */
    val scheduleMask: Int? = null
) {
    val isCompleted: Boolean
        get() = status == TaskStatus.COMPLETED
}
