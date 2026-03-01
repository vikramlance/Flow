# Tasks: Fix Task End Time Bug � Iteration 2

**Branch**: `005-fix-task-end-time`
**Input**: Design documents from `specs/005-fix-task-end-time/`
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)
**Status**: Complete — all 22 tasks implemented and tested (unit + instrumented)� specify ?, plan ?

> **Why Iteration 2**: Iteration 1 fixed the wrong layer (UI date-picker confirms).
> The real bugs are in `TaskRepositoryImpl.updateTask` (strips H:M from dueDate before every Room write)
> and `GlobalHistoryViewModel.saveEditTask` (strips H:M a second time before passing to the repository).
> Additionally: History screen `TaskEditSheet` has no time pickers; task cards do not show start date/time.
>
> **TDD note**: Per constitution, failing tests are written BEFORE implementation in every phase.
> `FakeTaskRepository.updateTask` stores verbatim (no normalisation), so a FakeRepo unit test catches
> the ViewModel-layer bug (GlobalHistoryViewModel normalises before FakeRepo). The Repository-layer bug
> requires the real `TaskRepositoryImpl` � tested in `TaskRepositoryImplTest.kt` with in-memory Room or a DAO spy.

---

## Legend

- **[P]**: Parallelisable with adjacent same-phase tasks (different files, no incomplete deps)
- **[US1]**: User Story 1 � exact dueDate preserved through Repository + ViewModel save paths
- **[US2]**: User Story 3 (spec.md) — History screen `TaskEditSheet` gains start-time + due-time pickers
- **[US3]**: User Story 4 (spec.md) — Home screen task card shows start date and time

---

## Phase 1: Cleanup + Baseline

- [X] T001 Verify build baseline: `./gradlew :app:assembleDebug` � zero errors required before any changes
- [X] T002 [P] Delete `app/src/test/java/com/flow/presentation/home/HomeScreenEndTimeTest.kt` and `app/src/test/java/com/flow/presentation/history/TaskEditSheetEndTimeTest.kt`; remove the utility-only assertion `taskEditSheet_dueDatePicker_preservesDueDateTime` from `GlobalHistoryScreenTest.kt`
- [X] T003 Run unit tests after cleanup: `./gradlew :app:testDebugUnitTest` � zero failures confirms no production code was accidentally removed

---

## Phase 2: US1 � Repository + ViewModel Bug Fix (TDD: RED ? GREEN)

### RED � Write failing tests before any implementation code

- [X] T004 [P] [US1] **[RED]** Write three failing unit tests in new file `app/src/test/java/com/flow/presentation/history/HistoryViewModelSavesExactDueDateTimeTest.kt`:
  1. `globalHistoryViewModel_saveEditTask_preservesDueDateTimeExact` — seed `FakeTaskRepository` with a task whose `dueDate` has H=22 M=45; call `GlobalHistoryViewModel.saveEditTask(task)` via `runTest`; assert `fakeRepo.allTasksFlow.value.find { it.id == task.id }!!.dueDate` has H=22 M=45
  2. `globalHistoryViewModel_saveEditTask_preservesTimeOnDateOnlyChange` (FR-007) — same setup; change only the date on the task (keep H=22 M=45); assert the H:M is unchanged after save
  3. `globalHistoryViewModel_saveEditTask_preservesDueDateTime_recurringTask` (FR-008) — same flow with `isRecurring = true`; assert H=22 M=45 preserved

  All three tests **MUST FAIL** before T006 (GlobalHistoryViewModel calls `normaliseToMidnight` before FakeRepo, so FakeRepo stores midnight today)
- [X] T005 [P] [US1] **[RED]** Write failing test `updateTask_preservesDueDateTimeExact` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt`: use the real `TaskRepositoryImpl` backed by a DAO mock/spy capturing the call (e.g., Mockito `captor` on `TaskDao.updateTask`) — lighter than in-memory Room per Principle IX; call `repository.updateTask(task)` with `dueDate` at H=15 M=30; assert the stored / captured value has H=15 M=30 — test **MUST FAIL** before T007 (real `TaskRepositoryImpl` calls `normaliseToMidnight` before DAO)

### GREEN � Implement the fixes

- [X] T006 [US1] Fix `GlobalHistoryViewModel.saveEditTask` in `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt`: remove `dueDate = updated.dueDate?.let { normaliseToMidnight(it) }` from the `updated.copy(...)` passed to `repository.updateTask` � pass `dueDate` through unchanged; **T004 must now pass**
- [X] T007 [US1] Fix `TaskRepositoryImpl.updateTask` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt`: remove `dueDate = task.dueDate?.let { normaliseToMidnight(it) }` from the `task.copy(...)` inside `taskDao.updateTask(...)` � pass `dueDate` through as `task.dueDate` unchanged; **T005 must now pass**
- [X] T008 [US1] Run full unit test suite: `./gradlew :app:testDebugUnitTest` � T004 and T005 must both pass; zero other failures

---

## Phase 3: US3 (spec.md) — History Screen Time Pickers (TDD: RED → GREEN)

### RED � Write failing instrumented test first

- [X] T009 [US2] **[RED]** Write failing instrumented test `taskEditSheet_timePicker_savesExactTime` in `app/src/androidTest/java/com/flow/presentation/history/GlobalHistoryScreenTest.kt`: render `TaskEditSheet`, simulate due-date picker confirm chained into due-time picker confirm selecting H=22 M=45, press Save, assert the `onSave` callback receives a task whose `dueDate` has H=22 M=45 � test **MUST FAIL** before T010-T014 (no time picker exists yet in `TaskEditSheet`)

### GREEN � Implement time pickers

