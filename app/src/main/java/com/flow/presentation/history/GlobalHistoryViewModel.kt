package com.flow.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.data.local.TaskCompletionLog
import com.flow.data.local.TaskEntity
import com.flow.data.local.TaskStatus
import com.flow.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * T012 — ViewModel for the Global History screen.
 *
 * Combines three repository flows to produce a merged [List<HistoryItem>]:
 *  - Source A: [TaskRepository.getAllCompletedRecurringLogs] (recurring completions)
 *  - Source B: [TaskRepository.getCompletedNonRecurringTasks] (one-off completed tasks)
 *  - All tasks: [TaskRepository.getAllTasks] (needed for title/dueDate lookup when joining logs)
 */
@HiltViewModel
class GlobalHistoryViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalHistoryUiState())
    val uiState: StateFlow<GlobalHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllCompletedRecurringLogs(),
                repository.getCompletedNonRecurringTasks(),
                repository.getAllTasks()
            ) { logs, nonRecurring, allTasks ->
                buildHistoryItems(logs, nonRecurring, allTasks)
            }.collect { items ->
                _uiState.update { state ->
                    state.copy(
                        allItems = items,
                        datesWithData = items.map { it.completedDayMidnight }.toSet(),
                        isLoading = false
                    )
                }
            }
        }
    }

    // ── User interactions ────────────────────────────────────────────────────

    /** Select a specific day to filter by; pass null to show all dates. */
    fun selectDate(dateMs: Long?) = _uiState.update { it.copy(selectedDateMidnight = dateMs) }

    fun setViewMode(mode: HistoryViewMode) = _uiState.update { it.copy(viewMode = mode) }

    fun setFilterMode(mode: HistoryFilterMode) = _uiState.update { it.copy(filterMode = mode) }

    /** Toggle "Show All" flat chronological view (deselects date and switches mode). */
    fun toggleShowAll() = _uiState.update { state ->
        val next = !state.showAll
        state.copy(
            showAll = next,
            selectedDateMidnight = if (next) null else state.selectedDateMidnight,
            viewMode = if (next) HistoryViewMode.CHRONOLOGICAL else HistoryViewMode.DATE_GROUPED
        )
    }

    // ── T034-T035: History editing ────────────────────────────────────────────

    /** Open the edit dialog for the given log entry. */
    fun openEditLog(log: com.flow.data.local.TaskCompletionLog) =
        _uiState.update { it.copy(editingLog = log, editError = null) }

    /** Dismiss the edit dialog without saving. */
    fun dismissEditLog() =
        _uiState.update { it.copy(editingLog = null, editError = null) }

    /** Clear the current edit error message. */
    fun clearEditError() =
        _uiState.update { it.copy(editError = null) }

    // ── T020/FR-005: Task edit from history ──────────────────────────────────

    /**
     * Open the [TaskEditSheet] for the given task.
     * If the task no longer exists (deleted), set error and DO NOT open the sheet.
     */
    fun openEditTask(taskId: Long) {
        viewModelScope.launch {
            val task = repository.getTaskById(taskId)
            if (task == null) {
                _uiState.update { it.copy(error = "Task not found") }
            } else {
                _uiState.update { it.copy(editingTask = task, error = null) }
            }
        }
    }

    /** Dismiss the TaskEditSheet without saving. */
    fun dismissEditTask() =
        _uiState.update { it.copy(editingTask = null) }

    /**
     * Validate and save an edited [TaskEntity] from the history edit sheet.
     *
     * (a) If the new status is not COMPLETED, clear completionTimestamp so the task
     *     disappears from [getCompletedNonRecurringTasks()].
     * (b) Calls repository.updateTaskStatus() to handle completionTimestamp side-effects,
     *     then repository.updateTask() which persists dueDate exactly as received (FR-001).
     */
    fun saveEditTask(updated: TaskEntity) {
        viewModelScope.launch {
            // Step (a): status side-effect (sets/clears completionTimestamp)
            repository.updateTaskStatus(updated, updated.status)
            // Step (b): persist all other field changes, preserving the
            // completionTimestamp that updateTaskStatus just set/cleared
            repository.updateTask(
                updated.copy(
                    completionTimestamp = if (updated.status == TaskStatus.COMPLETED) updated.completionTimestamp else null
                )
            )
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    /** Open the action bottom sheet for a recurring-task long-press. */
    fun openActionSheet(item: HistoryItem) =
        _uiState.update { it.copy(showActionSheet = true, actionSheetTarget = item) }

    /** Dismiss the action bottom sheet without taking any action. */
    fun dismissActionSheet() =
        _uiState.update { it.copy(showActionSheet = false, actionSheetTarget = null) }

    /**
     * T035: Validate and save an edited [TaskCompletionLog].
     * Validates:
     *  - Task still exists in the DB
     *  - completionDate is not in the future
     *  - completionDate >= task.startDate
     * Then saves via [repository.updateLog] and recalculates streaks.
     */
    fun updateLogEntry(log: com.flow.data.local.TaskCompletionLog) {
        viewModelScope.launch {
            val task = repository.getTaskById(log.taskId)
            if (task == null) {
                _uiState.update { it.copy(editError = "Task no longer exists") }
                return@launch
            }

            val today = normaliseToMidnight(System.currentTimeMillis()) + 86_399_999L  // end of today
            if (log.date > today) {
                _uiState.update { it.copy(editError = "Completion date cannot be in the future") }
                return@launch
            }

            if (log.date < task.startDate) {
                _uiState.update { it.copy(editError = "Completion date cannot be before the task start date") }
                return@launch
            }

            repository.updateLog(log)
            repository.recalculateStreaks(log.taskId)
            _uiState.update { it.copy(editingLog = null, editError = null) }
        }
    }

    // ── Derived list (called from composable) ────────────────────────────────

    /**
     * Returns the display-ready list derived from [state].
     * Apply date filter → apply filter mode → sorting is already handled in [buildHistoryItems].
     */
    fun filteredItems(state: GlobalHistoryUiState): List<HistoryItem> {
        if (state.showAll) return state.allItems

        val groupKey: (HistoryItem) -> Long? = when (state.filterMode) {
            HistoryFilterMode.COMPLETED_ON -> { item -> item.completedDayMidnight }
            HistoryFilterMode.TARGET_DATE  -> { item -> item.targetDate?.let { normaliseToMidnight(it) } }
        }
        return if (state.selectedDateMidnight == null) {
            state.allItems
        } else {
            state.allItems.filter { groupKey(it) == state.selectedDateMidnight }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun buildHistoryItems(
        logs: List<TaskCompletionLog>,
        nonRecurring: List<TaskEntity>,
        allTasks: List<TaskEntity>
    ): List<HistoryItem> {
        val taskMap = allTasks.associateBy { it.id }

        // Source A — recurring: join log entries with task metadata
        val recurringItems = logs.mapNotNull { log ->
            val task = taskMap[log.taskId] ?: return@mapNotNull null
            HistoryItem(
                taskId = task.id,
                taskTitle = task.title,
                targetDate = task.dueDate,
                completedDayMidnight = log.date,
                completedAtMs = log.timestamp,
                isRecurring = true,
                logId = log.id
            )
        }

        // Source B — non-recurring: derive day from completionTimestamp
        val nonRecurringItems = nonRecurring.mapNotNull { task ->
            val completedAt = task.completionTimestamp ?: return@mapNotNull null
            HistoryItem(
                taskId = task.id,
                taskTitle = task.title,
                targetDate = task.dueDate,
                completedDayMidnight = normaliseToMidnight(completedAt),
                completedAtMs = completedAt,
                isRecurring = false
            )
        }

        return (recurringItems + nonRecurringItems)
            .sortedByDescending { it.completedAtMs }
    }

    private fun normaliseToMidnight(timestamp: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
