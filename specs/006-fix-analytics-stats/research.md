# Research: Fix Analytics Statistics & History State Sync

**Branch**: `006-fix-analytics-stats` | **Date**: 2026-02-28

All findings come from direct source inspection — no external unknowns remain.

---

## Finding 1 — Root cause: heatmap and total count exclude non-recurring completions

**Decision**: Fix `TaskRepositoryImpl.getHeatMapData(startMs, endMs)` to merge both storage sources.

**Root cause located in**:
`app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` — `getHeatMapData(startMs, endMs)`

```kotlin
// CURRENT (buggy): only reads task_logs (recurring completions)
override fun getHeatMapData(startMs: Long, endMs: Long): Flow<Map<Long, Int>> =
    taskCompletionLogDao.getLogsBetween(startMs, endMs).map { logs ->
        logs.groupBy { it.date }.mapValues { (_, v) -> v.size }
    }
```

Non-recurring task completions are stored permanently in `tasks.completionTimestamp` (status=COMPLETED). They are never written to `task_logs`. `getLogsBetween` only queries `task_logs`, so non-recurring completions are invisible to the heatmap.

The analytics "Total" chip on Page 0 derives its value from `heatMapData.values.sum()` (per 001-ui-bug-fixes fix). Since the heatmap undercounts, the total also undercounts.

**Fix**: Combine both sources inside `getHeatMapData`:
1. `task_logs WHERE isCompleted=1 AND date BETWEEN startMs AND endMs` — recurring, grouped by `date` (already midnight-epoch)
2. `tasks WHERE isRecurring=0 AND completionTimestamp IS NOT NULL AND completionTimestamp BETWEEN startMs AND endMs` — non-recurring, grouped by `normaliseToMidnight(completionTimestamp)`

Merge the two maps by date key, summing overlapping counts.

**Rationale**: Both storage locations already exist and are authoritative for their respective types. No schema change required. The repository is the correct layer for this merge (CO-001).

**Alternatives considered**:
- Add a separate `getHeatMapDataNonRecurring` flow and merge in ViewModel → rejected: breaks CO-001 (cross-layer data assembly), would require ViewModel changes and risk UI bugs
- Write non-recurring completions to `task_logs` on every completion → rejected: breaks DI-001 (two sources of truth for a single fact); requires migration; ripple effect on history queries

---

## Finding 2 — Root cause: on-time count misses all recurring completions

**Decision**: Fix `TaskRepositoryImpl.getCompletedOnTimeCount()` to include recurring completions from `task_logs`.

**Root cause located in**:
`app/src/main/java/com/flow/data/local/TaskDao.kt` — `getCompletedOnTimeCount()`

```sql
-- CURRENT (buggy): only tasks table; misses recurring completions
SELECT COUNT(*) FROM tasks
WHERE completionTimestamp IS NOT NULL AND dueDate IS NOT NULL AND completionTimestamp <= dueDate
```

Recurring tasks have `completionTimestamp` and `dueDate` on the `tasks` row only while they are in COMPLETED state. `refreshRecurringTasks()` resets them to TODO, clearing `completionTimestamp` and setting a new `dueDate`. After this reset, `tasks.completionTimestamp IS NULL` for that row, so historical recurring completions are excluded.

Historical recurring completions live in `task_logs` with `isCompleted=1`. The `dueDate` for recurring tasks is always `date + 86_340_000` (11:59 PM of the scheduled day), so any completion with `log.timestamp <= log.date + 86_340_000` is "on time". Since `refreshRecurringTasks` only resets tasks WHERE `completedDay < today`, a completion that occurred at 11:58 PM would still be "on time" by design.

**Fix**: `getCompletedOnTimeCount()` = (on-time non-recurring from `tasks` DAO query existing) + (recurring on-time from `task_logs` where `isCompleted=1 AND timestamp <= date + 86340000`).

New DAO query on `TaskCompletionLogDao`:
```sql
SELECT COUNT(*) FROM task_logs
WHERE isCompleted = 1 AND timestamp <= (date + 86340000)
```

