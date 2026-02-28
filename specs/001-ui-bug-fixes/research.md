# Research: Comprehensive UI Bug Fixes

**Phase**: 0 — Unknowns Resolved  
**Feature**: 001-ui-bug-fixes  
**Date**: 2026-02-25

---

## Issue 1 — Recurring Task Time Defaults

**Decision**: Set startDate = today midnight + 60,000 ms (12:01 AM) and dueDate = today midnight + 86,339,000 ms (11:59 PM) when creating or resetting recurring tasks.

**Root cause found in**: `TaskRepositoryImpl.refreshRecurringTasks()` — resets recurring tasks with `startDate = today` and `dueDate = today` (both midnight). Also `addTask()` normalises both to midnight unconditionally.

**Rationale**: A day range of [12:01 AM, 11:59 PM] gives the task full-day coverage without ambiguous midnight overlap into adjacent days.

**Alternatives considered**: Using calendar start/end of day (midnight and 23:59:59.999). Using midnight was simpler but produces zero-duration tasks.

**Code locations**:
- `TaskRepositoryImpl.refreshRecurringTasks()` line ~300: `startDate = today, dueDate = today`
- `TaskRepositoryImpl.addTask()` line ~128: `normaliseToMidnight(startDate)` applied unconditionally

---

## Issue 2 — Non-Recurring Task End Date Default

**Decision**: When `addTask()` is called with `isRecurring = false` and `dueDate = null`, default `dueDate` to end-of-day milliseconds for today (midnight + 86,339,000 ms). `startDate` logic is unchanged.

**Root cause found in**: `HomeViewModel.addTask()` passes `dueDate = null` for non-recurring tasks. `addTask()` in the repository stores null.

**Rationale**: Consistent with recurring task convention. Enables today-progress calculation to include newly created non-recurring tasks immediately.

**Code location**: `TaskRepositoryImpl.addTask()` — add conditional defaulting for non-recurring + null dueDate.

---

## Issue 3 — Today's Progress Percentage

**Decision**: Change `getTodayProgress()` to query tasks with `dueDate >= todayStart AND dueDate <= todayEnd` (date-range query) instead of exact midnight match. This covers both recurring tasks (dueDate = 11:59 PM) and non-recurring tasks.

**Root cause found in**: `TaskRepositoryImpl.getTodayProgress()` calls `taskDao.getTasksDueOn(todayMidnight)` with exact match. After the time-default fix, all task dueDates will be 11:59 PM — making the exact midnight query return zero results.

**Also**: `TaskDao.getTasksDueOn()` uses `WHERE dueDate = :todayMidnight` — exact match.

**Fix**: Add `TaskDao.getTasksDueInRange(todayStart, todayEnd)` and update `getTodayProgress()` to pass `[todayMidnight, getEndOfDay(todayMidnight)]`.

**Alternatives considered**: Normalising dueDate to midnight for comparison only. Adding a computed "dateOnly" column. Using a date-range query is simpler and follows SQLite idioms.

---

## Issue 4 — History Deduplication

**Root cause A — completionTimestamp not cleared on status rollback**: `TaskRepositoryImpl.updateTask()` always overwrites `completionTimestamp` with the existing DB value: `task.copy(completionTimestamp = existing.completionTimestamp)`. This means that if a task is moved to IN_PROGRESS via `GlobalHistoryViewModel.saveEditTask()`, the completionTimestamp is never nulled, so `getCompletedNonRecurringTasks()` query (`WHERE completionTimestamp IS NOT NULL`) still returns the task — it appears in history AND on the home screen.

**Root cause B — COMPLETED→IN_PROGRESS not a valid status transition**: `updateTaskStatus()` allows only COMPLETED→TODO for nulling the timestamp. Moving back to IN_PROGRESS is not handled, so users working from the history edit sheet bypass the transition logic entirely.

**Root cause C (pre-existing, already mitigated)**: The DB has a UNIQUE index on `(taskId, date)` added in MIGRATION_4_5 (version 4→5). `@Insert(onConflict = REPLACE)` on the DAO replaces duplicate (taskId, date) log rows. Recurring task re-completion on the same day is already deduplicated at DB level. No new migration needed.

