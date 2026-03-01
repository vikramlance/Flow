$content = @"
# Tasks: Fix Task End Time Bug — Iteration 2

**Branch**: ``005-fix-task-end-time``
**Input**: Design documents from ``/specs/005-fix-task-end-time/``
**Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)
**Status**: Awaiting ``/speckit.plan`` — tasks below are the implementation target list

> **Why Iteration 2**: Iteration 1 fixed the wrong layer (UI date-picker confirms).
> The real bug is that ``TaskRepositoryImpl.updateTask`` and ``GlobalHistoryViewModel.saveEditTask``
> both call ``normaliseToMidnight()`` on ``dueDate`` when saving, unconditionally wiping the time to
> midnight. Additionally, the History screen has no time pickers, and task cards do not show start time.

---

## Legend

- **[P]**: Can run in parallel with adjacent tasks (different files, no incomplete dependencies)
- **[US1]**: User Story 1 — Saved task retains exact time (repository + ViewModel fix)
- **[US2]**: User Story 2 — History screen edit has full date+time selection
- **[US3]**: User Story 3 — Start date/time on task card

---

## Phase 1: Cleanup + Baseline

_Revert ineffective iteration-1 tests that only verified utility functions, not the save path._

- [ ] T001 Verify current build state: ``./gradlew :app:assembleDebug`` — zero errors
- [ ] T002 [P] Remove ``TaskEditSheetEndTimeTest.kt`` and ``HomeScreenEndTimeTest.kt`` (tested utility functions only, not the save path); remove or rewrite the utility-only assertion in ``GlobalHistoryScreenTest.taskEditSheet_dueDatePicker_preservesDueDateTime`` to be end-to-end
- [ ] T003 Run unit tests: ``./gradlew :app:testDebugUnitTest`` — no regressions after cleanup

---

## Phase 2: Core Fix — Repository and ViewModel Save Paths (US1 — P1)

_The two lines that unconditionally strip time to midnight must be removed._

- [ ] T004 [US1] Fix ``TaskRepositoryImpl.updateTask`` in ``app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt``: remove ``dueDate = task.dueDate?.let { normaliseToMidnight(it) }`` from the ``task.copy(...)`` inside ``taskDao.updateTask(...)`` — pass ``dueDate`` through unchanged
- [ ] T005 [US1] Fix ``GlobalHistoryViewModel.saveEditTask`` in ``app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt``: remove ``dueDate = updated.dueDate?.let { normaliseToMidnight(it) }`` from the ``updated.copy(...)`` passed to ``repository.updateTask`` — pass ``dueDate`` through unchanged
- [ ] T006 [US1] Write end-to-end test ``HomeViewModelSavesExactDueDateTimeTest.kt`` (new file, ``src/test/``): seed a task with ``dueDate`` at H=15 M=30, call ``HomeViewModel.updateTask(task)`` with FakeTaskRepository, assert the repository received H=15 M=30 — NOT midnight
- [ ] T007 [US1] Write end-to-end test ``HistoryViewModelSavesExactDueDateTimeTest.kt`` (new file, ``src/test/``): seed a task with ``dueDate`` at H=22 M=45, call ``GlobalHistoryViewModel.saveEditTask(task)``, assert the repository stored H=22 M=45 — NOT midnight
- [ ] T008 [US1] Run unit tests: ``./gradlew :app:testDebugUnitTest`` — all pass including T006+T007; confirm ``EditTaskDialogDefaultTimeTest`` still passes

---

## Phase 3: History Screen — Add Time Pickers (US2 — P1)

_``TaskEditSheet`` must gain start-time and due-time pickers matching ``EditTaskDialog``._