`TaskRepositoryImpl.getCompletedOnTimeCount()` becomes:
```kotlin
taskDao.getCompletedOnTimeCount() + taskCompletionLogDao.getRecurringOnTimeCount()
```

**Rationale**: No schema change. `86_340_000` milliseconds = 23 hours 59 minutes, which matches `refreshRecurringTasks`'s `dueDate = today + 86_340_000L`.

**Alternatives considered**:
- Store `dueDate` in `task_logs` at completion time → allows exact on-time check but requires schema change + migration + ripple effect on all log insert sites; disproportionate for this fix
- Count ALL recurring completions as on-time (no timestamp check) → simpler but inaccurate if someone completes at 11:59:59 PM+1ms (edge case); the timestamp check is cheap and correct

---

## Finding 3 — Root cause: missed count only counts non-recurring tasks

**Decision**: Fix `TaskRepositoryImpl.getMissedDeadlineCount()` to also count recurring missed occurrences from `task_logs`.

**Root cause located in**:
`app/src/main/java/com/flow/data/local/TaskDao.kt` — `getMissedDeadlineCount(now)`

```sql
-- CURRENT (buggy): only tasks table; recurring tasks are reset to TODO after each day
SELECT COUNT(*) FROM tasks
WHERE dueDate IS NOT NULL AND dueDate < :now AND status != 'COMPLETED'
```

After `refreshRecurringTasks()`, a recurring task that was not completed yesterday is reset to TODO with today's `dueDate`. The yesterday "missed" occurrence is gone from the `tasks` table. However, `updateTaskStatus` writes a `TaskCompletionLog` entry for recurring tasks with `isCompleted = justCompleted`; it does this for COMPLETED transitions. But for tasks that simply MISS (never marked complete), no `task_logs` entry is created — they transition silently.

Wait — on re-inspection: `refreshRecurringTasks()` only resets tasks where `status == TaskStatus.COMPLETED`. A task that was NEVER completed (missed) and has `dueDate < today` stays in the `tasks` table with `status = TODO` and the OLD `dueDate` until `refreshRecurringTasks` runs. BUT `getMissedDeadlineCount` counts `dueDate < now AND status != COMPLETED`, which includes these overdue TODO recurring tasks.

However, once `refreshRecurringTasks` resets the recurring task for today (e.g., it was completed yesterday and reset to today), any past-missed log entries won't exist in `task_logs` because logs are only written via `updateTaskStatus` on user action (mark complete), not on miss.

The ACTUAL user-reported scenario: **3 recurring tasks missed, 2 non-recurring missed → shows 2**. This means the recurring missed tasks are being filtered out. The fix needed: `getMissedDeadlineCount` must count `task_logs WHERE isCompleted=0 AND date < today_midnight` as missed recurring occurrences, AND the `tasks` query must be scoped to `isRecurring=0` to avoid double-counting (since recurring tasks with dueDate in the past and status TODO would be counted by both the tasks query AND the logs query).

**Fix**:
1. Modify `TaskDao.getMissedDeadlineCount` to exclude recurring tasks: add `AND isRecurring = 0`
2. Add a new `TaskCompletionLogDao` query: `SELECT COUNT(*) FROM task_logs WHERE isCompleted = 0 AND date < :todayMidnight`
3. `TaskRepositoryImpl.getMissedDeadlineCount()` = non-recurring missed (tasks) + recurring missed (task_logs)

**Rationale**: This is the only approach that correctly handles the split storage model without schema changes. It is also the symmetric fix to Finding 2 (on-time), making all three analytics metrics consistent in their data sourcing.

---

## Finding 4 — Root cause: history list not reactive for recurring task status changes from history screen

**Decision**: Fix `GlobalHistoryViewModel.saveEditTask` to explicitly update the original `task_logs` entry when a recurring task transitions away from COMPLETED.

**Root cause located in**:
`app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt` — `saveEditTask()`

