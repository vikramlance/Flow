# Data Model: App Icon, Task History & Help Access

**Phase**: 1  Design  
**Date**: 2026-02-21

---

## Summary

No new database tables or columns are required. DB schema version remains at **5**. All changes are additive new queries on existing tables.

---

## Existing Entities (unchanged)

### `TaskEntity` (table: `tasks`)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) | Stable row identity |
| `title` | String | Display name |
| `description` | String | Optional detail |
| `status` | TaskStatus | TODO / IN_PROGRESS / COMPLETED |
| `dueDate` | Long? | Target date (epoch ms). `null` = undated |
| `startDate` | Long | Always set; normalised to midnight |
| `isRecurring` | Boolean | Drives streak tracking + daily reset |
| `createdAt` | Long | Insertion time  used for stable home-screen ordering |
| `completionTimestamp` | Long? | Set on COMPLETED, cleared on COMPLETEDTODO revert |

**History role**: For *non-recurring* completed tasks, `completionTimestamp` is the source-of-truth for the history screen.

---

### `TaskCompletionLog` (table: `task_logs`)

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, auto) |  |
| `taskId` | Long (FKtasks.id) | Owning task |
| `date` | Long | Midnight epoch of the completion day |
| `isCompleted` | Boolean | `true` = completed that day; `false` = reverted |
| `timestamp` | Long | Exact time of write (used for chronological sort in history) |

**Unique index**: `idx_task_logs_task_date` on `(taskId, date)` (added in migration 45). `OnConflictStrategy.REPLACE` means inserting an entry for the same task+day overwrites the previous one  this is how completion reversal is handled (revert writes `isCompleted=false`).

**History role**: For *recurring* tasks, `isCompleted=true` log entries are the source-of-truth for the history screen.

---

### `DailyProgressEntity` (table: `daily_progress`)  unchanged, not involved in this feature

---

## New Read Queries (no schema change)

### `TaskDao`  new query: `getHomeScreenTasks`

Replaces the generic `getAllTasks()` as the data source for the home screen task grid.

```
Purpose: Return tasks that should be visible on today's home screen.

Input: todayStart (midnight epoch ms), tomorrowStart (next midnight epoch ms)

Output: List<TaskEntity> ordered by createdAt ASC (stable insertion order)

Visibility rules:
  Rule 1   isRecurring = 1             (all recurring tasks, always visible)
  Rule 2   dueDate between todayStart and tomorrowStart (due today, any status)
  Rule 3   dueDate < todayStart AND status  COMPLETED  (overdue, not done)
  Rule 4   dueDate IS NULL AND isRecurring = 0 AND
              (status  COMPLETED OR completionTimestamp  todayStart)
            (undated general tasks: visible until completed; if completed today, still visible today)
```

### `TaskDao`  new query: `getCompletedNonRecurringTasks`

```
Purpose: Return all non-recurring tasks that have been completed (completionTimestamp IS NOT NULL).
         Used as source B for the global history screen.

Output: List<TaskEntity> ordered by completionTimestamp DESC
```

### `TaskCompletionLogDao`  new query: `getAllCompletedLogs`

```
Purpose: Return all TaskCompletionLog entries where isCompleted = true.
         Used as source A for the global history screen.

Output: List<TaskCompletionLog> ordered by date DESC, timestamp DESC
```

---

## New Presentation Model

### `HistoryItem` (presentation layer  `presentation/history/`)

A display model constructed in `GlobalHistoryViewModel` by combining Sources A and B. Not persisted; derived from DB data on each update.

| Field | Type | Notes |
|---|---|---|
| `taskId` | Long | For navigation if needed |
| `taskTitle` | String | Task display name (from TaskEntity) |
| `targetDate` | Long? | Task's `dueDate`  may be null |
| `completedDayMidnight` | Long | Midnight epoch of the day completed (for grouping) |
| `completedAtMs` | Long | Exact completion timestamp (for ordering within a day) |
| `isRecurring` | Boolean | Display label / filter hint |

**Construction rules**:
- Recurring items: built from `TaskCompletionLog` (date, timestamp) + `TaskEntity` (title, dueDate) via in-memory join on `taskId`
- Non-recurring items: built directly from `TaskEntity` (completionTimestamp  completedDayMidnight, completionTimestamp  completedAtMs)
- No double counting: `TaskCompletionLog` entries only exist for recurring tasks; `getCompletedNonRecurringTasks()` only returns `isRecurring=0` tasks

---

## New UI State Models

### `GlobalHistoryUiState`

| Field | Type | Default | Notes |
|---|---|---|---|
| `allItems` | List<HistoryItem> | `[]` | Full unfiltered history |
| `selectedDateMidnight` | Long? | `null` | `null` = show all dates |
| `datesWithData` | Set<Long> | `{}` | Midnight timestamps with 1 history item (drives date-strip highlight) |
| `viewMode` | HistoryViewMode | DATE_GROUPED | Toggle between grouped and chronological |
| `filterMode` | HistoryFilterMode | COMPLETED_ON | Group by completion date or target date |
| `isLoading` | Boolean | `true` |  |

**Derived display list** (computed in ViewModel, not stored):
- Apply `selectedDateMidnight` filter if non-null
- Apply `filterMode` (use `completedDayMidnight` or `targetDate` as the grouping key)
- Apply `viewMode` (group under date headers or flat sorted list)

### `HistoryViewMode` (enum)

| Value | Meaning |
|---|---|
| `DATE_GROUPED` | Tasks rendered under collapsible date section headers (most recent date first) |
| `CHRONOLOGICAL` | Flat list with inline date/time per row, sorted by `completedAtMs DESC` |

### `HistoryFilterMode` (enum)

| Value | Grouping Key |
|---|---|
| `COMPLETED_ON` | Group/filter by `completedDayMidnight` |
| `TARGET_DATE` | Group/filter by `targetDate` (day of `dueDate`); tasks with null `dueDate` appear under "No Target Date" |

---

## HomeUiState Changes

| Change | Before | After |
|---|---|---|
| Task list field | `tasks: List<TaskEntity>` | `homeTasks: List<TaskEntity>` (home-screen-specific query) |
| Help overlay |  | `showHelp: Boolean = false` |

---

## State Transitions  Key Invariants Preserved

| Transition | TaskEntity | TaskCompletionLog |
|---|---|---|
| TODO  COMPLETED | `completionTimestamp` set, `status = COMPLETED` | Log written (`isCompleted=true`) if recurring |
| COMPLETED  TODO (undo, same day) | `completionTimestamp` cleared, `status = TODO` | Log overwritten (`isCompleted=false`) if recurring |
| Recurring reset (next day app open) | `completionTimestamp` cleared, `status = TODO` | Log from previous day unchanged (history preserved) |
| Task deleted | Row deleted | All logs for this task deleted (cascade in transaction) |
