# Internal Contracts: Fix Analytics Statistics & History State Sync

**Branch**: `006-fix-analytics-stats` | **Date**: 2026-02-28

---

## Contract 1 — DAO: `TaskDao` new / modified queries

**Layer**: `data/local/TaskDao.kt`

### New: `getCompletedNonRecurringInRange(startMs, endMs)`

```kotlin
@Query("""
    SELECT completionTimestamp FROM tasks
    WHERE isRecurring = 0
      AND completionTimestamp IS NOT NULL
      AND completionTimestamp >= :startMs
      AND completionTimestamp <= :endMs
""")
fun getCompletedNonRecurringInRange(startMs: Long, endMs: Long): Flow<List<Long>>
```

**Returns**: Reactive list of `completionTimestamp` epoch-ms values for completed non-recurring tasks within the range.
**Used by**: `TaskRepositoryImpl.getHeatMapData(startMs, endMs)` — one half of the merged heatmap.

---

### New: `getEarliestNonRecurringCompletionDate()`

```kotlin
@Query("SELECT MIN(completionTimestamp) FROM tasks WHERE isRecurring = 0 AND completionTimestamp IS NOT NULL")
suspend fun getEarliestNonRecurringCompletionDate(): Long?
```

**Returns**: Earliest `completionTimestamp` of any non-recurring completed task; null if none.
**Used by**: `TaskRepositoryImpl.getEarliestCompletionDate()` — to correctly compute the first available year for analytics.

---

### Modified: `getMissedDeadlineCount(now)`

Add `AND isRecurring = 0` to the existing query:

```kotlin
@Query("""
    SELECT COUNT(*) FROM tasks
    WHERE dueDate IS NOT NULL
      AND dueDate < :now
      AND status != 'COMPLETED'
      AND isRecurring = 0
""")
suspend fun getMissedDeadlineCount(now: Long): Int
```

**Invariant**: Does not break any existing callers — they previously counted recurring missed tasks here (incorrect); after the fix, recurring missed tasks are counted separately from `task_logs`. Net result is the same for non-recurring tasks; recurring tasks are now counted correctly.

---

## Contract 2 — DAO: `TaskCompletionLogDao` new queries

**Layer**: `data/local/TaskCompletionLogDao.kt`

### New: `getRecurringOnTimeCount()`

```kotlin
@Query("""
    SELECT COUNT(*) FROM task_logs
    WHERE isCompleted = 1
      AND timestamp <= (date + 86340000)
""")
suspend fun getRecurringOnTimeCount(): Int
```

**Invariant**: `86340000` = 23 h 59 m in milliseconds, matching `refreshRecurringTasks`'s `dueDate = today + 86_340_000L` for recurring tasks.
**Used by**: `TaskRepositoryImpl.getCompletedOnTimeCount()`.

---

### New: `getRecurringMissedCount(todayMidnight)`

```kotlin
@Query("""
    SELECT COUNT(*) FROM task_logs
    WHERE isCompleted = 0
      AND date < :todayMidnight
""")
suspend fun getRecurringMissedCount(todayMidnight: Long): Int
```

**Invariant**: Only counts logs for PAST days (`date < todayMidnight`). Today's in-progress recurring tasks (date = today) are not yet missed.
**Used by**: `TaskRepositoryImpl.getMissedDeadlineCount()`.

---

### New: `getTotalCompletedLogCount()`

```kotlin
@Query("SELECT COUNT(*) FROM task_logs WHERE isCompleted = 1")
suspend fun getTotalCompletedLogCount(): Int
```

**Used by**: `TaskRepositoryImpl.getLifetimeStats()` — recurring half of `totalCompleted`.

---

## Contract 3 — Repository interface: new method exposure

**Layer**: `data/repository/TaskRepository.kt`

```kotlin
/** Look up a single TaskCompletionLog by task and calendar-day; null if not found. */
suspend fun getLogForTaskDate(taskId: Long, date: Long): TaskCompletionLog?
```

**Invariant**: Pure passthrough to `TaskCompletionLogDao.getLogForTaskDate(taskId, date)`. Already implemented in the DAO; this addition just exposes it through the interface so the ViewModel can call it.

---

## Contract 4 — Repository implementation: `getHeatMapData` merge

**Layer**: `data/repository/TaskRepositoryImpl.kt`

```kotlin
override fun getHeatMapData(startMs: Long, endMs: Long): Flow<Map<Long, Int>> {
    val recurringFlow    = taskCompletionLogDao.getLogsBetween(startMs, endMs)
    val nonRecurringFlow = taskDao.getCompletedNonRecurringInRange(startMs, endMs)
    return combine(recurringFlow, nonRecurringFlow) { logs, timestamps ->
        val fromLogs = logs.groupBy { it.date }.mapValues { (_, v) -> v.size }
        val fromTasks = timestamps
            .groupBy { normaliseToMidnight(it) }
            .mapValues { (_, v) -> v.size }
        (fromLogs.keys + fromTasks.keys).associateWith { key ->
            (fromLogs[key] ?: 0) + (fromTasks[key] ?: 0)
        }
    }
}
```