The bug sequence:
1. `saveEditTask(updated)` is called where `updated.status = TODO` (the new desired status)
2. It calls `repository.updateTaskStatus(updated, updated.status)` — passing the entity with the NEW status as both `task` and `newStatus`
3. `updateTaskStatus` validates: `when (task.status)` → `task.status = TODO` → `newStatus = TODO` → `validTransition = TODO→TODO = false` → **returns early, no log updated**
4. `repository.updateTask(updated.copy(completionTimestamp = null))` updates the `tasks` row correctly
5. `tasks` row now has `status = TODO, completionTimestamp = null` → task appears on Home screen ✓
6. `task_logs` entry (for the original completion date) still has `isCompleted = true` → task stays in history ✗

The fix does NOT require changing `updateTaskStatus` (which has correct logic for the Home screen path). Instead, `saveEditTask` must:
1. Read the ORIGINAL task from `_uiState.value.editingTask` to know the original status + completion date
2. When transitioning recurring task FROM COMPLETED → non-COMPLETED: find the specific `task_logs` row by `(taskId, date)` where `date = normaliseToMidnight(originalTask.completionTimestamp)` and set `isCompleted = false`
3. Call `repository.updateTask(updated.copy(...))` as before (task row update is already correct)

The repository needs `getLogForTaskDate(taskId, date): TaskCompletionLog?` exposed on the interface — the DAO already has it; it just needs a delegation in `TaskRepository` interface and `TaskRepositoryImpl`.

**Why this is correct**: The history list is driven by `getAllCompletedRecurringLogs()` which queries `task_logs WHERE isCompleted=1`. Once the specific log row is updated to `isCompleted=false`, Room's reactive Flow automatically removes the entry from the history list. This is exactly the same mechanism that already works for non-recurring tasks (via `getCompletedNonRecurringTasks()` which uses `completionTimestamp IS NOT NULL`).

**Alternatives considered**:
- Fix `updateTaskStatus` call arguments in `saveEditTask` → requires knowing the original status; `updateTaskStatus` uses `today`'s date to find the log but the original completion may be on a past date → still broken for past completions
- Create a dedicated `updateRecurringTaskStatusFromHistory(taskId, logId, newStatus)` method → cleaner but larger scope; deferred to a future refactor; the targeted log-update approach is minimal and correct (Principle IX)

---

## Finding 5 — Root cause: `getCurrentYearStats.completedThisYear` misses non-recurring completions

**Decision**: Fix `getCurrentYearStats()` to add non-recurring completions from the `tasks` table.

**Root cause located in**:
`app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` — `getCurrentYearStats()`

```kotlin
val logsThisYear = taskCompletionLogDao.getLogsBetween(jan1, dec31)
    .firstOrNull()?.filter { it.isCompleted } ?: emptyList()
val completedThisYear = logsThisYear.size  // only recurring
```

Only counts task_logs (recurring). Non-recurring completions this year from `tasks` table are ignored.

**Fix**: Add `taskDao.getCompletedNonRecurringThisYear(jan1, dec31)` count and sum with `logsThisYear.size`.

---

## Finding 6 — Root cause: `getLifetimeStats.totalCompleted` misses historical recurring completions

**Decision**: Fix `getLifetimeStats()` to count recurring completions from `task_logs`.

**Root cause located in**:
`app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` — `getLifetimeStats()`

```kotlin
val total = taskDao.getCompletedTaskCount().firstOrNull() ?: 0  // counts tasks WHERE status=COMPLETED
```

After `refreshRecurringTasks()`, recurring tasks are reset to TODO. So `COUNT(*) FROM tasks WHERE status='COMPLETED'` only reflects tasks CURRENTLY in COMPLETED state — it misses all historical recurring completions.

**Fix**: `total = (recurring log count from task_logs WHERE isCompleted=1) + (non-recurring from tasks WHERE status=COMPLETED AND isRecurring=0)`.

