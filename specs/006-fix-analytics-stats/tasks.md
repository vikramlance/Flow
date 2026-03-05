# Tasks: Fix Analytics Statistics & History State Sync (006)

**Input**: Design documents from `/specs/006-fix-analytics-stats/`
**Branch**: `006-fix-analytics-stats`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)
**Data Model**: [data-model.md](data-model.md) | **Contracts**: [contracts/internal-contracts.md](contracts/internal-contracts.md)

**TDD MANDATORY**: Write the failing test FIRST. Confirm it fails. Then write the fix. Run `.\gradlew testDebugUnitTest` after every task.

---

## Dependencies

```
Phase 1 (Setup)
  â””â”€â”€ Phase 2 (Foundational: DAO Additions)
        â”œâ”€â”€ Phase 3 (US1: Analytics Completed Count â€” heatmap, on-time, lifetime, year stats)
        â”œâ”€â”€ Phase 4 (US2: History List State Sync â€” saveEditTask fix)
        â””â”€â”€ Phase 5 (US3: Missed Count + FR-011 Analytics Audit)
              â””â”€â”€ Final Phase (Polish & Non-Regression Sweep)
```

Phase 3 and Phase 4 are independent of each other; Phase 5 depends only on the DAO additions in Phase 2. **Note**: Phase 3 and Phase 5 both edit `TaskRepositoryImpl.kt` â€” do not execute them concurrently without a dedicated branch strategy.

---

## Parallel Execution Notes

Within Phase 2: T003â€“T009 are [P] â€” all add distinct new queries to separate files with no ordering dependency between them.
Within Phase 3: T019â€“T024 (failing test writes) are [P]; T026â€“T030 (implementations) MUST follow their corresponding test.
Within Phase 5: T043â€“T046 are [P] with each other.

---

## Phase 1: Setup

**Purpose**: Security scan and initial compile gate before any code changes.

- [X] T001 Run security scan to establish clean baseline: `& .specify\scripts\powershell\security-scan.ps1` â€” must exit 0 before any edits begin. **If this session will include any Tier 2/3 tasks (T042, T056), run the device connectivity pre-flight check now** before any other tasks: `$adb = if ($env:ANDROID_HOME) { "$env:ANDROID_HOME\platform-tools\adb.exe" } else { "adb" }; & $adb devices`
- [X] T002 Confirm project builds clean from current `006-fix-analytics-stats` branch: `.\gradlew compileDebugKotlin` â€” fix any pre-existing compile errors before proceeding

---

## Phase 2: Foundational â€” DAO Layer Additions

**Purpose**: All new and modified DAO queries that every user story depends on. Must be complete before Phases 3â€“5. TDD: write instrumented DAO tests first (they will fail until the queries are added).

**âš  CRITICAL**: No user story work begins until this phase is complete.