**Invariant**: Both source Flows are reactive (Room observes table changes). Any new completion in either table triggers a re-emit. The merge preserves this reactivity.

---

## Contract 5 — Repository implementation: scalar count fixes

**Layer**: `data/repository/TaskRepositoryImpl.kt`

### `getCompletedOnTimeCount()`
```kotlin
override suspend fun getCompletedOnTimeCount(): Int =
    taskDao.getCompletedOnTimeCount() + taskCompletionLogDao.getRecurringOnTimeCount()
```
Note: `TaskDao.getCompletedOnTimeCount()` already has `AND completionTimestamp IS NOT NULL AND dueDate IS NOT NULL AND completionTimestamp <= dueDate`. Adding `AND isRecurring = 0` is not strictly necessary (recurring tasks set completionTimestamp transiently, but after refreshRecurringTasks it is null, so they won't be counted). However, adding the guard is defensive and prevents transient double-counting in the window before refresh. Add `AND isRecurring = 0` to `TaskDao.getCompletedOnTimeCount()` to be explicit.

### `getMissedDeadlineCount()`
```kotlin
override suspend fun getMissedDeadlineCount(): Int {
    val todayMidnight = normaliseToMidnight(System.currentTimeMillis())
    return taskDao.getMissedDeadlineCount(System.currentTimeMillis()) +
           taskCompletionLogDao.getRecurringMissedCount(todayMidnight)
}
```

### `getLifetimeStats()`
```kotlin
val totalRecurring    = taskCompletionLogDao.getTotalCompletedLogCount()
val totalNonRecurring = taskDao.getAllTasks().firstOrNull()
    ?.count { !it.isRecurring && it.status == TaskStatus.COMPLETED } ?: 0
val total = totalRecurring + totalNonRecurring
```

### `getCurrentYearStats()`
```kotlin
val completedThisYear = logsThisYear.size +  // recurring (existing)
    taskDao.getAllTasks().firstOrNull()
        ?.count { !it.isRecurring && it.completionTimestamp != null && it.completionTimestamp in jan1..dec31 }
        ?: 0
```

### `getEarliestCompletionDate()`
```kotlin
override suspend fun getEarliestCompletionDate(): Long? {
    val logEarliest  = taskCompletionLogDao.getEarliestCompletionDate()
    val taskEarliest = taskDao.getEarliestNonRecurringCompletionDate()
    return listOfNotNull(logEarliest, taskEarliest).minOrNull()
}
```

---

## Contract 6 — ViewModel: `GlobalHistoryViewModel.saveEditTask` fix

**Layer**: `presentation/history/GlobalHistoryViewModel.kt`

```kotlin
fun saveEditTask(updated: TaskEntity) {
    viewModelScope.launch {
        val originalTask = _uiState.value.editingTask ?: return@launch

        // For recurring tasks: if transitioning away from COMPLETED,
        // find the specific log entry and mark it not-completed so the
        // history list reactively removes it (CO-001: fix at data layer, not UI).
        if (updated.isRecurring
            && originalTask.status == TaskStatus.COMPLETED
            && updated.status != TaskStatus.COMPLETED
        ) {
            val completionDate = originalTask.completionTimestamp
                ?.let { normaliseToMidnight(it) }
            if (completionDate != null) {
                val log = repository.getLogForTaskDate(updated.id, completionDate)
                if (log != null) {
                    repository.updateLog(log.copy(isCompleted = false))
                }
            }
        }

        // Persist the task row — status + completionTimestamp side effects
        repository.updateTask(
            updated.copy(
                completionTimestamp = if (updated.status == TaskStatus.COMPLETED)
                    updated.completionTimestamp else null
            )
        )
        _uiState.update { it.copy(editingTask = null) }
    }
}
```

**Invariants**:
- No change to the non-recurring path (originalTask.isRecurring == false → the `if` block is skipped)
- No change to the Home screen path (Home screen uses `updateTaskStatus` directly on the ViewModel, not `saveEditTask`)
- `updateLog(log.copy(isCompleted = false))` triggers Room's Flow for `getAllCompletedRecurringLogs()` → history list updates reactively without manual UI refresh
- If `completionDate == null` or log not found (edge case): silently no-ops; the task row is still updated correctly

---

## Non-Regression Coverage (AL-002)

| Existing flow | How verified |
|---|---|
| One-time task status change from history removes it from history | `isRecurring = false` → new `if` block in `saveEditTask` is skipped; existing `updateTask` path unchanged |
| All task types status change from Home screen | Home screen uses `HomeViewModel.updateTaskStatus` → `repository.updateTaskStatus` → log updated via `today`-date logic; this path is NOT changed |
| Analytics period filters (daily/monthly/yearly) | `getHeatMapData(startMs, endMs)` still accepts the same parameters; the merge is additive; period logic in ViewModel unchanged |
| Graph rendering | `heatMap` map structure (`Map<Long, Int>`) is identical; only values (counts) change |
| Forest data | `getForestData` unchanged — intentionally recurring-only |
| Streak/achievement triggers | `updateTaskStatus` code path unchanged |
