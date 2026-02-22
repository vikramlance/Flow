package com.flow.presentation.history

/**
 * T010 — Presentation model for a single completed-task entry in the Global History screen.
 *
 * Constructed by [GlobalHistoryViewModel] by combining two data sources:
 *  - Source A: [com.flow.data.local.TaskCompletionLog] entries (recurring tasks, isCompleted=true)
 *  - Source B: [com.flow.data.local.TaskEntity] with completionTimestamp set (non-recurring)
 *
 * Not persisted — derived in memory on every DB update.
 */
data class HistoryItem(
    /** FK to [com.flow.data.local.TaskEntity.id] — for task-streak navigation if needed. */
    val taskId: Long,
    /** Display name, copied from [com.flow.data.local.TaskEntity.title]. */
    val taskTitle: String,
    /** Optional due-date from the owning task; may be null for undated tasks. */
    val targetDate: Long?,
    /** Midnight epoch of the day this completion occurred — used for date grouping and filtering. */
    val completedDayMidnight: Long,
    /** Exact epoch-ms of completion — used for sort order within a day. */
    val completedAtMs: Long,
    /** True for recurring tasks (shown with 🌱 label); false for one-off tasks. */
    val isRecurring: Boolean,
    /** T036: PK of the backing [com.flow.data.local.TaskCompletionLog] row; null for non-recurring tasks. */
    val logId: Long? = null
)