- [X] T003 [P] Write failing instrumented DAO test `getCompletedNonRecurringInRange_returnsOnlyNonRecurringInWindow` in `app/src/androidTest/java/com/flow/data/TaskDaoTest.kt` â€” insert 2 non-recurring completed tasks inside range, 1 outside, 1 recurring inside; assert only the 2 non-recurring inside-range timestamps returned
- [X] T004 [P] Write failing instrumented DAO test `getEarliestNonRecurringCompletionDate_returnsMinTimestamp` in `app/src/androidTest/java/com/flow/data/TaskDaoTest.kt` â€” insert 3 non-recurring tasks with different completionTimestamps; assert MIN is returned; assert null when no completions
- [X] T005 [P] Write failing instrumented DAO test `getMissedDeadlineCount_excludesRecurringTasks` in `app/src/androidTest/java/com/flow/data/TaskDaoTest.kt` â€” insert 3 overdue non-recurring tasks + 2 overdue recurring tasks (status=TODO); assert count = 3 (not 5)
- [X] T006 [P] Write failing instrumented DAO test `getCompletedOnTimeCount_excludesRecurringRows` in `app/src/androidTest/java/com/flow/data/TaskDaoTest.kt` â€” insert 2 non-recurring on-time tasks + 1 recurring task currently in COMPLETED state (transient row); assert tasks DAO returns only the 2 non-recurring count
- [X] T007 [P] Write failing instrumented DAO test `getRecurringOnTimeCount_countsLogsWithinScheduledDay` in `app/src/androidTest/java/com/flow/data/TaskCompletionLogDaoTest.kt` â€” insert 3 completed logs where `timestamp <= date + 86340000` and 2 where `timestamp > date + 86340000`; assert count = 3
- [X] T008 [P] Write failing instrumented DAO test `getRecurringMissedCount_countsPastIncompleteLogs` in `app/src/androidTest/java/com/flow/data/TaskCompletionLogDaoTest.kt` â€” insert 3 logs with `isCompleted=0` and `date < todayMidnight`, 2 with `isCompleted=0` and `date = todayMidnight`; assert count = 3
- [X] T009 [P] Write failing instrumented DAO test `getTotalCompletedLogCount_returnsAllCompletedLogs` in `app/src/androidTest/java/com/flow/data/TaskCompletionLogDaoTest.kt` â€” insert 5 completed logs + 2 incomplete logs; assert count = 5
- [X] T010 Add `getCompletedNonRecurringInRange(startMs: Long, endMs: Long): Flow<List<Long>>` to `app/src/main/java/com/flow/data/local/TaskDao.kt` â€” exact SQL per contracts/internal-contracts.md Contract 1
- [X] T011 Add `getEarliestNonRecurringCompletionDate(): Long?` (suspend) to `app/src/main/java/com/flow/data/local/TaskDao.kt` â€” exact SQL per contracts/internal-contracts.md Contract 1
- [X] T012 Modify `getMissedDeadlineCount(now: Long): Int` in `app/src/main/java/com/flow/data/local/TaskDao.kt` â€” add `AND isRecurring = 0` to the existing WHERE clause
- [X] T013 Write failing instrumented DAO test `getCompletedOnTimeCount_excludesTransientRecurringRows` in `app/src/androidTest/java/com/flow/data/TaskDaoTest.kt` â€” insert 1 recurring task currently in COMPLETED state + 2 non-recurring on-time tasks; assert `getCompletedOnTimeCount()` returns 2 (not 3); then add `AND isRecurring = 0` guard to `getCompletedOnTimeCount()` in `app/src/main/java/com/flow/data/local/TaskDao.kt`
- [X] T014 Add `getRecurringOnTimeCount(): Int` (suspend) to `app/src/main/java/com/flow/data/local/TaskCompletionLogDao.kt` â€” exact SQL per contracts/internal-contracts.md Contract 2
- [X] T015 Add `getRecurringMissedCount(todayMidnight: Long): Int` (suspend) to `app/src/main/java/com/flow/data/local/TaskCompletionLogDao.kt` â€” exact SQL per contracts/internal-contracts.md Contract 2
- [X] T016 Add `getTotalCompletedLogCount(): Int` (suspend) to `app/src/main/java/com/flow/data/local/TaskCompletionLogDao.kt` â€” exact SQL per contracts/internal-contracts.md Contract 2
- [X] T017 Compile instrumented test apk gate: `.\gradlew compileDebugAndroidTestKotlin` â€” must compile cleanly before running any instrumented tests
- [X] T018 Run full Tier 1 unit test suite: `.\gradlew testDebugUnitTest` â€” must be zero failures before Phase 3 begins

---

## Phase 3: User Story 1 â€” Accurate Completed Task Counts in Analytics (Priority: P1)

**US1 Goal**: The Analytics screen heatmap, total-completed chip, on-time count, lifetime stats, and current-year stats all aggregate both recurring (`task_logs`) and non-recurring (`tasks.completionTimestamp`) completions so that the displayed numbers equal the true total across both task types.

