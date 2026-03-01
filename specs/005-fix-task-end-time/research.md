# Research: Fix Task End Time Bug (Revised — Iteration 2)

**Branch**: `005-fix-task-end-time`
**Date**: February 28, 2026

## Objective

Identify why the first implementation cycle did not fix the bug — tasks saved from both screens still show 12:00 AM — and determine the correct fix locations.

---

## Why Iteration 1 Failed

### What Was Fixed in Iteration 1

The first round fixed the date-picker confirm lambdas in the UI layer:

- `HomeScreen.kt` `EditTaskDialog` target-date confirm: `endTimeForDate(...)` → `mergeDateTime(..., dueDate ?: defaultEndTime())`
- `HomeScreen.kt` `EditTaskDialog` start-date confirm: `utcDateToLocalMidnight(it)` → `mergeDateTime(..., startDate)`
- `GlobalHistoryScreen.kt` `TaskEditSheet` due-date confirm: `utcDateToLocalMidnight(it)` → `resolveEditTaskSheetDueDate(...)`
- `GlobalHistoryScreen.kt` `TaskEditSheet` start-date confirm: same mergeDateTime fix

### Why Those Fixes Were Irrelevant

After the UI computes the correct epoch millis with H:M preserved, the value flows through:

1. **Home screen save path**: `onSave(task.copy(dueDate = dueDate))` → `HomeViewModel.updateTask(task)` → `repository.updateTask(task)`
2. **History screen save path**: `onSave(task.copy(..., dueDate = dueDateMs, ...))` → `GlobalHistoryViewModel.saveEditTask(updated)` → `repository.updateTask(task.copy(dueDate = updated.dueDate?.let { normaliseToMidnight(it) }))`

At step (1) and (2), the Repository does:

```kotlin
// TaskRepositoryImpl.updateTask — the real bug
taskDao.updateTask(
    task.copy(
        completionTimestamp = resolvedTimestamp,
        dueDate = task.dueDate?.let { normaliseToMidnight(it) }   // ← ALWAYS midnight
    )
)
```

**This line executes on every task update**, stripping the time component before writing to Room. No UI-layer fix can survive this.

For the history screen, the ViewModel also applies `normaliseToMidnight` before the repository gets it:

```kotlin
// GlobalHistoryViewModel.saveEditTask — second normalisation
repository.updateTask(
    updated.copy(
        dueDate = updated.dueDate?.let { normaliseToMidnight(it) }  // ← midnight again
    )
)
```

### Why Tests Passed

The unit tests created in iteration 1 tested utility functions directly:

```kotlin
// TaskEditSheetEndTimeTest.kt — calls utility function, never touches repository
val result = resolveEditTaskSheetDueDate(pickerUtcMs, existingDueMs)
assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))  // passes ✓
```

```kotlin
// GlobalHistoryScreenTest.kt — same: calls utility directly
val savedDueMs = resolveEditTaskSheetDueDate(pickerUtcMs, existingDueMs)
assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))  // passes ✓
```

Neither test exercised `repository.updateTask()`. The repository's midnight normalisation was never called during testing. All 78 instrumented tests passed, but none of them covered the actual save path.

---

## True Root Causes

### Bug 1 — Repository strips time on every task update (PRIMARY)

**File**: `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt`
**Line**: ~151
**Code**: `dueDate = task.dueDate?.let { normaliseToMidnight(it) }`
**Effect**: Every call to `repository.updateTask()` produces midnight for the stored `dueDate`, no matter what the caller sends.
**Fix**: Remove this normalisation. The repository is a thin persistence layer; it must store what it receives.

### Bug 2 — History ViewModel also normalises (SECONDARY)

**File**: `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt`
**Line**: ~126
**Code**: `dueDate = updated.dueDate?.let { normaliseToMidnight(it) }`
**Effect**: History-screen saves apply midnight normalisation twice (VM + repository).
**Fix**: Remove `normaliseToMidnight` from the `dueDate` field in `saveEditTask`. Pass the value from the UI directly.

### Missing Feature — History screen has no time pickers

**File**: `app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt`
**Composable**: `TaskEditSheet`
**Issue**: Only date pickers exist. Even after fixing the save path, users cannot set or see the time when editing from history.
**Fix**: Add start-time and target-time pickers to `TaskEditSheet`, identical to those already in `EditTaskDialog` on the Home screen.

