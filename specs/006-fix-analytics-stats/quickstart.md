# Quickstart: Fix Analytics Statistics & History State Sync

**Branch**: `006-fix-analytics-stats` | **Date**: 2026-02-28

## What this fixes

Six related bugs all sharing the same root cause: analytics aggregation and history list queries
were written only for recurring tasks (stored in `task_logs`) and silently ignored non-recurring
tasks (stored permanently in `tasks`), and vice versa.

| Bug | Symptom | Fix area |
|---|---|---|
| Heatmap / analytics total | Only counts recurring completions | `TaskRepositoryImpl.getHeatMapData` |
| On-time count | Only counts non-recurring completions (misses recurring after daily reset) | `TaskDao.getCompletedOnTimeCount` + new `TaskCompletionLogDao` query |
| Missed count | Only counts non-recurring missed tasks | `TaskDao.getMissedDeadlineCount` + new `TaskCompletionLogDao` query |
| History list stays stale for recurring tasks after status edit from history screen | `task_logs` row never updated | `GlobalHistoryViewModel.saveEditTask` |
| Lifetime stats `totalCompleted` undercounts | Only CURRENTLY-completed tasks; recurring completions reset daily | `TaskRepositoryImpl.getLifetimeStats` |
| Current year stats `completedThisYear` undercounts | Only recurring logs | `TaskRepositoryImpl.getCurrentYearStats` |

## No schema changes

All fixes are query additions and computation logic only. No Room migration required.

## Development environment

```powershell
# Set up local paths (copy once, never commit)
Copy-Item .local\env.ps1.template .local\env.ps1
# Edit .local\env.ps1 and set $env:ANDROID_HOME to your Android SDK root
```

## Build & test commands

```powershell
# Tier 1 — unit tests (no device needed)
.\gradlew testDebugUnitTest

# Tier 2 — instrumented tests (device or AVD required)
# First: verify a device is connected (Constitution VIII)
$adb = if ($env:ANDROID_HOME) { "$env:ANDROID_HOME\platform-tools\adb.exe" } else { "adb" }
& $adb devices
# Then run:
.\gradlew connectedDebugAndroidTest
```

## TDD order (Constitution TDD Red-Green Protocol)

For every task below, write the failing test FIRST, confirm it fails, then write the fix.

### Task sequence summary

**Phase A — DAO layer (Tier 2 instrumented tests)**
1. Add `getCompletedNonRecurringInRange` to `TaskDao` + DAO test
2. Add `getEarliestNonRecurringCompletionDate` to `TaskDao` + DAO test
3. Modify `getMissedDeadlineCount` to add `AND isRecurring = 0` + DAO test
4. Add `getCompletedOnTimeCount` guard `AND isRecurring = 0` + verify DAO test
5. Add `getRecurringOnTimeCount` to `TaskCompletionLogDao` + DAO test
6. Add `getRecurringMissedCount` to `TaskCompletionLogDao` + DAO test
7. Add `getTotalCompletedLogCount` to `TaskCompletionLogDao` + DAO test

**Phase B — Repository layer (Tier 1 unit tests with fakes)**
8. Expose `getLogForTaskDate` on `TaskRepository` interface + `TaskRepositoryImpl`
9. Fix `getHeatMapData` to merge both flows + unit test (non-recurring completions included)
10. Fix `getCompletedOnTimeCount` to sum both sources + unit test
11. Fix `getMissedDeadlineCount` to sum both sources + unit test
12. Fix `getLifetimeStats.totalCompleted` to include recurring logs + unit test
13. Fix `getCurrentYearStats.completedThisYear` to include non-recurring + unit test
14. Fix `getEarliestCompletionDate` to take min of both sources + unit test

**Phase C — ViewModel layer (Tier 1 unit tests with FakeTaskRepository)**
15. Fix `GlobalHistoryViewModel.saveEditTask` — update recurring log on COMPLETED→non-COMPLETED + unit tests
16. Update `FakeTaskRepository` to implement `getLogForTaskDate`

**Phase D — Analytics ViewModel audit (FR-011)**
17. Audit all other `AnalyticsUiState` fields; add tests for any additionally affected metrics

**Phase E — Instrumented integration & E2E**
18. Instrumented DAO tests (Phase A items above)
19. `HistoryScreenTest` — recurring task status change from history removes it from list

## Key file paths

```
app/src/main/java/com/flow/
  data/local/
    TaskDao.kt
    TaskCompletionLogDao.kt
    TaskCompletionLog.kt
  data/repository/
    TaskRepository.kt
    TaskRepositoryImpl.kt
  presentation/
    history/GlobalHistoryViewModel.kt
    analytics/AnalyticsViewModel.kt

app/src/test/java/com/flow/
  fake/FakeTaskRepository.kt
  presentation/analytics/AnalyticsViewModelTest.kt
  presentation/history/GlobalHistoryViewModelTest.kt
  data/repository/ (new: TaskRepositoryImplTest additions)

app/src/androidTest/java/com/flow/
  data/ (new: TaskDaoTest, TaskCompletionLogDaoTest additions)
  HistoryScreenTest.kt
```

## Acceptance check (manual, after all tests pass)

1. Complete 4 recurring + 2 one-time tasks on the same day
2. Open Analytics → confirm total = 6, on-time = 6 (if all were on time)
3. Miss 3 recurring + 2 one-time tasks (let their due date pass)
4. Open Analytics → confirm missed = 5
5. Find a completed recurring task in history → edit status to "To Do" → confirm:
   - Task disappears from history immediately
   - Task appears on Home screen To Do list
