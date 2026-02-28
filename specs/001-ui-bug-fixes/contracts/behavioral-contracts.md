# API & Behavioral Contracts: Comprehensive UI Bug Fixes

**Feature**: 001-ui-bug-fixes  
**Date**: 2026-02-25

This document specifies the behavioral contracts for every layer changed by this feature. These are the precise "interface" guarantees that tests must verify.

---

## TaskRepository Contracts

### `addTask(title, startDate, dueDate, isRecurring, scheduleMask)`

**Pre-condition**: Called with any combination of arguments.

**Post-conditions**:

| Scenario | Expected stored `startDate` | Expected stored `dueDate` |
|----------|----------------------------|--------------------------|
| `isRecurring = true`, no dueDate override | `normaliseToMidnight(startDate) + 60_000L` (12:01 AM) | `normaliseToMidnight(startDate) + 86_339_000L` (11:59 PM) |
| `isRecurring = false`, `dueDate = null` | `normaliseToMidnight(startDate)` (unchanged) | `getEndOfDay(normaliseToMidnight(now))` (11:59 PM today) |
| `isRecurring = false`, `dueDate != null` | `normaliseToMidnight(startDate)` (unchanged) | `normaliseToMidnight(dueDate)` (unchanged — user set) |

---

### `refreshRecurringTasks()`

**Contract**: For every recurring task that was completed on a prior calendar day:
- Reset `status = TODO`
- Reset `completionTimestamp = null`
- Reset `startDate = normaliseToMidnight(today) + 60_000L`
- Reset `dueDate = getEndOfDay(normaliseToMidnight(today))`

**Invariant**: After refresh, all reset recurring tasks satisfy `dueDate - startDate == 86_279_000` (11:58 min span, i.e., 12:01→11:59).

---

### `updateTask(task)`

**Contract**:
- If `task.status != COMPLETED`: set `completionTimestamp = null` in the persisted record, regardless of what the existing DB record contains.
- If `task.status == COMPLETED`: use `task.completionTimestamp` if non-null, otherwise preserve existing DB `completionTimestamp`.
- `dueDate` is normalised to midnight as before.

**Violation of old behavior broken intentionally**: Old behavior preserved `completionTimestamp` unconditionally. New behavior clears it on non-COMPLETED status. This is a deliberate, documented behavioral change.

---

### `updateTaskStatus(task, newStatus)`

**Contract — valid transitions expanded**:

| From | To | completionTimestamp |
|------|----|---------------------|
| TODO | IN_PROGRESS | unchanged (null) |
| TODO | COMPLETED | set to `now` if previously null |
| IN_PROGRESS | COMPLETED | set to `now` if previously null |
| COMPLETED | TODO | set to `null` |
| **COMPLETED** | **IN_PROGRESS** | **set to `null`** ← NEW |

All other transitions are rejected (no-op).

---

### `getTodayProgress(): Flow<TodayProgressState>`

**Contract**: Emits tasks satisfying `dueDate >= todayMidnight AND dueDate <= todayMidnight + 86_399_999`. Both recurring and non-recurring tasks with dueDate on today's calendar date are included.

**Old contract (broken)**: Exact match `dueDate == todayMidnight`. No longer valid after time-default fix.

---

## TaskDao Contracts

### `getTasksDueInRange(todayStart, todayEnd): Flow<List<TaskEntity>>`

**New method.** Selects all tasks whose `dueDate` falls within `[todayStart, todayEnd]` inclusive.

```sql
SELECT * FROM tasks WHERE dueDate >= :todayStart AND dueDate <= :todayEnd
```

---

## AnalyticsScreen Contracts

### Page 0 "Total" chip

**Contract**: The value shown in the "Total" chip MUST equal `heatMapData.values.sum()` — the sum of all completion log entries in the currently selected period. It MUST NOT read from a separate `getCompletedTaskCount()` flow.

**Verification**: If 5 tasks are completed this year, both the "Total" chip and the sum of all heatmap cells for the year must equal 5.

---

## ContributionHeatmap Composable Contract

### New signature: `ContributionHeatmap(heatMapData: Map<Long, Int>, startMs: Long, endMs: Long)`

**Contract**:
- The rendered grid starts at the Monday of the week containing `startMs`.
- The rendered grid ends at the Saturday of the week containing `endMs`.
- For `CurrentYear` (Jan 1 to today): grid spans Jan 1 through current week — no future months rendered.
- For `SpecificYear`: grid spans the full calendar year Jan 1 to Dec 31.
- Cell count is computed from the date range, not a hardcoded 52.
- `TaskHistoryScreen` passes `startMs = jan1CurrentYear, endMs = todayEndOfDay`.

---

## TimerViewModel Contracts

### Settings reactivity

**Contract**: When `settingsRepository.defaultTimerMinutes` emits a new value AND the timer is neither running nor paused, the `uiState.durationSeconds` and `uiState.remainingSeconds` MUST update to `newValue * 60` within the same coroutine collection cycle (effectively immediate on navigation).

---

## Timer Completion Sound Contract

**Contract**:
- When `uiState.isFinished` transitions from `false` to `true`, a short audio chime plays once.
- The audio uses `AudioAttributes.USAGE_NOTIFICATION`.
- No sound plays if the device is in silent mode or DND (Android OS handles suppression automatically via the notification stream).
- The sound file is bundled at `res/raw/timer_complete.ogg` (no network access).

---

## Theme Contract

**Contract**: `FlowTheme` MUST use `DarkBackground = Color(0xFF121212)` as the Material `background` color on all Android API levels, including API 31+ (Android 12). Dynamic color theming MUST be disabled by default (`dynamicColor = false`).

---

## Constitution Amendment Contract

**Contract (FR-017 / NC-001)**: The following principle must be added to `.specify/memory/constitution.md` under Core Principles:

> **VII. Emoji Correctness (Non-Negotiable)**  
> All Unicode emoji characters in the codebase MUST render as their intended visual symbols on all supported Android API levels.  
> Any code change that modifies, serializes, processes, or stores emoji string content MUST include an automated test that asserts the emoji renders as the correct character, not as a replacement character, box, or empty string.  
> This rule was added after an encoding regression in feature 001-ui-bug-fixes stripped emoji return values from `achievementEmoji()` in AnalyticsScreen.  
> Version bump: 1.3.2 → 1.4.0 (MINOR: new principle added).