**Independent Test**: Complete 4 recurring + 2 one-time tasks; open Analytics; confirm total = 6 and on-time = 6 (assuming all on time). Verify lifetime stats and year stats also reflect 6.

### Tests for US1 (Tier 1 â€” write first; must FAIL before T024, PASS after)

- [X] T019 [P] [US1] Write failing unit test `getHeatMapData_includesNonRecurringCompletions` in `app/src/test/java/com/flow/presentation/analytics/AnalyticsViewModelTest.kt` (or new `TaskRepositoryImplTest.kt`) â€” provide fake that returns 3 recurring log entries on day D and 2 non-recurring completions on day D; assert heatmap[D] = 5
- [X] T020 [P] [US1] Write regression (green-at-start) unit test `getHeatMapData_zeroWhenNoCompletions` in same test file â€” empty both sources; assert empty map (no false positives); this test is expected to pass before and after implementation
- [X] T021 [P] [US1] Write failing unit test `getCompletedOnTimeCount_sumsBothSources` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” fake returns 2 non-recurring on-time + 3 recurring on-time logs; assert result = 5
- [X] T022 [P] [US1] Write failing unit test `getLifetimeStats_totalIncludesRecurringLogs` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” 4 recurring completed logs + 2 non-recurring COMPLETED tasks; assert `totalCompleted = 6`
- [X] T023 [P] [US1] Write failing unit test `getCurrentYearStats_completedThisYearIncludesNonRecurring` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” 3 recurring logs within year + 2 non-recurring completions within year; assert `completedThisYear = 5`
- [X] T024 [P] [US1] Write failing unit test `getEarliestCompletionDate_returnsMinOfBothSources` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” log earliest = day 100, non-recurring earliest = day 50; assert result = day 50

### Implementation for US1