- [X] T010 [US2] Add `showStartTimePicker` and `showDueTimePicker` state variables (`remember { mutableStateOf(false) }`) to `TaskEditSheet` in `app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt`
- [X] T011 [US2] Update start-date button label format from `"MMM d, yyyy"` to `"MMM d, HH:mm"` in `TaskEditSheet`; update start-date `DatePickerDialog` confirm lambda to set `startDateMs = mergeDateTime(utcDateToLocalMidnight(it), startDateMs)` then `showStartDatePicker = false; showStartTimePicker = true`
- [X] T012 [US2] Add start-time picker `Dialog` to `TaskEditSheet` (`if (showStartTimePicker)`): `rememberTimePickerState(initialHour, initialMinute)` from current `startDateMs`; on Confirm � `val cal = Calendar.getInstance().apply { timeInMillis = startDateMs; set(HOUR_OF_DAY, state.hour); set(MINUTE, state.minute); set(SECOND, 0); set(MILLISECOND, 0) }; startDateMs = cal.timeInMillis; showStartTimePicker = false`; NeonGreen/SurfaceDark styling matching `EditTaskDialog`
- [X] T013 [US2] Update due-date button label format to `"MMM d, HH:mm"`; update due-date `DatePickerDialog` confirm lambda to set `dueDateMs = resolveEditTaskSheetDueDate(it, dueDateMs)` then `showDueDatePicker = false; showDueTimePicker = true`
- [X] T014 [US2] Add due-time picker `Dialog` to `TaskEditSheet` (`if (showDueTimePicker)`): same `TimePicker` pattern as T012 but operating on `dueDateMs`; if `dueDateMs` is null when picker opens, initialise `Calendar` from `defaultEndTime()`
- [X] T015 [US2] Run unit tests: `./gradlew :app:testDebugUnitTest` � zero failures
- [X] T016 [US2] Run history screen instrumented tests: `./gradlew :app:connectedDebugAndroidTest --tests "com.flow.presentation.history.GlobalHistoryScreenTest"` � T009 must now pass; all other history tests must still pass

---

## Phase 4: US4 (spec.md) — Task Card Start Date/Time (additive)

- [X] T017 [US3] In `TaskItem` composable in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`: directly below the existing "Target: �" / "Daily Target" text block, add `Text(text = "Start: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(task.startDate))}", style = MaterialTheme.typography.labelSmall, color = if (isCompleted) contentColor else Color.Gray)` � every task has a non-null `startDate`
- [X] T018 [US3] Run `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` � zero failures
- [X] T019 [US3] Run full instrumented tests: `./gradlew :app:connectedDebugAndroidTest` � zero failures; visually confirm task card shows both "Start: Mar 1, 09:00" and "Target: Mar 1, 23:59" for a newly created task

---

## Final Phase: Full Suite Sign-Off

- [X] T020 Full unit test suite: `./gradlew :app:testDebugUnitTest` � zero failures
- [X] T021 Full instrumented test suite — run Principle VIII device pre-flight check first (`adb devices`), then `./gradlew :app:connectedDebugAndroidTest` — zero failures; target ≥79 instrumented tests pass (78 pre-existing + at least 1 new from T009)
- [X] T022 Security + constitution check: (a) no credentials/PII in tracked files; (b) `.gitignore` unchanged; (c) search `normaliseToMidnight` across `app/src/main` � confirm zero results on any `dueDate`/`startDate` **store** path (only filtering/comparison calls remain); (d) no sensitive data in production logs

---

## Dependency Graph

```
T001
 +-- T002 [P] -- T003
                   �
         +--------------------------------------------+
         �                                             �
     T004 [P][RED]                               T005 [P][RED]
     T006 (GREEN for T004)                       T007 (GREEN for T005)
         +----------------- T008 ---------------------+
                              �
               +----------------------------------+
               �                                  �
           T009 [RED]                           T017
        T010 ? T011 ? T012                      T018
        T013 ? T014                             T019
           T015 ? T016                            �
               +----------------------------------+
                                 �
                           T020 ? T021 ? T022
```

### Parallel Opportunities

**Phase 2 � both RED tests can be written in parallel (different files):**
```
Thread A: HistoryViewModelSavesExactDueDateTimeTest.kt    ? T004
Thread B: add test case to TaskRepositoryImplTest.kt      ? T005
```
**Phase 2 � both GREEN fixes can be applied in parallel (different files):**
```
Thread A: GlobalHistoryViewModel.kt � remove normaliseToMidnight  ? T006
Thread B: TaskRepositoryImpl.kt    � remove normaliseToMidnight  ? T007
```
**Phase 3 + Phase 4 can start in parallel after T008 (different files/screens).**

---

## Task Count Summary

| Phase | Tasks | Stories Covered |
|---|---|---|
| Phase 1: Cleanup + Baseline | 3 (T001�T003) | � |
| Phase 2: Repository + ViewModel Fix | 5 (T004�T008) | US1 |
| Phase 3: History Screen Time Pickers | 8 (T009–T016) | US3 (spec) |
| Phase 4: Task Card Start Date | 3 (T017–T019) | US4 (spec) |
| Final: Sign-off | 3 (T020�T022) | Cross-cutting |
| **Total** | **22** | **US1, [US2], [US3]** (spec.md: US1–US4) |

### Independent Test Criteria per Story

| Story | Independently testable when� |
|---|---|
| US1 | T004 + T005 both pass (ViewModel + Repository save path tests green) |
| US3 (spec) | T009 passes on device (time picker end-to-end instrumented test green) |
| US4 (spec) | Task card "Start:" label visible in T019 instrumented run |

### Suggested MVP Scope

US1 (T001–T008) alone fully fixes the reported bug. [US2] (spec US3, History pickers) and [US3] (spec US4, task card) are additive improvements. Shipping T001–T008 resolves the critical issue; the remaining stories can follow.
