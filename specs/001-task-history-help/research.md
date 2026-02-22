# Research: App Icon, Task History & Help Access

**Phase**: 0  Research  
**Date**: 2026-02-21  
**Branch**: `001-task-history-help`

---

## Decision 1: Root Cause of Disappearing Completed Tasks

**Investigation**: `HomeScreen.kt` line ~52:
```kotlin
val activeTasks = uiState.tasks.filter { it.status != TaskStatus.COMPLETED }
```
This one-line filter is why completed tasks vanish immediately. The fix is to remove this filter and replace the data source with a new home-screen-specific query.

**Secondary root cause  task reordering**: `TaskDao.getAllTasks()` uses `ORDER BY status ASC, createdAt DESC`. Sorting by status forces alphabetical reordering (COMPLETED < IN_PROGRESS < TODO), so every status change moves cards in the grid.

**Decision**: Add new DAO query `getHomeScreenTasks(todayStart, tomorrowStart)` with `ORDER BY createdAt ASC`. Remove the `activeTasks` filter in `HomeScreen.kt`. Use stable order (insertion order) so cards never move.

**Alternatives considered**:
- Sort in ViewModel with `sortedBy { it.createdAt }`: works but redundant if DAO already returns correct order.
- Add `displayOrder` column: over-engineered; `createdAt` already provides stable insertion order.

---

## Decision 2: Home Screen Task Visibility Rules

**Investigation**: Three task categories have distinct visibility rules per spec.

**Decision**: New `getHomeScreenTasks(todayStart, tomorrowStart)` SQL covers all four cases:
1. `isRecurring = 1`  recurring tasks: always on home screen (reset daily but always visible)
2. `dueDate >= todayStart AND dueDate < tomorrowStart`  tasks due today (any status)
3. `dueDate < todayStart AND status != 'COMPLETED'`  overdue, not yet done (orange styling)
4. `dueDate IS NULL AND isRecurring = 0 AND (status != 'COMPLETED' OR completionTimestamp >= todayStart)`  general tasks: show until completed; if completed today, still show today

**Rationale**: Rule 3 (overdue) preserves the existing overdue orange styling (US1 scenario 5). Rule 4 ensures general tasks remain visible on the day of completion (per A-001) but disappear when the app is opened the next day (completionTimestamp < todayStart).

---

## Decision 3: Recurring Task Daily Reset Mechanism

**Investigation**: `TaskRepositoryImpl.refreshRecurringTasks()` already implements the lazy reset:
```kotlin
allTasks.filter { it.isRecurring && it.status == TaskStatus.COMPLETED }.forEach { task ->
    val completedDay = task.completionTimestamp?.let { normaliseToMidnight(it) } ?: 0L
    if (completedDay < today) {
        taskDao.updateTask(task.copy(status = TaskStatus.TODO, completionTimestamp = null))
    }
}
```
It is called in `HomeViewModel.init`. The `TaskCompletionLog` entry is written at completion time in `updateTaskStatus()`, so history is already preserved before the reset.

**Decision**: No changes needed to `refreshRecurringTasks()`. History for recurring tasks is already correctly preserved via `TaskCompletionLog`.

**Verification**: `updateTaskStatus()` writes `TaskCompletionLog(taskId, date=today, isCompleted=true)` when `justCompleted=true`. The reset only clears `TaskEntity.completionTimestamp` and reverts `status`. Log entry is unaffected.

---

## Decision 4: Global History Data Sources (No Double Counting)

**Investigation**: `TaskCompletionLog` is only written for `isRecurring = true` tasks (guard in `updateTaskStatus()`). Non-recurring tasks use `TaskEntity.completionTimestamp` only. So the two sources are mutually exclusive.

**Decision**:
- **Source A (recurring)**: `TaskCompletionLogDao.getAllCompletedLogs()`  returns all logs where `isCompleted = true`, ordered by `date DESC`.
- **Source B (non-recurring)**: `TaskDao.getCompletedNonRecurringTasks()`  returns all TaskEntity where `completionTimestamp IS NOT NULL AND isRecurring = 0`, ordered by `completionTimestamp DESC`.

**Combined in**: `GlobalHistoryViewModel`  joins both sources against `getAllTasks()` for task titles, maps to `HistoryItem` display models. Repository returns raw data (DAO results). ViewModel constructs the display model. This preserves CO-001 (no presentation types in data layer).

**Alternatives considered**:
- Room JOIN query: would require a view or join that spans `tasks` and `task_logs`  more SQL complexity, harder to maintain.
- New `CompletedTaskHistory` table: extra write path, redundant data, migration needed. Rejected.

---

## Decision 5: History Entry Reversal When Cycling Back to TODO (Q5:B)

