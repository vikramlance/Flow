# Contracts: Fix Task End Time Bug — Iteration 2

No external API contracts. All changes are within the single Android app module — no network calls, no new endpoints, no new IPC boundaries.

## Internal Save Path Contracts

These define the expected behaviour at each layer boundary after the fix.

### Contract 1 — `TaskRepositoryImpl.updateTask(task: TaskEntity)`

**Pre-condition**: Caller provides a `TaskEntity` with `dueDate` set to the epoch millis the user selected (including H:M).

**Post-condition**: `taskDao.updateTask()` receives a `TaskEntity` where `dueDate` equals the caller's value — no time truncation applied.

**Violation pattern (removed in T004)**:
```kotlin
// BEFORE: repository silently strips H:M
dueDate = task.dueDate?.let { normaliseToMidnight(it) }
```

**Fixed pattern (T004)**:
```kotlin
// AFTER: repository stores what it receives
taskDao.updateTask(
    task.copy(completionTimestamp = resolvedTimestamp)
    // dueDate flows through unchanged
)
```

---

### Contract 2 — `GlobalHistoryViewModel.saveEditTask(updated: TaskEntity)`

**Pre-condition**: `updated.dueDate` is the epoch millis the user selected in the History edit sheet (including H:M).

**Post-condition**: `repository.updateTask()` receives an entity where `dueDate` equals the caller's `updated.dueDate` — no time truncation.

**Violation pattern (removed in T005)**:
```kotlin
// BEFORE: ViewModel strips H:M before repository
dueDate = updated.dueDate?.let { normaliseToMidnight(it) }
```

**Fixed pattern (T005)**:
```kotlin
// AFTER: pass dueDate directly
repository.updateTask(
    updated.copy(
        completionTimestamp = if (updated.status == TaskStatus.COMPLETED) updated.completionTimestamp else null
        // dueDate flows through unchanged
    )
)
```

---

### Contract 3 — `TaskEditSheet` time picker chain

**Trigger**: User confirms a date in the start-date or due-date `DatePickerDialog` inside `TaskEditSheet`.

**Chain**:
1. Date picker confirms → `showStartTimePicker = true` (or `showDueTimePicker = true`)
2. Time picker confirms → `startDateMs = mergeDateTime(dateMs, [H:M from picker])` (or `dueDateMs = ...`)
3. User presses Save → `onSave(task.copy(startDate = startDateMs, dueDate = dueDateMs, ...))`
4. `GlobalHistoryViewModel.saveEditTask` receives H:M-accurate epoch millis
5. After T005 fix: repository stores value unchanged

**Invariant**: If user dismisses the time picker after confirming the date picker, the previously selected time is preserved (not reset to midnight).
