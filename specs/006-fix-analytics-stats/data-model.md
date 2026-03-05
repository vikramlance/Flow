# Data Model: Fix Analytics Statistics & History State Sync

**Branch**: `006-fix-analytics-stats` | **Date**: 2026-02-28

## Existing Entities (unchanged schema — no migrations required)

### `tasks` (TaskEntity)

Stores every task. Relevant fields for this fix:

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK) | Auto-generated |
| `isRecurring` | Boolean | True = recurring task series, False = one-time |
| `status` | TaskStatus | TODO / IN_PROGRESS / COMPLETED |
| `completionTimestamp` | Long? | Epoch-ms of completion; null when not completed |
| `dueDate` | Long? | For recurring: 11:59 PM of scheduled day; for one-time: user-set or null |
| `startDate` | Long | For recurring: 12:01 AM of scheduled day after reset |

**Invariant preserved**: `completionTimestamp` IS the single record of a non-recurring task's completion. For recurring tasks, `completionTimestamp` is transient — cleared by `refreshRecurringTasks()` when a new day begins.

### `task_logs` (TaskCompletionLog)

Stores one row per (task, calendar-day) for recurring task completion tracking.

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK) | Auto-generated |
| `taskId` | Long (FK → tasks.id) | |
| `date` | Long | Midnight epoch of the scheduled day |
| `isCompleted` | Boolean | True = completed that day; False = not yet or was un-completed |
| `timestamp` | Long | Epoch-ms when the log was written (defaults to `System.currentTimeMillis()`) |

**Invariant preserved**: A log with `isCompleted = false` and `date < today_midnight` represents a missed recurring occurrence. A log with `isCompleted = true` represents a completed recurring occurrence.

## No Schema Changes

All fixes are purely in DAO queries and repository logic. The existing tables are unchanged.

This satisfies **DI-002**: no migration is needed.

## New DAO Queries (additions only — no changes to existing queries)

### `TaskDao` additions

| Method | SQL | Purpose |
|---|---|---|
| `getCompletedNonRecurringInRange(startMs, endMs)` | `SELECT completionTimestamp FROM tasks WHERE isRecurring=0 AND completionTimestamp IS NOT NULL AND completionTimestamp BETWEEN :startMs AND :endMs` | Heatmap: non-recurring completions in date range |
| `getEarliestNonRecurringCompletionDate()` | `SELECT MIN(completionTimestamp) FROM tasks WHERE isRecurring=0 AND completionTimestamp IS NOT NULL` | Earliest date for year-range calculation |
| (modify) `getMissedDeadlineCount` | Add `AND isRecurring = 0` to existing query | Exclude recurring tasks so they are counted from task_logs instead |

### `TaskCompletionLogDao` additions

| Method | SQL | Purpose |
|---|---|---|
| `getRecurringOnTimeCount()` | `SELECT COUNT(*) FROM task_logs WHERE isCompleted=1 AND timestamp <= (date + 86340000)` | On-time recurring completions (within the scheduled day) |
| `getRecurringMissedCount(todayMidnight)` | `SELECT COUNT(*) FROM task_logs WHERE isCompleted=0 AND date < :todayMidnight` | Missed recurring occurrences (past days, not completed) |
| `getTotalCompletedLogCount()` | `SELECT COUNT(*) FROM task_logs WHERE isCompleted=1` | Lifetime total of recurring completions |

## Repository Interface Addition

```
suspend fun getLogForTaskDate(taskId: Long, date: Long): TaskCompletionLog?
```

Already exists in `TaskCompletionLogDao`; needs delegation in `TaskRepository` interface and `TaskRepositoryImpl`. Required by `GlobalHistoryViewModel.saveEditTask` to locate the specific log entry to update.

## Data Flow Changes

### Heatmap (Analytics Total)

**Before**: `task_logs[startMs..endMs]` → grouped by `date` → `Map<Long, Int>`

**After**: 
- Source A: `task_logs[startMs..endMs]` grouped by `date` → `Map<Long, Int>`
- Source B: `tasks.completionTimestamp[startMs..endMs]` grouped by `normaliseToMidnight(completionTimestamp)` → `Map<Long, Int>`
- Merged: `(A.keys ∪ B.keys).associate { key → (A[key] ?: 0) + (B[key] ?: 0) }`

The merge must happen inside `getHeatMapData` in `TaskRepositoryImpl`, combining the two reactive Flows using `combine`.

### On-Time Count

**Before**: `COUNT(*) FROM tasks WHERE completionTimestamp IS NOT NULL AND dueDate IS NOT NULL AND completionTimestamp <= dueDate`

**After**: above (non-recurring only, add `AND isRecurring=0`) + `COUNT(*) FROM task_logs WHERE isCompleted=1 AND timestamp <= (date + 86340000)`

### Missed Count

**Before**: `COUNT(*) FROM tasks WHERE dueDate IS NOT NULL AND dueDate < now AND status != 'COMPLETED'`

**After**: `COUNT(*) FROM tasks WHERE dueDate IS NOT NULL AND dueDate < now AND status != 'COMPLETED' AND isRecurring=0` + `COUNT(*) FROM task_logs WHERE isCompleted=0 AND date < todayMidnight`

### History List (Recurring Tasks)

**Before**: `saveEditTask` updates `tasks` row correctly but never updates `task_logs` → log with `isCompleted=true` remains → task stays in history

**After**: `saveEditTask` detects COMPLETED→non-COMPLETED for recurring tasks, finds the specific log by `(taskId, normaliseToMidnight(originalCompletionTimestamp))`, sets `isCompleted=false` → Room Flow in `globalHistoryViewModel` automatically removes the item from the list

### LifetimeStats.totalCompleted

**Before**: `COUNT(*) FROM tasks WHERE status='COMPLETED'` (only current-state COMPLETED tasks, misses historical recurring)

**After**: `COUNT(*) FROM task_logs WHERE isCompleted=1` + `COUNT(*) FROM tasks WHERE isRecurring=0 AND status='COMPLETED'`

### CurrentYearStats.completedThisYear

**Before**: `task_logs[year]` count only (recurring only)

**After**: `task_logs[year]` count + `tasks WHERE isRecurring=0 AND completionTimestamp IN [year]` count