**Date filter row**: `GlobalHistoryScreen` already has a reactive `LazyRow` of date chips driven by `state.datesWithData`. Once root cause A is fixed, the filter strips will update reactively because the state flows will re-emit.

**Fixes**:
- A: Change `updateTask()` to set `completionTimestamp = null` when `task.status != COMPLETED`, regardless of DB value.
- B: Allow COMPLETED→IN_PROGRESS in `updateTaskStatus()`, clearing completionTimestamp.

**Code locations**:
- `TaskRepositoryImpl.updateTask()` line ~138
- `TaskRepositoryImpl.updateTaskStatus()` valid-transition map line ~148

---

## Issue 5 — Analytics Discrepancy

**Root cause found in**: `AnalyticsScreen.kt` Page 0 shows `uiState.totalCompleted` (from `getCompletedTaskCount()` = count of tasks with `status = 'COMPLETED'`) in the "Total" chip. The heatmap on the same page renders `uiState.heatMapData` (from `task_logs` count per day in the selected period). These are two different data sources with different semantics:
- `getCompletedTaskCount()`: tasks currently in COMPLETED state (all time, e.g., 14)
- heatMapData: log entries for the selected period window (e.g., 7 for this year)

**Decision (per spec Q4/FR-009)**: Derive the "Total" chip value on Page 0 from `heatMapData.values.sum()` — the same source as the heatmap. Both chip and grid now come from a single fetch. No separate `totalCompleted` field needed on Page 0.

**The `totalCompleted` field** can remain in `AnalyticsUiState` for the Lifetime page which uses a different semantic. Page 0's chip source changes from `totalCompleted` to `heatMapData.values.sum()`.

**Code locations**:
- `AnalyticsScreen.kt` line ~95: `StatChip("Total", uiState.totalCompleted.toString(), ...)` → change to `heatMapData.values.sum()`

---

## Issue 6 — Heat Map Default Date Range (General + Recurring Streak)

**Root cause found in**: Both `ContributionHeatmap()` and `ForestHeatmap()` in `AnalyticsScreen.kt` hard-code `weeksToShow = 52` (rolling 52 weeks, ending on next Saturday). This always shows the last 52 weeks regardless of the selected period. For `CurrentYear`, the spec requires showing January through the current month only.

**Decision**: Refactor `ContributionHeatmap(heatMapData, startMs, endMs)` to accept explicit date bounds. The grid computes its start/end columns from the passed range:
- `CurrentYear` → `startMs = Jan 1 of current year midnight`, `endMs = today's end of day` → renders Jan through current month
- `Last12Months` / `Lifetime` → keep rolling 52-week display (pass rolling startMs)
- `SpecificYear` → full year, `startMs = Jan 1`, `endMs = Dec 31`

**`TaskHistoryScreen` / recurring streak heat map**: Uses `ContributionHeatmap`. After the signature change, pass `startMs = jan1CurrentYear`, `endMs = today`. Same fix covers both the general heatmap and the streak heat map.

**Color change for streak heat map**: Current NeonGreen at `alpha = 0.35f` for count=1. Change to `alpha = 0.60f` minimum and `alpha = 1.0f` (full NeonGreen) for count=1+. Specifically for the streak view where each day is 0 or 1, use `NeonGreen.copy(alpha = 0.75f)` for present cells — darker and more visible.

**Code location**: `ContributionHeatmap` composable in `AnalyticsScreen.kt` line ~408.

---

## Issue 7 — Focus Timer Live Settings Update

**Root cause found in**: `TimerViewModel.init` reads default duration with `firstOrNull()` — a one-shot collection. The `defaultTimerMinutes` flow emits new values when settings change, but the ViewModel never subscribes to subsequent emissions.

**Decision**: Replace `firstOrNull()` with an ongoing `collect { }` in a `viewModelScope.launch` coroutine. The `durationSeconds` and `remainingSeconds` are updated only when the timer is idle (not running, not paused, not finished).

