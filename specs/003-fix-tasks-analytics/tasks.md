# Tasks: Fix Tasks & Analytics (003)

**Input**: Design documents from `specs/003-fix-tasks-analytics/`
**Branch**: `003-fix-tasks-analytics`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)
**Test baseline**: 63 unit / 45 instrumented — must remain green throughout

---

## Phase 1: Setup

**Purpose**: Add shared utility needed by multiple user stories before any story work begins.

- [X] T001 Create `app/src/main/java/com/flow/util/DateUtils.kt` with `utcDateToLocalMidnight(utcMidnight: Long): Long` (extract UTC calendar date components, rebuild local midnight; see `research.md` R-001 for full implementation)
- [X] T002 [P] Create `app/src/test/java/com/flow/util/DateUtilsTest.kt` with: `utcDateToLocalMidnight(feb22UtcMidnight)` returns Feb 22 local midnight (UTC+ zone); same input in UTC-N zone returns Feb 22 local midnight (not Feb 21); idempotency — already-local-midnight input returns itself unchanged

**Checkpoint**: `.\gradlew :app:test` — T002 tests pass; `DateUtils.kt` compiles cleanly.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Fix the data write path (FR-001) and DAO query (FR-003) before any UI story can be built on correct data.

**⚠️ CRITICAL**: US1, US2, US3 all depend on correct `dueDate` storage and correct query results. Complete this phase first.

- [X] T003 In `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt`, change `addTask()`: replace `dueDate = dueDate` with `dueDate = dueDate?.let { normaliseToMidnight(it) }` (FR-001)
- [X] T004 In the same file `TaskRepositoryImpl.kt`, apply the same normalisation to `updateTask()`: `dueDate = updatedTask.dueDate?.let { normaliseToMidnight(it) }` (FR-001 completeness)
- [X] T005 [P] In `app/src/main/java/com/flow/data/local/TaskDao.kt`, add the missing future-task clause to `getHomeScreenTasks` SQL query: `OR (dueDate IS NOT NULL AND dueDate >= :tomorrowStart AND status != 'COMPLETED')` immediately after the "today" clause (FR-003; see `contracts/schema-changes.md` C-001 for exact before/after SQL)
- [X] T006 [P] Write regression unit test in `app/src/test/java/com/flow/data/local/TodayProgressTest.kt`: add test `dueDate stored at 6pm is normalised to midnight and counted` — creates a task via `addTask()` with `dueDate = todayMidnight + 79_200_000` (10 pm), reads it back, asserts `dueDate == todayMidnight` (TR-001, red-then-green for FR-001)
- [X] T007 [P] Write regression instrumented test in `app/src/androidTest/java/com/flow/data/local/TaskDaoTest.kt`: add test `getHomeScreenTasks returns future-dated incomplete task` — insert task with `dueDate = tomorrowMidnight + 86_400_000`, call `getHomeScreenTasks(today, tomorrow)`, assert task is in result (TR-001/TR-002, red-then-green for FR-003)

**Checkpoint**: `.\gradlew :app:test` — T006 test passes (red before T003, green after). `.\gradlew :app:compileDebugAndroidTestKotlin` — compile gate passes.

---

## Phase 3: User Story 1 — Today Progress Bar Correct Count (P1)

**Goal**: `getTodayProgress()` returns accurate counts when tasks have `dueDate` = today.

**Independent Test**: Create two tasks with `dueDate = today`, complete one, observe top bar shows 50%.

### Implementation for User Story 1

- [X] T009 [P] [US1] Write instrumented test in `app/src/androidTest/java/com/flow/data/repository/RepositoryIntegrityTest.kt`: test `addTask_with_dueDate_today_then_getTodayProgress_counts_it` — insert task via repository `addTask()`, call `getTodayProgress()`, assert `totalToday = 1`; this round-trip also confirms `getTodayProgress()` calls `getTasksDueOn(normaliseToMidnight(now))` correctly (TR-002 lowest-layer verification; covers former standalone verify step)

**Checkpoint (US1)**: `.\gradlew :app:connectedDebugAndroidTest` — T009 passes. Progress bar shows correct count on device.

---

## Phase 4: User Story 2 — Target Date Saved as Correct Calendar Day (P1)

**Goal**: Date picker produces the local calendar day the user selected, not previous day in UTC-N zones.

**Independent Test**: Pick today in DatePickerDialog, save, open task — shown date matches selected day.

### Implementation for User Story 2