- [X] T025 [US1] Expose `getLogForTaskDate(taskId: Long, date: Long): TaskCompletionLog?` on `TaskRepository` interface in `app/src/main/java/com/flow/data/repository/TaskRepository.kt`; implement delegation in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` *(TDD driver: T032 is the first failing test that requires this method; `FakeTaskRepository` stub is added in T036)*
- [X] T026 [US1] Fix `getHeatMapData(startMs, endMs)` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” use `combine(taskCompletionLogDao.getLogsBetween(startMs,endMs), taskDao.getCompletedNonRecurringInRange(startMs,endMs))` and merge both maps by midnight-epoch key (sum overlapping counts), per contracts/internal-contracts.md Contract 4
- [X] T027 [US1] Fix `getCompletedOnTimeCount()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” return `taskDao.getCompletedOnTimeCount() + taskCompletionLogDao.getRecurringOnTimeCount()`, per contracts/internal-contracts.md Contract 5
- [X] T028 [US1] Fix `getLifetimeStats()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” compute `total = taskCompletionLogDao.getTotalCompletedLogCount() + taskDao.getAllTasks().firstOrNull()?.count { !it.isRecurring && it.status == TaskStatus.COMPLETED } ?: 0`, per contracts/internal-contracts.md Contract 5
- [X] T029 [US1] Fix `getCurrentYearStats()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” add non-recurring completions within [jan1, dec31] to `completedThisYear`, per contracts/internal-contracts.md Contract 5
- [X] T030 [US1] Fix `getEarliestCompletionDate()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” return `listOfNotNull(taskCompletionLogDao.getEarliestCompletionDate(), taskDao.getEarliestNonRecurringCompletionDate()).minOrNull()`, per contracts/internal-contracts.md Contract 5
- [X] T031 [US1] Run Tier 1 unit test suite â€” verify T019â€“T024 now pass (green): `.\gradlew testDebugUnitTest`

---

## Phase 4: User Story 2 â€” History List Reflects Live Task State (Priority: P1)

**US2 Goal**: When a user changes a recurring task's status from "Completed" to "To Do" or "In Progress" directly from the History screen, the task immediately disappears from the history list and appears on the Home screen â€” matching the already-correct behaviour for non-recurring tasks and for changes made from the Home screen.

**Independent Test**: Find a completed recurring task in history; change its status to "To Do" from the history edit sheet; confirm it disappears from history immediately and appears in the Home screen to-do list. Confirm non-recurring path still works.

### Tests for US2 (Tier 1 â€” T032â€“T033 must FAIL before T037 (implementation), PASS after; T034 and T035 are regression tests that must pass before and after implementation)

- [X] T032 [US2] Write failing unit test `saveEditTask_recurringCompletedToTodo_updatesLogToNotCompleted` in `app/src/test/java/com/flow/presentation/history/GlobalHistoryViewModelTest.kt` â€” ViewModel state contains a recurring task with `status=COMPLETED`; call `saveEditTask(task.copy(status=TaskStatus.TODO))`; assert `fakeRepo.updatedLog?.isCompleted == false` *(FR-006 home-screen appearance is covered at Tier 2 by T041; unit layer asserts only the log update)*
- [X] T033 [US2] Write failing unit test `saveEditTask_recurringCompletedToInProgress_updatesLogToNotCompleted` in `app/src/test/java/com/flow/presentation/history/GlobalHistoryViewModelTest.kt` â€” same setup; call with `status=IN_PROGRESS`; assert `fakeRepo.updatedLog?.isCompleted == false` *(FR-006 home-screen appearance is covered at Tier 2 by T041; unit layer asserts only the log update)*
- [X] T034 [US2] Write passing regression test `saveEditTask_nonRecurring_doesNotTouchLog` in `app/src/test/java/com/flow/presentation/history/GlobalHistoryViewModelTest.kt` â€” same setup but `isRecurring=false`; assert `fakeRepo.updatedLog == null` (log path not invoked)
- [X] T035 [US2] Write passing regression test `saveEditTask_doesNotInvokeUpdateTaskStatus` in `app/src/test/java/com/flow/presentation/history/GlobalHistoryViewModelTest.kt` â€” call `saveEditTask` with any input; assert that `repository.updateTaskStatus` is never called (confirming the Home screen code path is not touched by the history fix)
- [X] T035a [US2] Write unit test `historyList_neverShowsNonCompletedTask` in `app/src/test/java/com/flow/presentation/history/GlobalHistoryViewModelTest.kt` â€” insert a recurring TODO task into the fake data source; assert it does not appear in the history list observable (FR-007 invariant: history shows Completed-only)

### Implementation for US2

- [X] T036 [US2] Update `FakeTaskRepository` in `app/src/test/java/com/flow/fake/FakeTaskRepository.kt` â€” implement `getLogForTaskDate(taskId, date)` to return from a `MutableMap<Pair<Long,Long>, TaskCompletionLog>`; add a `var lastUpdatedLog: TaskCompletionLog?` capture field for test assertions
- [X] T037 [US2] Fix `saveEditTask(updated: TaskEntity)` in `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt` â€” when `updated.isRecurring && originalTask.status == COMPLETED && updated.status != COMPLETED`: compute `completionDate = normaliseToMidnight(originalTask.completionTimestamp)`; call `repository.getLogForTaskDate(updated.id, completionDate)?.let { repository.updateLog(it.copy(isCompleted = false)) }`; then persist task row as before, per contracts/internal-contracts.md Contract 6
- [X] T038 [US2] Run Tier 1 unit test suite â€” verify T032â€“T033 now pass (green): `.\gradlew testDebugUnitTest`
- [X] T039 [US2] Device connectivity pre-flight check (Constitution VIII): run `$adb = if ($env:ANDROID_HOME) { "$env:ANDROID_HOME\platform-tools\adb.exe" } else { "adb" }; & $adb devices` â€” if no device found, STOP and request device connection before continuing with T040â€“T041
- [X] T040 [US2] Compile instrumented test gate: `.\gradlew compileDebugAndroidTestKotlin`
- [X] T041 [US2] Expand `app/src/androidTest/java/com/flow/HistoryScreenTest.kt` â€” add test `recurringTask_statusChangedFromHistory_disappearsFromList`: insert a recurring task + completed log; open history screen; trigger status change to TODO from history edit sheet; assert item no longer in history list; assert item present in home screen todo section
- [X] T042 [US2] Run connected instrumented tests: `.\gradlew connectedDebugAndroidTest` â€” must pass zero failures

---

## Phase 5: User Story 3 â€” Accurate Missed Task Counts in Analytics (Priority: P2)

**US3 Goal**: The Analytics screen missed-task count includes all missed tasks â€” both recurring (`task_logs WHERE isCompleted=0 AND date < today`) and non-recurring (`tasks WHERE dueDate < now AND status != COMPLETED AND isRecurring=0`) â€” so the displayed total equals the true combined missed count.

**Independent Test**: Let 3 recurring + 2 non-recurring tasks go past their due dates without completing them; open Analytics; confirm missed count = 5.

### Tests for US3 (Tier 1 â€” write first; must FAIL before T047, PASS after)

- [X] T043 [P] [US3] Write failing unit test `getMissedDeadlineCount_sumsBothSources` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” fake DAO returns 2 non-recurring missed tasks; fake log DAO returns 3 recurring missed logs; assert result = 5
- [X] T044 [P] [US3] Write failing unit test `getMissedDeadlineCount_onlyRecurringMissed_countedFromLogs` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” 0 non-recurring missed, 3 recurring missed logs; assert result = 3
- [X] T045 [P] [US3] Write passing regression test `getMissedDeadlineCount_onlyNonRecurring_unchanged` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” 2 non-recurring missed, 0 recurring missed logs; assert result = 2 (existing behaviour preserved)

### Implementation for US3

- [X] T047 [US3] Fix `getMissedDeadlineCount()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” return `taskDao.getMissedDeadlineCount(System.currentTimeMillis()) + taskCompletionLogDao.getRecurringMissedCount(normaliseToMidnight(System.currentTimeMillis()))`, per contracts/internal-contracts.md Contract 5
- [X] T048 [US3] *(FR-011 audit and additional fixes moved to Phase 5.5 â€” T046, T048a, T051a)*
- [X] T049 [US3] Run Tier 1 unit test suite â€” verify T043â€“T044 now pass (green): `.\gradlew testDebugUnitTest`
- [X] T050 [US3] Expand `AnalyticsViewModelTest` in `app/src/test/java/com/flow/presentation/analytics/AnalyticsViewModelTest.kt` â€” add tests: (a) `missedCount_includesRecurringMissedLogs`; (b) `missedCount_zeroWhenNoMissed`; (c) `heatMapAndTotal_reflectBothTaskTypes` end-to-end through ViewModel layer
- [X] T051 [US3] Run Tier 1 unit test suite: `.\gradlew testDebugUnitTest`