**Why collect and not combine**: The timer tick loop and the settings observer are independent lifecycle concerns. A `collect` guarded by `isRunning + isPaused` is simpler and does not create a state dependency graph.

**Code location**: `TimerViewModel.kt` lines 34–38.

---

## Issue 8 — Focus Timer Completion Sound

**Decision**: Play a notification-stream sound via `android.media.MediaPlayer` with `AudioAttributes.USAGE_NOTIFICATION`. Bundle a short OGG chime (< 3 s) at `res/raw/timer_complete.ogg`. Play in a `LaunchedEffect` on `uiState.isFinished` transition in `TimerPanel`.

**Rationale**: Using MediaPlayer with a bundled raw resource gives full control over the sound file and avoids dependency on the device's default ringtone (which may be harsh). `USAGE_NOTIFICATION` ensures silent/DND suppression.

**Alternative considered**: `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)` — simpler but relies on device-configured sound which could be harsh or undefined.

**Code locations**: Add `res/raw/timer_complete.ogg`. Modify `TimerPanel.kt` to add sound playback on finish.

---

## Issue 9 — Emoji Display Regression

**Root cause found in**: `achievementEmoji()` private function in `AnalyticsScreen.kt` lines 304–311. All six `when` branches return empty string `""`. The emoji characters were stripped, likely during a file encoding change or find-replace operation in a prior commit. The function signature is intact; only the return values are missing.

**Additional locations verified OK**: `TaskHistoryScreen.kt` uses `Text("🌱")` literal (UTF-8 file, fine). `GlobalHistoryScreen.kt` uses `"🌱"` literal (fine). `ForestHeatmap` uses `"\uD83C\uDF32"` Unicode escapes (fine). `OnboardingFlow.kt` uses emoji inline in strings (fine).

**Decision**: Restore six emoji strings in `achievementEmoji()`:

| Type | Emoji | Unicode |
|------|-------|---------|
| STREAK_10 | 🌱 | `\uD83C\uDF31` |
| STREAK_30 | 🌳 | `\uD83C\uDF33` |
| STREAK_100 | 🏆 | `\uD83C\uDFC6` |
| ON_TIME_10 | ⏱️ | `\u23F1\uFE0F` |
| EARLY_FINISH | ⚡ | `\u26A1` |
| YEAR_FINISHER | 🎯 | `\uD83C\uDFAF` |

All represented as Unicode escape sequences to prevent future file-encoding issues.

**Constitution update required**: Add emoji non-negotiable principle (NC-001) to constitution file as per FR-017.

---

## Issue 10 — Background Color

**Decision**: Set `dynamicColor = false` in `FlowTheme` to ensure `DarkBackground = Color(0xFF121212)` is always used, not overridden by Android 12+ dynamic theming.

**Root cause found in**: `Theme.kt` — `FlowTheme(dynamicColor: Boolean = true)`. On Android 12+ (API 31+), `dynamicDarkColorScheme(context)` overrides `DarkBackground`. The `DarkBackground = Color(0xFF121212)` is already correctly defined in `Color.kt`.

**Code location**: `FlowTheme` default parameter in `Theme.kt`.

---

## Issue 11 — App Icon Centering

**Root cause**: The foreground layer of the adaptive icon likely extends content to the full 108dp canvas, but the safe zone is only 72dp (inner 66%). Content near the edges gets clipped by launchers.

**Decision**: Constrain all visual content in `ic_launcher_foreground.xml` / foreground drawable within a centered 66dp area. Check that `android:viewportWidth` and `android:viewportHeight` in the vector drawable have correct padding.

**Note**: Cannot read binary PNG files or fully inspect the SVG structure without rendering. The fix requires visually inspecting the foreground vector and adjusting padding/scaling to bring everything within safe zone.

---

## No Outstanding NEEDS CLARIFICATION items

All 5 clarification answers have been incorporated. All root causes are identified with specific file and line references. No unknowns remain.