- [X] T010 [US2] In `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`, find the `AddTaskDialog` start-date confirm handler (line ≈619): replace `startDate = it` with `startDate = utcDateToLocalMidnight(it)` — import `com.flow.util.utcDateToLocalMidnight` (FR-002)
- [X] T011 [US2] In same file `HomeScreen.kt`, find the `AddTaskDialog` due-date confirm handler (line ≈666): replace `dueDate = it` with `dueDate = utcDateToLocalMidnight(it)` (FR-002 primary bug site)
- [X] T012 [US2] In same file `HomeScreen.kt`, find the `EditTaskDialog` start-date confirm handler (line ≈866): replace `startDate = it` with `startDate = utcDateToLocalMidnight(it)` (FR-002)
- [X] T013 [US2] In same file `HomeScreen.kt`, find the `EditTaskDialog` due-date confirm handler (line ≈913): replace `dueDate = it` with `dueDate = utcDateToLocalMidnight(it)` (FR-002)
- [X] T014 [P] [US2] Verify `DateUtilsTest.kt` (T002) covers UTC-N off-by-one scenario — no new test needed if T002 already covers it; otherwise add a test `picker_returns_feb22_utc_stored_as_feb22_local` in `app/src/test/java/com/flow/util/DateUtilsTest.kt`

**Checkpoint (US2)**: `.\gradlew :app:test` — all `DateUtilsTest` pass. Install on device, pick Feb 22 in AddTask dialog, confirm card shows Feb 22.

---

## Phase 5: User Story 3 — Future-Dated Tasks in Upcoming Section (P1)

**Goal**: Tasks with `dueDate > today` appear in a labelled "Upcoming" section on Home; not counted in today's progress.

**Independent Test**: Create task with `dueDate = tomorrow`. Navigate to Home — task appears under "Upcoming" header.

### Implementation for User Story 3