---

## Phase 5.5: FR-011 Analytics Audit

**Purpose**: Scope-wide audit of ALL Analytics screen metrics for the same recurrence-type exclusion root cause. This is independent of US1â€“US3 and is a dependency of the Final Phase.

- [X] T046 [P] Audit all fields of `AnalyticsUiState` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt` for the recurrence-type exclusion bug â€” check `currentStreak`, `bestStreak`, `forestTreeCount`, `onTimePct` derivation, `availableYears` computation; document findings in `specs/006-fix-analytics-stats/research.md` under "Finding 9 â€” FR-011 Audit Results"
- [X] T048a [P] For each additional affected metric found in T046: write a failing unit test FIRST, then apply the fix in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` and/or `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt`; if no additional metrics are affected, confirm in research.md Finding 9 and close
- [X] T051a Run Tier 1 unit test suite: `.\gradlew testDebugUnitTest` â€” all T046/T048a fixes must be green before Final Phase

---

## Final Phase: Polish & Cross-Cutting Concerns

**Purpose**: Full non-regression sweep, instrumented DAO verification, and security gate.

- [X] T052 [P] Confirm `app/src/androidTest/java/com/flow/data/TaskDaoTest.kt` exists (created in Phase 2 T003â€“T006); run all T003â€“T006 DAO instrumented tests and confirm green on device
- [X] T053 [P] Confirm `app/src/androidTest/java/com/flow/data/TaskCompletionLogDaoTest.kt` exists (created in Phase 2 T007â€“T009); run all T007â€“T009 DAO instrumented tests and confirm green on device
- [X] T054 Device connectivity pre-flight check (Constitution VIII): `$adb = if ($env:ANDROID_HOME) { "$env:ANDROID_HOME\platform-tools\adb.exe" } else { "adb" }; & $adb devices` â€” if no device found, STOP and request device connection
- [X] T055 Compile instrumented test gate: `.\gradlew compileDebugAndroidTestKotlin`
- [X] T056 Run full instrumented test suite (all Tier 2/3 tests): `.\gradlew connectedDebugAndroidTest` â€” must pass with zero failures; confirms DAO queries, repository contracts, and history screen behaviour on a real (or virtual) device
- [X] T057 Run full Tier 1 unit test suite final sweep: `.\gradlew testDebugUnitTest` â€” must be zero failures; this is the final non-regression gate
- [X] T058 Security scan: `& .specify\scripts\powershell\security-scan.ps1` â€” must exit 0; no credentials, PII, or machine paths in any tracked file
- [X] T059 Consistency check â€” verify layer boundaries: (a) no direct DAO calls from ViewModel layer; (b) all new logic in `TaskRepositoryImpl` and DAOs; (c) `GlobalHistoryViewModel.saveEditTask` only calls repository interfaces; (d) `AnalyticsViewModel` unchanged except for correct data from repository; (e) run a snapshot or screenshot comparison of the heatmap composable confirming the `Map<Long, Int>` â†’ bar rendering pipeline is unaffected (AL-002 area d)