**Investigation**: `updateTaskStatus()` already handles this for recurring tasks:
```kotlin
// When undone (justCompleted=false), inserts isCompleted=false for today's date.
// The UNIQUE INDEX on (taskId, date) with OnConflictStrategy.REPLACE overwrites the
// original isCompleted=true entry.
taskCompletionLogDao.insertLog(TaskCompletionLog(taskId = task.id, date = today, isCompleted = justCompleted))
```
For non-recurring tasks, clearing `completionTimestamp` (already done) makes them disappear from history (Source B only includes tasks with non-null timestamp).

**Decision**: No code changes needed for Q5. Existing `updateTaskStatus()` already correctly invalidates both sources.

**BUT NOTE**: There is a subtle bug in the existing code: when the status cycles COMPLETED  TODO, for recurring tasks, `refreshRecurringTasks()` only resets when `completedDay < today`. If the user completes and then undoes within the same day, `updateTaskStatus()` correctly inserts `isCompleted=false`. On the next day's reset, `completedDay < today` would be false for the undone task (since `completionTimestamp = null`). So the reset is skipped. The recurring task would still appear on home screen the next day with status TODO (since it was never reset). **This is correct behavior**  the undo was done on the same day, effectively nothing happened.

---

## Decision 6: `HistoryItem` Location (CO-001 Compliance)

**Investigation**: `HistoryItem` is a display model (combines task title + log date). If placed in `data/` layer, it leaks presentation logic. If placed in `presentation/history/` layer, Repository cannot return it.

**Decision**: Place `HistoryItem` in `presentation/history/` package. Repository exposes raw data (two new Flow methods). `GlobalHistoryViewModel` constructs `HistoryItem` by combining the two flows via `combine()` along with `getAllTasks()` for title lookup. This strictly respects CO-001.

---

## Decision 7: App Icon — PNG Bitmap Adaptive Icon

**Investigation**: The user provided `flow_logo.png` (located at `app/src/main/res/mipmap-hdpi/flow_logo.png`) as the production-ready icon artwork. The PNG already contains the complete design: dark `#0A0A0A` background, neon green `#39FF14` wave lines, and the "Flow" text label rendered at 320×320 px (sourced from the original SVG design).

**Decision**: Use the PNG directly as the adaptive icon foreground via a `<bitmap>` drawable XML. Scale `flow_logo.png` to the five required mipmap density sizes and save as `ic_launcher_foreground.png` in each `mipmap-*` folder. Create `res/drawable/ic_launcher_foreground.xml` as `<bitmap android:src="@mipmap/ic_launcher_foreground" android:gravity="center" />`. Background: solid `#0A0A0A` color in `ic_launcher_background.xml`. Use Android Adaptive Icon format (`<adaptive-icon>` in `mipmap-anydpi-v26/`) for API 26+. Also scale the PNG to standard launcher icon sizes as `ic_launcher.png` and `ic_launcher_round.png` density fallbacks for API 24–25.

**Rationale**: PNG bitmap approach requires zero conversion tooling — no VectorDrawable path data, no SVG text-to-path export, no Inkscape. The "Flow" text is preserved exactly as designed because it is baked into the PNG raster. Raster icons at the required mipmap densities are fully supported on all Android API levels from 24+. The trade-off (no infinite scalability) is acceptable for a launcher icon which has fixed target sizes.

---

## Decision 8: Help Icon  `isFirstLaunch` vs Separate State

**Investigation**: `SettingsManager` already has `isFirstLaunch` (drives onboarding on first open) and `hasSeenTutorial` (unused). Help icon overlay uses the same `OnboardingFlow` composable.

**Decision**: Add `showHelp: Boolean = false` to `HomeUiState`. `HomeViewModel` exposes `showHelp()` and `hideHelp()`. `HomeScreen` renders `OnboardingFlow` when `uiState.isFirstLaunch || uiState.showHelp`. Both trigger the same composable. On any dismissal (tap outside or "Let's Go!") the `HomeViewModel` calls both `completeOnboarding()` (if first launch) and `hideHelp()`.

`OnboardingFlow.kt` change: `Dialog(onDismissRequest = {})`  `Dialog(onDismissRequest = onDismiss)` where `onDismiss` is a new separate parameter from `onComplete`. This allows the caller to provide dismiss-without-completing behavior if desired.

---

## Decision 9: DB Version

**Conclusion**: DB version stays at **5**. Zero schema changes (no new tables, no new columns, no new indexes). Only new SELECT queries are added to existing DAOs. Room migrations are not triggered by query additions.

---

## Decision 10: Top Bar Icon Order (5 Icons)

**Decision**: Left-to-right order in the `actions` block of `TopAppBar` (renders right-to-left on screen):
`Settings | Help | History | Analytics | Timer`

This puts the most-used action (Timer) leftmost in the actions list  rightmost (most thumb-accessible) on screen. Settings and Help are less-frequent and placed further from thumb reach.