### Missing UI — Task card does not show start date/time

**File**: `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`
**Composable**: `TaskItem` (lines ~490–530)
**Issue**: Task card displays only `"Target: MMM dd, HH:mm"`. Start date/time is not visible.
**Fix**: Add a `"Start: MMM dd, HH:mm"` line below the title in the task card column.

---

## `normaliseToMidnight` Usage Audit

`normaliseToMidnight` appears in these locations and is **correct** in all except the save paths above:

| File | Line | Usage | Verdict |
|---|---|---|---|
| `TaskRepositoryImpl.kt` | ~55 | Computing today start for overdue checking | **Correct** (comparison, not storage) |
| `TaskRepositoryImpl.kt` | ~90 | todayStart for status queries | **Correct** (comparison) |
| `TaskRepositoryImpl.kt` | ~151 | `dueDate = task.dueDate?.let { normaliseToMidnight(it) }` during updateTask | **BUG — remove** |
| `GlobalHistoryViewModel.kt` | ~126 | `dueDate` in saveEditTask | **BUG — remove** |
| `GlobalHistoryViewModel.kt` | ~157 | Computing end of today for edit validation | **Correct** (comparison) |
| `GlobalHistoryViewModel.kt` | ~185 | Grouping items by target date for filter | **Correct** (grouping key) |
| `GlobalHistoryViewModel.kt` | ~224 | `completedDayMidnight` for history grouping | **Correct** (grouping key) |

---

## Fix Strategy (Revised)

### Fix 1 — Remove midnight normalisation from `TaskRepositoryImpl.updateTask`

```kotlin
// Before (BUG)
taskDao.updateTask(
    task.copy(
        completionTimestamp = resolvedTimestamp,
        dueDate = task.dueDate?.let { normaliseToMidnight(it) }
    )
)

// After (FIXED)
taskDao.updateTask(
    task.copy(
        completionTimestamp = resolvedTimestamp
        // dueDate flows through unchanged — caller is responsible for correct H:M
    )
)
```

### Fix 2 — Remove midnight normalisation from `GlobalHistoryViewModel.saveEditTask`

```kotlin
// Before (BUG)
repository.updateTask(
    updated.copy(
        completionTimestamp = ...,
        dueDate = updated.dueDate?.let { normaliseToMidnight(it) }
    )
)

// After (FIXED)
repository.updateTask(
    updated.copy(
        completionTimestamp = ...
        // dueDate from UI state flows through unchanged
    )
)
```

### Fix 3 — Add time pickers to `TaskEditSheet` in `GlobalHistoryScreen.kt`

Mirror the existing `EditTaskDialog` pattern:
- Start-date confirm already uses `mergeDateTime` (iteration 1 fix, retained) → **add start-time picker** that opens after start-date confirm
- Due-date confirm already uses `resolveEditTaskSheetDueDate` (iteration 1 fix, retained) → **add due-time picker** that opens after due-date confirm
- Update button display labels to show H:M (format `"MMM d, HH:mm"` instead of `"MMM d, yyyy"`)

### Fix 4 — Show start date/time on task card in `HomeScreen.kt`

In `TaskItem`, below the title column, add:
```kotlin
Text(
    text = "Start: ${sdf.format(Date(task.startDate))}",
    style = MaterialTheme.typography.labelSmall,
    color = Color.Gray
)
```

---

## Non-Regression Scope

| Item | Must remain unchanged |
|---|---|
| `AddTaskDialog` | New task creation defaults to 23:59 |
| `normaliseToMidnight` calls for grouping/filtering | All usages except save paths |
| `repository.addTask()` | `dueDate` passes through directly — already correct |
| `updateTaskStatus()` | Does not touch `dueDate` — no change needed |

---

## Process Improvement Notes

The test gap that allowed this to slip through was **scope isolation**: tests only verified that helper functions produce correct output, but did not verify that the repository stored that output correctly. Future test design for any date/time storage feature MUST include at least one test that:

1. Seeds a task with a known time
2. Calls the ViewModel save function (not the utility function directly)
3. Reads back from the Repository/DAO
4. Asserts the stored H:M matches what was passed in