---

## Implementation Strategy

**MVP (Phase 3 only â€” US1)**: Completing Phases 1â€“3 alone delivers the highest-impact fix: heatmap, total, on-time, lifetime stats, and year stats all include non-recurring tasks. The analytics screen accurately reflects both task types. History list stale-entry bug (US2) and missed count (US3) remain outstanding but do not block the core analytics accuracy.

**Full delivery order**: 1 â†’ 2 â†’ 3 â†’ 4 â†’ 5 â†’ Final. Phases 3 and 4 can be worked in parallel by two developers since they touch non-overlapping files (`TaskRepositoryImpl` + `AnalyticsViewModelTest` for Phase 3; `GlobalHistoryViewModel` + `GlobalHistoryViewModelTest` for Phase 4).

**Task count summary**:
- Phase 1: 2 tasks
- Phase 2: 16 tasks (7 test-write, 7 DAO implementation, 1 compile gate, 1 unit test run)
- Phase 3 (US1): 13 tasks (6 test-write, 6 implementation, 1 test run)
- Phase 4 (US2): 11 tasks (4 test-write, 3 implementation, 1 pre-flight, 1 compile, 1 instrumented, 1 test run)
- Phase 5 (US3): 9 tasks (3 test-write, 1 audit, 2 implementation, 3 test runs)
- Final: 8 tasks
- **Total: 59 tasks**

**Parallel opportunities per phase**:
- Phase 2: T003â€“T009 all parallel (distinct files)
- Phase 3: T019â€“T024 parallel (test writes); T025 then T026â€“T030 parallel (distinct repository methods)
- Phase 5: T043â€“T045 parallel; T046 parallel with Phase 3/4

**Independent test criteria**:
- US1: `.\gradlew testDebugUnitTest` passes all new T019â€“T024 cases after T026â€“T030 implemented
- US2: `.\gradlew testDebugUnitTest` passes T032â€“T033 after T037; `.\gradlew connectedDebugAndroidTest` passes T041
- US3: `.\gradlew testDebugUnitTest` passes T043â€“T044 after T047
