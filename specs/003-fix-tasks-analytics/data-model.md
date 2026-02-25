# Data Model — 003 Fix Tasks & Analytics

> Phase 1 output for `/speckit.plan`. No new DB tables or schema migrations.

---

## Schema Version

Current: **AppDatabase v7** (unchanged — no migration required for this feature).

All bugs are in the *write path* or *query logic*, not schema structure.

---

## Modified Entities

### `TaskEntity` (existing — `tasks` table)

No column changes. Field behaviour specifications updated:

| Field | Type | Constraint | Change |
|-------|------|------------|--------|
| `dueDate` | `Long?` | nullable | Must be stored as **local midnight** (00:00:00.000 local TZ). Currently stored raw. Fix: normalise in repository on all write paths. |
| `startDate` | `Long` | non-null | Already normalised to midnight in `addTask()`. No change. |

**Invariant**: after this fix, `tasks.dueDate` is always either `NULL` or an exact local
midnight value. All DAO range queries already use `>= todayStart` / `< tomorrowStart` logic
which remains correct.

---

## New Presentation State Fields

### `HomeUiState` (updated — `HomeUiState.kt`)

```kotlin
data class HomeUiState(
    // existing fields …
    val todayTasks: List<Task> = emptyList(),    // was: tasks — dueDate = today
    val upcomingTasks: List<Task> = emptyList(), // NEW — dueDate > today, !COMPLETED
    // … rest unchanged
)
```

`HomeViewModel` splits the single `getHomeScreenTasks` result into the two lists.

### `GlobalHistoryUiState` (updated — `GlobalHistoryUiState.kt`)

```kotlin
data class GlobalHistoryUiState(
    // existing fields …
    val editingTask: TaskEntity? = null,          // NEW — non-null = TaskEditSheet open
    val showActionSheet: Boolean = false,          // NEW — recurring long-press action picker
    val actionSheetTarget: HistoryItem? = null,    // NEW — which item triggered the sheet
    // editingLog: TaskCompletionLog? (existing — unchanged)
)
```

---

## New Utility

### `DateUtils.kt` (new — `app/src/main/java/com/flow/util/DateUtils.kt`)

```kotlin
package com.flow.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Converts a UTC-midnight epoch (as returned by [androidx.compose.material3.DatePickerState.selectedDateMillis])
 * to the equivalent local-timezone midnight for the same calendar date.
 *
 * Bug context (FR-002): In UTC-N timezones, UTC midnight maps to <previous date> local time.
 * Applying `normaliseToMidnight()` to the raw UTC value would yield the wrong local day.
 * This function extracts the UTC (YEAR, MONTH, DAY_OF_MONTH) and rebuilds local midnight.
 */
fun utcDateToLocalMidnight(utcMidnight: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMidnight
    }
    return Calendar.getInstance().apply {   // default = device local timezone
        set(Calendar.YEAR,         utcCal.get(Calendar.YEAR))
        set(Calendar.MONTH,        utcCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY,  0)
        set(Calendar.MINUTE,       0)
        set(Calendar.SECOND,       0)
        set(Calendar.MILLISECOND,  0)
    }.timeInMillis
}
```

---

## State Transitions

### Task lifecycle (dueDate-normalised after this fix)

```
Created          → dueDate stored as local midnight (FR-001 fix)
Picked from UI   → DatePicker UTC midnight → utcDateToLocalMidnight() → local midnight (FR-002 fix)
Queried today    → WHERE dueDate = localMidnight(today)  ✓ now matches
Queried home     → WHERE dueDate >= todayStart … OR >= tomorrowStart (FR-003 fix)
```

### History item long-press state machine (FR-005/FR-006)

```
Long-press detected
├── isRecurring = true
│   └── showActionSheet = true, actionSheetTarget = item
│       ├── "Edit this day" → editingLog = log (existing HistoryEditDialog)
│       └── "Edit task"     → editingTask = TaskEntity (new TaskEditSheet)
└── isRecurring = false
    └── editingTask = TaskEntity (new TaskEditSheet, direct)
```