- [X] T015 [US3] In `app/src/main/java/com/flow/presentation/home/HomeUiState.kt`, add field `val upcomingTasks: List<Task> = emptyList()` alongside the existing `tasks` field — **do not rename** `tasks`; adding `upcomingTasks` as a purely additive field avoids cascading renames across `HomeScreen.kt` and `HomeViewModel.kt`
- [X] T016 [US3] In `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt`, update the `getHomeScreenTasks` collector: split the result into `todayTasks` (dueDate within today's window, undated, recurring, overdue) and `upcomingTasks` (dueDate >= tomorrowStart and status != COMPLETED); populate both fields in UiState
- [X] T017 [US3] In `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`, after the existing task list section, add a conditional "Upcoming" section: show a `Text("Upcoming", style = labelLarge, color = Color.Gray)` header followed by the `upcomingTasks` list in the same card style; only render the section when `upcomingTasks` is non-empty (FR-003 UI)
- [X] T018 [P] [US3] Write instrumented test in `app/src/androidTest/java/com/flow/data/local/TaskDaoTest.kt` (extends T007): add test `future_task_not_counted_in_todayProgress` — insert task with `dueDate = tomorrowMidnight`, call `getTasksDueOn(todayMidnight)`, assert result is empty (TR-002 — FR-004 today count excludes future tasks)

**Checkpoint (US3)**: `.\gradlew :app:connectedDebugAndroidTest` — T018 passes. Install on device, create task for next week — appears in Upcoming, not in progress bar denominator.

---

## Phase 6: User Story 4 — Full Task Editing from History (P2)

**Goal**: Long-press in History opens correct edit surface for both recurring and non-recurring tasks.

**Independent Test**: Recurring long-press → action sheet with two options. Non-recurring long-press → `TaskEditSheet` opens directly.

### Implementation for User Story 4

- [X] T019 [US4] In `app/src/main/java/com/flow/presentation/history/GlobalHistoryUiState.kt`, add three new fields: `val editingTask: TaskEntity? = null`, `val showActionSheet: Boolean = false`, `val actionSheetTarget: HistoryItem? = null` (FR-005; see `data-model.md`)
- [X] T020 [US4] In `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt`, add five new public functions: `openEditTask(taskId: Long)`, `dismissEditTask()`, `saveEditTask(updated: TaskEntity)`, `openActionSheet(item: HistoryItem)`, `dismissActionSheet()` — **`openEditTask`**: call `repository.getTaskById(taskId)`; if result is null set `uiState.error = "Task not found"` and return without opening the sheet (null-guard for deleted tasks); **`saveEditTask`**: (a) if `updated.status != TaskStatus.COMPLETED` pass `completionTimestamp = null` in the copy so the task is removed from `getCompletedNonRecurringTasks()`; (b) call `repository.updateTaskStatus(updated.id, updated.status)` to trigger the side-effect that clears/sets `completionTimestamp`, then `repository.updateTask(updated.copy(dueDate = updated.dueDate?.let { normaliseToMidnight(it) }))` (FR-005/FR-006; see `contracts/schema-changes.md` C-004)
- [X] T021 [US4] In `app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt`, create new `TaskEditSheet` composable: `ModalBottomSheet` with `OutlinedTextField` for title, date-only picker for startDate (using `utcDateToLocalMidnight`), date-only picker for dueDate (nullable), and a `FilterChip` row for status (PENDING / COMPLETED); Save button calls `onSave(updatedTask)` (FR-005; see `contracts/schema-changes.md` C-005)
- [X] T022 [US4] In `GlobalHistoryScreen.kt`, update the `onLongClick` handler: replace the current `item.logId?.let { … }` with two-branch logic — if `item.isRecurring` call `viewModel.openActionSheet(item)`, else call `viewModel.openEditTask(item.taskId)` (FR-005)
- [X] T023 [US4] In `GlobalHistoryScreen.kt`, add rendering of the action bottom sheet: when `uiState.showActionSheet == true` show a `ModalBottomSheet` with two `ListItem` rows: "Edit this day" (calls `viewModel.openEditLog(log)` and `viewModel.dismissActionSheet()`) and "Edit task" (calls `viewModel.openEditTask(item.taskId)` and `viewModel.dismissActionSheet()`) (FR-005)
- [X] T024 [US4] In `GlobalHistoryScreen.kt`, add rendering of `TaskEditSheet`: when `uiState.editingTask != null` show `TaskEditSheet(task, onSave = { viewModel.saveEditTask(it) }, onDismiss = { viewModel.dismissEditTask() })` (FR-006)
- [X] T025 [P] [US4] Write unit tests in `app/src/test/java/com/flow/presentation/history/GlobalHistoryViewModelTest.kt`: (a) `openActionSheet_sets_showActionSheet_true`; (b) `openEditTask_fetches_task_by_id`; (c) `saveEditTask_calls_repository_updateTask`; (d) `dismissed_task_not_in_editingTask`; (e) `saveEditTask_withStatusTodo_clearsCompletionTimestamp` — assert task `completionTimestamp` is null after `saveEditTask(task.copy(status = TaskStatus.TODO))`; (f) `openEditTask_withDeletedTask_setsErrorNotSheet` — assert null return from `getTaskById` sets `uiState.error` and leaves `editingTask` null (TR-001/TR-003)

**Checkpoint (US4)**: `.\gradlew :app:test` — T025 passes. Install on device, long-press recurring history row — sheet shows two options; long-press non-recurring — edit sheet opens directly.

---

## Phase 7: User Story 5 — Analytics Tab/Pager with Tree Forest (P3)

**Goal**: Analytics redesigned as 4-section tab/pager; Forest cells show 🌲 icons; active tab is neon-green.

**Independent Test**: Open Analytics → section switcher row visible with 4 tabs → tap "Forest" → only Forest content visible → cells show 🌲 not squares.

### Implementation for User Story 5

- [X] T027 [US5] In `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt`, add `PillTabRow` composable helper: a `Row` of 4 `FilterChip` items (labels: "Graph", "Lifetime", "This Year", "Forest"); selected chip uses `containerColor = NeonGreen, labelColor = Color.Black`; unselected uses transparent background with `NeonGreen` border; synced to `PagerState` via `coroutineScope.launch { pager.animateScrollToPage(index) }` (FR-007/FR-009)
- [X] T028 [US5] In `AnalyticsScreen.kt`, replace the top-level `LazyColumn` with: `val pagerState = rememberPagerState(pageCount = { 4 })` + `Column { PillTabRow(pagerState); HorizontalPager(state = pagerState) { page -> … } }` — move existing section composables into the four page slots (page 0 = Graph/heatmap, page 1 = Lifetime, page 2 = This Year, page 3 = Forest) (FR-007)
- [X] T029 [US5] In `AnalyticsScreen.kt`, update `ForestHeatmap` composable: replace `Box(modifier = Modifier.size(12.dp).background(color))` cells with `Text(text = if (count == 0) "·" else "🌲".repeat(count.coerceAtMost(4)), style = MaterialTheme.typography.labelSmall)` where `count = forestData[dayMidnight]?.size ?: 0`; set `color = Color.Gray` for empty cells (FR-008)
- [X] T030 [US5] In `AnalyticsScreen.kt`, inside `ForestSection`, add `Text("Best streak: ${uiState.bestStreak} days", color = NeonGreen, style = MaterialTheme.typography.bodySmall)` above the heatmap grid — data already in `AnalyticsUiState.bestStreak` (FR-009)
- [X] T031 [P] [US5] Write instrumented tests in `app/src/androidTest/java/com/flow/presentation/analytics/AnalyticsPeriodSelectorTest.kt`: (a) `analytics_shows_four_tabs` — launch `AnalyticsScreen`, assert nodes with text "Graph", "Lifetime", "This Year", "Forest" are all displayed; (b) `analytics_each_tab_shows_only_its_content` — seed one `TaskCompletionLog` entry, swipe pager to each of the 4 pages in turn, assert at least one content node is visible on each page and no other page's unique headline is simultaneously shown (TR-001/constitution Gate 5 — replaces manual check)

**Checkpoint (US5)**: `.\gradlew :app:connectedDebugAndroidTest` — T031 passes. Install on device, confirm tab navigation and 🌲 cells in Forest.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, regression sweep, consistency and security checks.

- [X] T032 Run `.\gradlew :app:test` — assert all 63+ unit tests pass (includes T002, T006, T014/T025 new tests)
- [X] T033 Run `.\gradlew :app:connectedDebugAndroidTest` on SM-S936U (serial R5CY305LTGB) — assert all 45+ instrumented tests pass (includes T007, T009, T018, T026, T031 new tests)
- [X] T034 [P] Consistency check: verify all new DAO queries in `TaskDao.kt` use Room `:param` syntax (no string concatenation); verify no `@Suppress` annotations added; verify all new colours use existing `NeonGreen`/`SurfaceDark` constants (SE-003, CO-001)
- [X] T035 [P] Security check: confirm `build.gradle.kts` has no new dependencies added; confirm `DateUtils.kt` has no internet permissions or PII logging; confirm `TaskEditSheet` does not log task titles (SE-001/SE-002/SE-004)
- [X] T036 [P] Confirm T032 and T033 automated suites cover all AL-001 non-regression flows (recurring streaks, undated tasks, history rendering, analytics per-tab rendering); if any critical user journey still lacks automated coverage at this point, add a targeted test before marking the feature complete — no manual-only verification (constitution Gate 5)
- [X] T037 Count new tests added: confirm at least 4 red-then-green regression tests exist covering FR-001 through FR-004 (one each: T006 × FR-001, T002 × FR-002, T007 × FR-003, T018 × FR-004) — satisfies TR-001/TR-003 (SC-9)

---

## Dependencies

```
T001 ──► T010, T011, T012, T013 (DateUtils.kt must exist before picker fixes)
T003 ──► T006 (write fix must exist before regression test is meaningful)
T005 ──► T007 (query fix must exist before instrumented test passes)
T003, T005 ──► T008, T009, T015, T016 (foundational data fixes unblock US1, US3)
T015, T016 ──► T017 (UiState / VM split unblocks UI rendering)
T019, T020 ──► T021, T022, T023, T024 (UiState + VM additions unblock screen changes)
T027 ──► T028, T029, T030 (PillTabRow helper must exist before pager assembly)
T028 ──► T029, T030 (pager structure must exist before per-page content changes)
```

**Parallel opportunities within each story** (tasks marked [P]):
- US1: T009 can run after T003, T005 independently of US2/US3
- US2: T010, T011, T012, T013 can all proceed in parallel (different blocks in same file)
- US3: T015 + T016 must sequence, then T017 + T018 can run in parallel
- US4: T019 then T020 then T021–T024 sequence; T025 + T026 parallel with T021+
- US5: T027 then T028 then T029 + T030 + T031 in parallel

---

## Implementation Strategy

**MVP scope (deliver first)**: Phase 1 (T001–T002) + Phase 2 (T003–T007) + Phase 3 (T008–T009)
— fixes the most visible and trust-damaging bug (progress bar always 0%) and lays the
correct data foundation for all remaining stories. Shippable as a hotfix.

**Increment 2**: Phase 4 (T010–T014) — FR-002 date picker fix; low risk, touches only UI layer.

**Increment 3**: Phase 5 (T015–T018) — FR-003 Upcoming section; depends on correct DAO query (T005 already done in Phase 2).

**Increment 4**: Phase 6 (T019–T026) — FR-005/006 history editing; largest surface area, P2 priority.

**Increment 5**: Phase 7 (T027–T031) + Phase 8 (T032–T037) — FR-007/008/009 analytics redesign + full regression sweep.

---

## Summary

| Metric | Value |
|--------|-------|
| Total tasks | 35 |
| Phase 1 (Setup) | 2 |
| Phase 2 (Foundational) | 5 |
| Phase 3 (US1 — Progress) | 1 |
| Phase 4 (US2 — Date picker) | 5 |
| Phase 5 (US3 — Upcoming) | 4 |
| Phase 6 (US4 — History edit) | 5 |
| Phase 7 (US5 — Analytics) | 5 |
| Phase 8 (Polish) | 6 |
| Parallelisable [P] tasks | 18 |
| New regression tests (red-then-green) | 4 (T002, T006, T007, T018) |
| New unit tests | 7 (T002, T006, T014, T025a–f) |
| New instrumented tests | 5 (T007, T009, T018, T031a, T031b) |
| MVP scope | T001–T009 (Phases 1–3) |