Note: `getCompletedTaskCount()` as a reactive Flow is used on the home screen to decide whether to populate dummy data. That usage should remain unchanged (it's a check for initial state, not an analytics count). The fix is isolated to `getLifetimeStats()`.

---

## Finding 7 — `getEarliestCompletionDate` misses non-recurring tasks (lower priority)

**Decision**: Fix `getEarliestCompletionDate()` to take the minimum of earliest log date AND earliest non-recurring completion date.

Currently: `MIN(date) FROM task_logs WHERE isCompleted=1` — returns null if only non-recurring tasks exist, making the analytics year range show only the current year.

**Fix**: `min(taskCompletionLogDao.getEarliestCompletionDate(), taskDao.getEarliestNonRecurringCompletionDate())` — take the lesser non-null value.

---

## Finding 8 — `getForestData` intentionally only shows recurring tasks (no bug)

`getForestData` uses `getRecurringLogsBetween` which joins `task_logs` with `tasks WHERE isRecurring=1`. The forest visualization represents recurring habits. This is by design. No change needed.

---

## Finding 9 — FR-011 Audit Results (T046)

**Date**: Post-implementation audit of all `AnalyticsUiState` fields for the recurrence-type exclusion pattern.

**Fields audited**:

| Field | Source | Status |
|---|---|---|
| `heatMapData` | `getHeatMapData(startMs, endMs)` | ✅ Fixed (T026) — now merges both sources |
| `completedOnTime` | `getCompletedOnTimeCount()` | ✅ Fixed (T027) — sums task_logs + tasks |
| `missedDeadlines` | `getMissedDeadlineCount()` | ✅ Fixed (T047) — sums task_logs + tasks |
| `lifetimeStats` | `getLifetimeStats()` | ✅ Fixed (T028) — total includes both sources |
| `currentYearStats` | `getCurrentYearStats()` | ✅ Fixed (T029) — year total includes non-recurring |
| `availableYears` | derived from `getEarliestCompletionDate()` | ✅ Fixed (T030) — min of both sources |
| `totalCompleted` | `getCompletedTaskCount()` (reactive Flow, ALL `status=COMPLETED` tasks) | ✅ Correct — already counts all task types by querying `tasks` table directly |
| `currentStreak` | `calculateCurrentStreak()` → only `task_logs` | ✅ By design — streak is a recurring-habit metric |
| `bestStreak` | `getBestStreak()` → only `task_logs` | ✅ By design — streak is a recurring-habit metric |
| `forestData` / `forestTreeCount` | `getForestData()` → `getRecurringLogsBetween()` | ✅ By design (Finding 8) — forest represents recurring habits only |

**Conclusion**: All metrics that logically should include both task types have been fixed. Streak and forest metrics intentionally use only recurring task data — these are habit-tracking concerns that do not apply to one-off tasks. **No further action required for T048a.**

---

## Summary of Affected Files

| File | Change |
|---|---|
| `data/local/TaskDao.kt` | Add `getCompletedNonRecurringInRange(startMs, endMs)`, `getEarliestNonRecurringCompletionDate()`; fix `getMissedDeadlineCount` to add `AND isRecurring = 0` |
| `data/local/TaskCompletionLogDao.kt` | Add `getRecurringOnTimeCount()`, `getRecurringMissedCount(todayMidnight)`, `getTotalCompletedLogCount()` |
| `data/repository/TaskRepository.kt` | Expose `getLogForTaskDate(taskId, date)` |
| `data/repository/TaskRepositoryImpl.kt` | Fix `getHeatMapData`, `getCompletedOnTimeCount`, `getMissedDeadlineCount`, `getLifetimeStats`, `getCurrentYearStats`, `getEarliestCompletionDate`; implement `getLogForTaskDate` |
| `presentation/history/GlobalHistoryViewModel.kt` | Fix `saveEditTask` to update original log entry for recurring tasks |
| Test files (unit) | New tests in `AnalyticsViewModelTest`, `GlobalHistoryViewModelTest`, `TaskRepositoryImplTest` |
| Test files (instrumented) | New DAO tests, `HistoryScreenTest` expansion |
