package com.flow.presentation.home

import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.repository.TodayProgressState

// ── T022: Urgency colour coding ──────────────────────────────────────────────

/**
 * Urgency level for a future-dated task, based on elapsed time between startDate and dueDate.
 * NONE for completed, overdue, or tasks without both dates.
 */
enum class UrgencyLevel { NONE, GREEN, YELLOW, ORANGE }

/**
 * Compute urgency for a task card.
 *  - NONE:   completed, no dueDate, or overdue (dueDate < today)
 *  - GREEN:  elapsed < 50 %
 *  - YELLOW: 50 % ≤ elapsed < 80 %
 *  - ORANGE: elapsed ≥ 80 % (still in future)
 */
fun TaskEntity.urgencyLevel(today: Long = System.currentTimeMillis()): UrgencyLevel {
    if (status == TaskStatus.COMPLETED) return UrgencyLevel.NONE
    val due = dueDate ?: return UrgencyLevel.NONE
    if (due < today) return UrgencyLevel.NONE               // overdue — existing red handles it
    val total = due - startDate
    if (total <= 0L) return UrgencyLevel.ORANGE             // startDate == dueDate
    val elapsed = (today - startDate).coerceAtLeast(0L).toFloat() / total.toFloat()
    return when {
        elapsed >= 0.80f -> UrgencyLevel.ORANGE
        elapsed >= 0.50f -> UrgencyLevel.YELLOW
        else             -> UrgencyLevel.GREEN
    }
}

/** Wrapper carrying a task together with its pre-computed urgency level. */
data class HomeTaskItem(
    val task: TaskEntity,
    val urgency: UrgencyLevel
)

// ── T019: Home UI state ───────────────────────────────────────────────────────

data class HomeUiState(
    /** Mapped task list with per-item urgency colours. */
    val homeTasks: List<HomeTaskItem> = emptyList(),
    /** Future-dated tasks (dueDate >= tomorrow, not completed) for the Upcoming section. */
    val upcomingTasks: List<HomeTaskItem> = emptyList(),
    /** Today-focused progress (FR-001). */
    val todayProgressState: TodayProgressState = TodayProgressState(0, 0),
    val isFirstLaunch: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,           // non-null triggers error banner
    val showHelp: Boolean = false
) {
    /** Convenience accessor — keeps existing HomeScreen code backwards-compatible. */
    val todayProgress: Float get() = todayProgressState.ratio
}
