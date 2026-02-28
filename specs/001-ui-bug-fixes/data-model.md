# Data Model: Comprehensive UI Bug Fixes

**Feature**: 001-ui-bug-fixes  
**Date**: 2026-02-25  
**Based on**: research.md (all root causes resolved)

---

## Entities Affected

### TaskEntity (existing — no schema change)

| Field | Current Behavior | New Behavior after Fix |
|-------|-----------------|----------------------|
| `startDate: Long` | Normalised to midnight for all tasks | Recurring: midnight + 60,000 ms (12:01 AM). Non-recurring: unchanged (current working behavior). |
| `dueDate: Long?` | Normalised to midnight; null for recurring if not set | Recurring: midnight + 86,339,000 ms (11:59 PM). Non-recurring: defaults to end-of-today if null at creation time. |
| `completionTimestamp: Long?` | Preserved unconditionally in `updateTask()`; only cleared on COMPLETED→TODO | **Cleared whenever `task.status != COMPLETED`** in `updateTask()`. COMPLETED→IN_PROGRESS transition now valid and also clears it. |
| All other fields | Unchanged | Unchanged |

**Invariants to preserve**:
- `startDate < dueDate` must hold for all tasks. With [12:01 AM, 11:59 PM], this is guaranteed.
- `completionTimestamp` must be null if and only if `status != COMPLETED`.
- `dueDate` date-part must equal `startDate` date-part for recurring tasks (same calendar day). Enforced by always computing both from the same today-midnight base.

**Migration**: No schema migration required. Column types are unchanged. Existing tasks with midnight-to-midnight times are not auto-migrated (deferred per spec assumption 2).

---

### TaskCompletionLog (existing — no schema change)

| Field | Notes |
|-------|-------|
| `id: Long` | Auto-generated PK |
| `taskId: Long` | FK to tasks.id |
| `date: Long` | Midnight epoch for the completion day |
| `isCompleted: Boolean` | True = completed, false = undone |
| `timestamp: Long` | Wall-clock time of the event |

**UNIQUE index on (taskId, date)**: Already added in MIGRATION_4_5 (DB version 4→5). Current DB version is 7. No new migration needed.

**Upsert behavior**: `@Insert(onConflict = REPLACE)` on unique (taskId, date) deletes the old row and inserts a new one with the new `isCompleted` value. Ensures at most one log row per task per calendar day.

**Invariant to preserve**: A given (taskId, date) pair maps to exactly one row in `task_logs`.

---

### DailyProgressEntity (existing — no change)

Used by `upsertDailyProgress()`. Not affected by this feature. Present for reference.

---

## Derived State Changes

### TodayProgressState (data class, no schema change)

Currently computed from `getTasksDueOn(todayMidnight)` — exact match on midnight.

**After fix**: Computed from `getTasksDueInRange(todayStart, todayEnd)` where `todayStart = normaliseToMidnight(now)` and `todayEnd = todayStart + 86,399,999`. This range captures:
- New recurring tasks: dueDate = 11:59 PM (falls in range)
- New non-recurring tasks: dueDate = 11:59 PM (falls in range)
- Legacy tasks: dueDate = midnight (also falls in range, backward compatible)

**Invariant to preserve**: `completedToday <= totalToday`. Division by zero handled: if `totalToday == 0`, `ratio = 0f`.

---

## DAO Changes

### TaskDao — new query added

```
getTasksDueInRange(todayStart: Long, todayEnd: Long): Flow<List<TaskEntity>>
Query: SELECT * FROM tasks WHERE dueDate >= :todayStart AND dueDate <= :todayEnd
```

Old query `getTasksDueOn(todayMidnight)` kept for any legacy callers but no longer used by `getTodayProgress()`.

---

## Repository Behavioral Changes (no new tables or columns)

| Method | Change Type | Description |
|--------|-------------|-------------|
| `addTask()` | Logic change | If `isRecurring=true`, set `dueDate = getEndOfDay(normaliseToMidnight(startDate))` and `startDate = normaliseToMidnight(startDate) + 60_000L`. If `isRecurring=false && dueDate == null`, set `dueDate = getEndOfDay(normaliseToMidnight(now))`. |
| `refreshRecurringTasks()` | Logic change | Reset `startDate = today + 60_000L` and `dueDate = getEndOfDay(today)` instead of both = today midnight. |
| `updateTask()` | Behavioral fix | Set `completionTimestamp = null` when `task.status != COMPLETED`; otherwise preserve/accept caller's value. |
| `updateTaskStatus()` | Behavioral fix | Add COMPLETED→IN_PROGRESS as valid transition that clears completionTimestamp. |
| `getTodayProgress()` | Query change | Use `getTasksDueInRange(todayStart, todayEnd)` instead of `getTasksDueOn(todayMidnight)`. |

---

## State Model Changes

### AnalyticsUiState (no new fields)

The "Total" chip on Page 0 (Graph) derives its value from `heatMapData.values.sum()` computed inline in the composable — no new state field required. The existing `totalCompleted: Int` field is retained (used by Lifetime page) but Page 0 no longer reads it for the primary chip.

---

## Validation Rules (post-fix invariants)

| Rule | Check |
|------|-------|
| R1 | `dueDate != null` for all newly created tasks (both recurring and non-recurring) |
| R2 | For recurring tasks: `dueDate = normaliseToMidnight(startDate) + 86,339,000` |
| R3 | `completionTimestamp == null XOR status == COMPLETED` (for non-recurring tasks) |
| R4 | `task_logs.(taskId, date)` is unique — enforced by DB unique index (already in place) |
| R5 | Heat map never renders future months beyond `today` in CurrentYear default view |
| R6 | TimerViewModel's `durationSeconds` == `settingsRepository.defaultTimerMinutes * 60` when timer is idle |
