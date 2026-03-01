# Data Model: Fix Task End Time Bug — Iteration 2

**No database schema changes required.** The fix is entirely in the save path (ViewModel + Repository layer).

## Affected Entity

### `TaskEntity` (Room, table `tasks`)

| Field | Type | Change |
|---|---|---|
| `dueDate: Long?` | epoch millis (nullable) | **Behavior fix only.** Field type unchanged. Was silently truncated to midnight on every update write; after fix the exact epoch millis provided by the caller is stored. |
| `startDate: Long` | epoch millis (non-null) | **Display only.** Field unchanged. Home screen task card will now render this value as "Start: MMM dd, HH:mm". |
| All other fields | — | Unchanged |

### Storage Contract (after fix)

| Action | Old behaviour (Iteration 1 — BUG) | New behaviour (Iteration 2 — FIXED) |
|---|---|---|
| Edit task from Home screen → Save | `dueDate` stored as `midnight(selectedDay)` — time lost | `dueDate` stored as exact epoch millis from UI state |
| Edit task from History screen → Save | `dueDate` normalised twice (VM + repository) → midnight | `dueDate` stored as exact epoch millis from UI state |
| Create new task (`AddTaskDialog`) | `dueDate` stored at 23:59 on selected day | Unchanged — `addTask()` not modified |

### Root Cause: Save Path Transformation (REMOVED)

The following calls are the bug and are removed in T004 and T005:

```kotlin
// TaskRepositoryImpl.updateTask — BEFORE (BUG)
taskDao.updateTask(task.copy(
    completionTimestamp = resolvedTimestamp,
    dueDate = task.dueDate?.let { normaliseToMidnight(it) }  // ← strips H:M to 00:00
))

// GlobalHistoryViewModel.saveEditTask — BEFORE (BUG)
repository.updateTask(updated.copy(
    completionTimestamp = ...,
    dueDate = updated.dueDate?.let { normaliseToMidnight(it) }  // ← strips H:M to 00:00
))
```

### Invariants Maintained

- `dueDate = null` means "no target date set" — unchanged.
- When `dueDate` is non-null it represents an exact local-timezone epoch:
  - **New tasks**: always stored at 23:59:00 on selected day — unchanged.
  - **Edited tasks**: stored at user-selected H:M:00.000 on selected day — fixed.
- `startDate: Long` defaults to `System.currentTimeMillis()` on creation — unchanged.
- `normaliseToMidnight` is retained in all comparison/grouping call sites — unchanged.

## No Migration

Existing rows with `dueDate` at midnight (00:00) are **not modified**.
Per DI-002, retroactive migration is out of scope.
