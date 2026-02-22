package com.flow.presentation.history

/**
 * T011 — Controls how items are rendered in the Global History list.
 */
enum class HistoryViewMode {
    /** Tasks grouped under collapsible date-section headers, most recent date first. */
    DATE_GROUPED,
    /** Flat chronological list sorted by completedAtMs DESC, with inline date/time per row. */
    CHRONOLOGICAL
}

/**
 * T011 — Controls which timestamp is used for date grouping / filtering.
 */
enum class HistoryFilterMode {
    /** Group and filter by the day the task was completed (completedDayMidnight). */
    COMPLETED_ON,
    /** Group and filter by the task's target/due date; null-dueDate items appear under "No Target". */
    TARGET_DATE
}

/**
 * T011 — UI state for [GlobalHistoryScreen].
 *
 * @property allItems         Full unfiltered list of history items.
 * @property selectedDateMidnight Null = show all dates; non-null = filter to that midnight epoch.
 * @property datesWithData    Set of midnight timestamps that have at least one history entry
 *                            (drives highlighted dots on the date strip).
 * @property viewMode         DATE_GROUPED or CHRONOLOGICAL display.
 * @property filterMode       Whether to group/filter by completion-day or target-date.
 * @property isLoading        True until the first DB snapshot arrives.
 * @property error            Non-null string triggers an error banner.
 * @property showAll          True when the "Show All" chip is active (flat chronological, no filter).
 */
data class GlobalHistoryUiState(
    val allItems: List<HistoryItem> = emptyList(),
    val selectedDateMidnight: Long? = null,
    val datesWithData: Set<Long> = emptySet(),
    val viewMode: HistoryViewMode = HistoryViewMode.DATE_GROUPED,
    val filterMode: HistoryFilterMode = HistoryFilterMode.COMPLETED_ON,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showAll: Boolean = false,
    /** T034: Log entry currently open in the edit dialog; null = no dialog. */
    val editingLog: com.flow.data.local.TaskCompletionLog? = null,
    /** T034: Non-null when a validation error occurred during log edit. */
    val editError: String? = null
)