- [ ] T009 [US2] Add ``showStartTimePicker`` and ``showDueTimePicker`` state variables to ``TaskEditSheet`` in ``app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt``
- [ ] T010 [US2] Update start-date button label format to ``"MMM d, HH:mm"`` (was ``"MMM d, yyyy"``); update start-date picker confirm to chain into start-time picker (set ``showStartTimePicker = true`` after closing date picker)
- [ ] T011 [US2] Add start-time picker dialog to ``TaskEditSheet``: on confirm, set ``startDateMs = mergeDateTime(startDateMs, [cal with H:M from picker])`` with ``SECOND=0``, ``MILLISECOND=0``
- [ ] T012 [US2] Update due-date button label format to ``"MMM d, HH:mm"``; update due-date picker confirm to chain into due-time picker
- [ ] T013 [US2] Add due-time picker dialog to ``TaskEditSheet``: on confirm, set ``dueDateMs = mergeDateTime(dueDateMs ?: defaultEndTime(), [cal with H:M])`` with ``SECOND=0``, ``MILLISECOND=0``
- [ ] T014 [US2] Run unit tests: ``./gradlew :app:testDebugUnitTest`` — no regressions
- [ ] T015 [US2] Write instrumented regression test ``taskEditSheet_timePicker_savesExactTime`` in ``GlobalHistoryScreenTest.kt``: simulate date confirm + time confirm selecting 22:45, assert ``onSave`` receives ``dueDate`` with H=22 M=45 (not midnight)
- [ ] T016 [US2] Run instrumented tests: ``./gradlew :app:connectedDebugAndroidTest --tests "com.flow.presentation.history.GlobalHistoryScreenTest"`` — all pass

---

## Phase 4: Task Card — Show Start Date/Time (US3 — P2)

- [ ] T017 [US3] In ``TaskItem`` composable in ``app/src/main/java/com/flow/presentation/home/HomeScreen.kt``, add ``"Start: MMM dd, HH:mm"`` label in the title column below the task title using ``SimpleDateFormat("MMM dd, HH:mm")`` and ``task.startDate``
- [ ] T018 [US3] Run build and unit tests: no regressions
- [ ] T019 [US3] Run full instrumented tests: ``./gradlew :app:connectedDebugAndroidTest`` — no regressions on task card display

---

## Final Phase: Full Suite Sign-Off

- [ ] T020 Run full unit test suite: ``./gradlew :app:testDebugUnitTest`` — zero failures required
- [ ] T021 Run full instrumented test suite: ``./gradlew :app:connectedDebugAndroidTest`` — zero failures required
- [ ] T022 Security and consistency check: (a) no credentials/PII in any tracked file, (b) ``.gitignore`` unchanged, (c) no ``normaliseToMidnight`` call on a field being *stored* (only on fields used for comparison/grouping), (d) no sensitive data in logs

---

## Dependency Graph

```
T001
 |
T002 -> T003
          |
    ┌─────┴──────────────────────────────────────┐
    |                                             |
  T004                                         (can start Phase 3
  T005                                          after T003 passes)
  T006                                           T009
  T007                                           T010
  T008 (gate)                                    T011
    |                                            T012
    |                                            T013
    |                                            T014
    |                                            T015
    |                                            T016 (gate)
    └──────────────────┬──────────────────────────┘
                       |
                     T017 -> T018 -> T019 (Phase 4)
                                         |
                                    T020 -> T021 -> T022
```

---

## Task Count Summary

| Phase | Tasks | Stories Covered |
|---|---|---|
| Phase 1: Cleanup | 3 (T001-T003) | — |
| Phase 2: Repository + ViewModel | 5 (T004-T008) | US1 |
| Phase 3: History Time Pickers | 8 (T009-T016) | US2 |
| Phase 4: Task Card | 3 (T017-T019) | US3 |
| Final: Sign-off | 3 (T020-T022) | Cross-cutting |
| **Total** | **22** | **US1, US2, US3** |
"@

Set-Content -Path "d:\Android\Flow\specs\005-fix-task-end-time\tasks.md" -Value $content
Write-Host "Lines: $((Get-Content 'd:\Android\Flow\specs\005-fix-task-end-time\tasks.md' | Measure-Object -Line).Lines)"
