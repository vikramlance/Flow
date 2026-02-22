# Tasks: Progress Gamification & Analytics Enhancement

**Input**: `specs/002-progress-gamification/` — plan.md, spec.md, research.md, data-model.md, contracts/internal-contracts.md, quickstart.md  
**Branch**: `002-progress-gamification`  
**DB version**: 5 → 6

---

## Phase 1: Setup

**Purpose**: DB migration infrastructure and pure-Kotlin domain layer — required before any user story can run.

- [X] T001 Add `scheduleMask: Int? = null` field to `TaskEntity` in `app/src/main/java/com/flow/data/local/TaskEntity.kt`
- [X] T002 [P] Create `DayMask` object in `app/src/main/java/com/flow/domain/streak/DayMask.kt` (bit helpers: `fromDayOfWeek()`, `isScheduled()`, constants MON–SUN)
- [X] T003 [P] Create `StreakResult` data class in `app/src/main/java/com/flow/domain/streak/StreakResult.kt` (`currentStreak`, `longestStreak`, `longestStreakStartDate: Long?`; invariant guard `longestStreak >= currentStreak`)
- [X] T004 Create `TaskStreakEntity` in `app/src/main/java/com/flow/data/local/TaskStreakEntity.kt` (`@Entity(tableName="task_streaks")`, `taskId` PK, `currentStreak`, `longestStreak`, `longestStreakStartDate`, `lastUpdated`)
- [X] T005 [P] Create `AchievementType` enum in `app/src/main/java/com/flow/data/local/AchievementType.kt` (values: `STREAK_10`, `STREAK_30`, `STREAK_100`, `ON_TIME_10`, `EARLY_FINISH`, `YEAR_FINISHER`)
- [X] T006 [P] Create `AchievementEntity` in `app/src/main/java/com/flow/data/local/AchievementEntity.kt` (`@Entity(tableName="achievements")`, `id` PK autoGenerate, `type: AchievementType`, `taskId: Long?`, `earnedAt: Long`, `periodLabel: String?`)
- [X] T007 Create `TaskStreakDao` in `app/src/main/java/com/flow/data/local/TaskStreakDao.kt` (`upsertStreak()`, `getStreakForTask(taskId): Flow<TaskStreakEntity?>`, `deleteByTaskId(taskId)`)
- [X] T008 [P] Create `AchievementDao` in `app/src/main/java/com/flow/data/local/AchievementDao.kt` (`insertOnConflictIgnore()`, `getAll(): Flow<List<AchievementEntity>>`, `getByTask(taskId)`)
- [X] T009 Write `MIGRATION_5_6` and bump `AppDatabase` to version 6 in `app/src/main/java/com/flow/data/local/AppDatabase.kt` (3 DDL statements: `ALTER TABLE tasks ADD COLUMN scheduleMask INTEGER DEFAULT NULL`; `CREATE TABLE IF NOT EXISTS task_streaks`; `CREATE TABLE IF NOT EXISTS achievements` + unique index)
- [X] T010 Register `TaskStreakEntity`, `AchievementEntity`, `TaskStreakDao`, `AchievementDao` in `AppDatabase` and bind DAOs in `app/src/main/java/com/flow/di/AppModule.kt`

**Checkpoint**: App compiles and DB migrates cleanly from v5 to v6 — `.\gradlew assembleDebug` must pass.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: `StreakCalculator`, extended repository interface, and `AnalyticsPeriod` type — required by every user story.

**⚠️ No user story work can begin until this phase is complete.**

- [X] T011 Create `StreakCalculator` object in `app/src/main/java/com/flow/domain/streak/StreakCalculator.kt` — `compute(completionDates: List<Long>, scheduleMask: Int?, today: LocalDate): StreakResult`; walk-backwards current streak (today grace period); walk-forwards longest streak; skip non-scheduled days; null mask = DAILY
- [X] T012 Add `updateLog(log: TaskCompletionLog)` and `getRecurringLogsBetween(startMs: Long, endMs: Long): Flow<List<RecurringLogEntry>>` to `TaskCompletionLogDao` in `app/src/main/java/com/flow/data/local/TaskCompletionLogDao.kt`; add `RecurringLogEntry` projection DTO (completionDate, taskTitle)
- [X] T013 Create `AnalyticsPeriod` sealed class in `app/src/main/java/com/flow/presentation/analytics/AnalyticsPeriod.kt` (`CurrentYear`, `Last12Months`, `SpecificYear(year: Int)`, `Lifetime`); add `toDateRange(earliestLogMs: Long): Pair<Long, Long>` extension
- [X] T014 Add 9 new method signatures to `TaskRepository` interface in `app/src/main/java/com/flow/data/repository/TaskRepository.kt`: `getTodayProgress()`, `getHeatMapData(startMs: Long, endMs: Long)` (**replaces the existing parameterless `getHeatMapData()` — remove the old signature**), `getForestData(startMs: Long, endMs: Long)`, `getStreakForTask(taskId)`, `getAchievements()`, `getLifetimeStats()`, `getCurrentYearStats()`, `updateLog()`, `recalculateStreaks(taskId)`, `checkAndAwardAchievements(taskId?)`; also add `TodayProgressState`, `LifetimeStats`, `CurrentYearStats` data classes in the same or companion files
- [X] T015 Implement new repository methods in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt`: `getTodayProgress()` (filter by `dueDate = todayMidnight`); `getHeatMapData(startMs, endMs)` (query completions between the given epoch timestamps — ViewModel has already converted `AnalyticsPeriod` via `period.toDateRange(earliestLogMs)` before calling); `getForestData(startMs, endMs)` (use `getRecurringLogsBetween(startMs, endMs)`, group by midnight date); `recalculateStreaks(taskId)` (fetch logs → `StreakCalculator.compute()` → upsert `TaskStreakEntity`); `checkAndAwardAchievements(taskId?)` (check thresholds, insert with `CONFLICT IGNORE`); `getStreakForTask`, `getAchievements`, `getLifetimeStats`, `getCurrentYearStats`
- [X] T016 Extend `refreshRecurringTasks()` in `TaskRepositoryImpl` to use `scheduleMask`: compute `todayBit`, skip tasks where `(scheduleMask & todayBit) == 0` AND `scheduleMask IS NOT NULL`, set both `startDate` and `dueDate` to `todayMidnight` for scheduled tasks; existing `scheduleMask = null` tasks default to DAILY (current behaviour preserved)

**Checkpoint**: `.\gradlew testDebugUnitTest` — StreakCalculator tests (T011) must pass independently with no device.

---

## Phase 3: User Story 1 — Today-Focused Progress Indicator (P1) 🎯 MVP

**Goal**: Home screen shows correct daily percentage (only `dueDate = today` tasks) with "Today's progress" label, yellow/green colour, and a neutral empty state when no tasks are due today.

**Independent Test**: Create 2 tasks due today (1 completed) + 3 tasks due next week → indicator must show 50% with a "today" label, not 20%.

- [X] T017 [P] [US1] Write `StreakCalculatorTest` in `app/src/test/java/com/flow/domain/streak/StreakCalculatorTest.kt` covering: DAILY schedule all-complete streak, DAILY with gap resets, MWF schedule gap days skipped, null mask = DAILY fallback, start-date boundary, today grace period on scheduled day (~25 test cases)
- [X] T017b [P] [US1] Write `TodayProgressTest` in `app/src/test/java/com/flow/data/local/TodayProgressTest.kt` covering: 0 tasks today → `hasAnyTodayTasks = false` / empty state; `dueDate = todayMidnight` counted; `dueDate = tomorrowMidnight` excluded; recurring task with `dueDate = todayMidnight` counted; midnight boundary (±1 ms); `completedToday <= totalToday` invariant (~8 test cases)
- [X] T018 [P] [US1] Write `UrgencyLevelTest` in `app/src/test/java/com/flow/presentation/home/UrgencyLevelTest.kt` covering: 15%→GREEN, 50%→YELLOW, 85%→ORANGE, completed→NONE, no dueDate→NONE, overdue→NONE, startDate==dueDate→ORANGE (~12 test cases)
- [X] T019 [US1] Add `TodayProgressState` to `HomeUiState` and add `todayProgress: TodayProgressState` field in `app/src/main/java/com/flow/presentation/home/HomeUiState.kt`
- [X] T020 [US1] Subscribe to `repository.getTodayProgress()` in `HomeViewModel` and expose it through `HomeUiState` in `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt`
- [X] T021 [US1] Update the progress ring/bar composable in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`: replace current label with "Today's progress"; colour yellow when `ratio < 1.0f`, green when `ratio == 1.0f`; show neutral empty state card ("No tasks for today") when `hasAnyTodayTasks == false`; hide 0%/100% when empty

**Checkpoint**: Open Home with mixed tasks — indicator shows 50% with "Today's progress" label; no future tasks inflate or deflate the count.

---

## Phase 4: User Story 2 — Urgency Colour Coding (P1)

**Goal**: Future-dated task cards with both `startDate` and `dueDate` show green/yellow/orange based on elapsed %.

**Independent Test**: Task with 50% elapsed → yellow card; task with 90% elapsed → orange card; completed task → default colour.

- [X] T022 [US2] Add `UrgencyLevel` enum (`NONE`, `GREEN`, `YELLOW`, `ORANGE`) and `TaskEntity.urgencyLevel(today: Long): UrgencyLevel` extension function in `app/src/main/java/com/flow/presentation/home/HomeUiState.kt` (or a new `UrgencyLevel.kt` in the `home` package)
- [X] T023 [US2] Derive `urgencyLevel` per task in `HomeViewModel` (no repository call needed — computed from `TaskEntity` fields) and include it in the `HomeUiState` task list; expose as `homeTasks: List<HomeTaskItem>` where `HomeTaskItem(task: TaskEntity, urgency: UrgencyLevel)` is a data class defined in `HomeUiState.kt`; in `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt`
- [X] T024 [US2] Apply urgency card background/border tint in the task card composable in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`: `GREEN` → tinted green border; `YELLOW` → tinted yellow; `ORANGE` → tinted orange; `NONE` → current default; completed and overdue cards unaffected

**Checkpoint**: Create tasks at 15%, 50%, 85% elapsed — cards render green, yellow, orange respectively; completed task reverts to default.

---

## Phase 5: User Story 3 — Heatmap Improvements & Multi-Period Analytics (P2)

**Goal**: Analytics heatmap defaults to current calendar year, month labels fully visible, four period options work correctly, lifetime + current-year stats shown.

**Independent Test**: Open Analytics → labels fully visible; default = current year; switching periods updates the heatmap date range correctly.

- [X] T025 [P] [US3] Add `selectedPeriod: AnalyticsPeriod`, `heatmapData: Map<Long, Int>`, `lifetimeStats: LifetimeStats?`, `currentYearStats: CurrentYearStats?`, `availableYears: List<Int>`, `isLoading: Boolean` to `AnalyticsUiState` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsUiState.kt`
- [X] T026 [US3] Subscribe to `repository.getHeatMapData(startMs, endMs)` (converting `selectedPeriod` via `period.toDateRange(earliestLogMs)` in the ViewModel) and `repository.getLifetimeStats()` / `repository.getCurrentYearStats()` in `AnalyticsViewModel`; expose `onPeriodSelected(period)` event; populate `availableYears` from earliest log year to current year; default period = `AnalyticsPeriod.CurrentYear` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt`
- [X] T027 [US3] Add `PeriodSelectorRow` composable to `AnalyticsScreen.kt` using horizontally scrollable `FilterChip` row (3 fixed chips + 1 year chip with `ArrowDropDown` icon that opens a compact `YearPickerDialog`); year chip label self-updates to selected year in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt`
- [X] T028 [US3] Fix month label clipping in the existing `ContributionHeatmap` composable — ensure each label has sufficient width (`wrapContentWidth()` or `widthIn(min=)`) and the horizontal scroll region matches the cell grid width in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt`
- [X] T029 [US3] Add lifetime and current-year stats summary cards below the heatmap in `AnalyticsScreen.kt` (total completed, on-time rate, longest streak, unique habits count; current-year panel scoped to 1 Jan – 31 Dec)

**Checkpoint**: Open Analytics → all month labels visible; default = current year; "Last 12 months", specific year, and Lifetime all update the heatmap and stats panels.

---

## Phase 6: User Story 4 — Gamification: Streaks, Awards & Achievements (P2)

**Goal**: Per-task streak tracking; 6 achievement badges earned at milestones and displayed in an Achievements section on Analytics.

**Independent Test**: Complete a recurring task for 10 consecutive scheduled days → "Budding Habit" badge appears in Achievements; break streak → current streak resets to 0, badge persists.

- [X] T030 [US4] Add `streaks: List<TaskStreakEntity>`, `achievements: List<AchievementEntity>` to `AnalyticsUiState` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsUiState.kt` (**must be sequenced after T025 — both modify the same file**)
- [X] T031 [US4] Trigger `recalculateStreaks(taskId)` and `checkAndAwardAchievements(taskId)` inside `TaskRepositoryImpl.updateTaskStatus()` when marking a task COMPLETED, and after any `updateLog()` call; also call `checkAndAwardAchievements(null)` after every COMPLETED status change to evaluate global (non-task-specific) thresholds (`ON_TIME_10`, `YEAR_FINISHER`) in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt`
- [X] T032 [US4] Subscribe to `repository.getStreakForTask()` (for all recurring tasks) and `repository.getAchievements()` in `AnalyticsViewModel`; combine with existing heatmap flow in `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt`
- [X] T033 [US4] Add `AchievementsSection` composable to `AnalyticsScreen.kt`: grid/list of `AchievementBadge` cards (emoji + name + earn date + task name if applicable); group by type; earned badges always visible; use `LazyVerticalGrid` inside a non-scrollable container within the screen's `LazyColumn` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt`

**Checkpoint**: Complete a recurring task 10 days in a row on scheduled days → Achievements section shows "Budding Habit" badge; miss a day → `currentStreak` resets on next scheduled day; badge still visible.

---

## Phase 7: User Story 6 — Task History Editing (P2)

*Implemented before US5 (Forest) because US5 depends on correct completion log data.*

**Goal**: Users can edit `startDate`, `dueDate`, `completionDate`, and `status` on any History entry; saving recalculates streaks and updates heatmap.

**Independent Test**: Edit completion date → History shows new date; change status to TODO → task re-appears on Home screen.

- [X] T034 [P] [US6] Add `editingLog: TaskCompletionLog?` and `editError: String?` to `GlobalHistoryViewModel` UI state; add `updateLogEntry(log: TaskCompletionLog)` and `clearEditError()` event handlers in `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt`
- [X] T035 [US6] Implement `updateLogEntry()` in `GlobalHistoryViewModel`: first call `repository.getTaskById(log.taskId)` to load the `TaskEntity` (emit `editError` and return if entity is not found); validate `completionDate <= today` and `completionDate >= task.startDate` (emit `editError` if invalid); call `repository.updateLog(log)`; call `repository.recalculateStreaks(log.taskId)`; if status → TODO/IN_PROGRESS also call `repository.updateTask()` to reset `TaskEntity.status` so it re-appears in `getHomeScreenTasks()` in `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt`
- [X] T036 [US6] Add `HistoryEditDialog` composable to `app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt`: date pickers for start date, target date, completion date (completion date picker disabled for future dates); status dropdown (TODO / IN_PROGRESS / COMPLETED); error message display; Save and Cancel buttons; open via long-press or edit icon on a history row

**Checkpoint**: Long-press a history entry → edit dialog opens; change completion date → History list updates; error shown when completion date is in future; change status to TODO → task card re-appears on Home screen without restart.

---

## Phase 8: User Story 5 — Forest Concept (P3)

**Goal**: Forest section within Analytics shows tree count and tree-density heatmap for recurring-task completions, using the same period selector.

**Independent Test**: Complete 3 recurring tasks on the same day → tree count increments by 3; switching periods updates count and cell densities.

- [X] T037 [US5] Add `forestData: Map<Long, List<String>>`, `forestTreeCount: Int` to `AnalyticsUiState` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsUiState.kt` (**must be sequenced after T030 — both modify the same file**)
- [X] T038 [US5] Subscribe to `repository.getForestData(startMs, endMs)` (converting `selectedPeriod` via `period.toDateRange(earliestLogMs)`) in `AnalyticsViewModel`; compute `forestTreeCount = forestData.values.sumOf { it.size }`; both `forestData` and `heatmapData` must derive from the same `selectedPeriod` in a single `combine` call in `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt`
- [X] T039 [US5] Create `ForestCell` composable and `ForestSection` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt`: reuse existing week-column horizontal-scroll grid structure; `ForestCell` renders 4 density tiers (0=`#2A2A2A`, 1–2=green@40%, 3–5=green@75%+🌿, 6+=`#1B5E20`+🌳); tree count banner ("Your forest: N trees"); tap on day cell opens `ModalBottomSheet` listing recurring task titles completed that day

**Checkpoint**: Complete three recurring tasks → tree count = 3; heatmap cell darkens; switching to "Last 12 Months" updates count and cells.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Non-regression validation, add-task dialog update for schedule picker, and constitution gates.

- [X] T040 Add `scheduleMask` picker to the task creation / edit dialog in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`: only shows when `isRecurring = true`; offers "Every day" (null) and weekday multi-select checkboxes (Mon–Sun); encodes selection to bitmask before calling `viewModel.addTask()` / `viewModel.updateTask()` (FR-019)
- [X] T041 [P] Update `addTask()` signature in `HomeViewModel` and `TaskRepository` to accept `scheduleMask: Int? = null` in `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt` and `app/src/main/java/com/flow/data/repository/TaskRepository.kt`; pass through to `TaskRepositoryImpl.addTask()` and `taskDao.insertTask()`
- [X] T042 [P] Write `RepositoryIntegrityTest` in `app/src/androidTest/java/com/flow/data/repository/RepositoryIntegrityTest.kt`: after `updateLog()` + `recalculateStreaks()`, assert `getCompletedTaskCount()` matches `isCompleted=true` log rows; assert `TaskStreakEntity.longestStreak >= currentStreak`
- [X] T043 [P] Write `HistoryEditDialogTest` in `app/src/androidTest/java/com/flow/presentation/history/HistoryEditDialogTest.kt`: (a) date edit persists; (b) future completion date blocked with error; (c) status→TODO causes task to appear in home task list via `getHomeScreenTasks()`
- [X] T044 [P] Write `AnalyticsPeriodSelectorTest` in `app/src/androidTest/java/com/flow/presentation/analytics/AnalyticsPeriodSelectorTest.kt`: chip selection updates `selectedPeriod`; heatmap data reloads; year picker resolves correct January 1st boundary
- [X] T045 Run full non-regression suite: `.\gradlew testDebugUnitTest` and `.\gradlew connectedDebugAndroidTest`; confirm all feature-001 tests still pass alongside new tests; confirm BUILD SUCCESSFUL
- [X] T046 Security gate check: grep all new/modified `.kt` files for hardcoded dates, task titles, or personal info in log statements; confirm all new `@Query` use named parameters; confirm `TaskEntity.scheduleMask` validation rejects values outside `1..127` and `null`
- [ ] T047 Install on device: `.\gradlew installDebug`; manually verify quickstart.md test recipes — urgency colours at 15%/50%/85%, "Budding Habit" badge at day-10, history edit round-trip, Forest tree count increment

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Requires Phase 1 — **BLOCKS all user stories**
- **Phase 3 (US1)**: Requires Phase 2
- **Phase 4 (US2)**: Requires Phase 2 (independent of US1)
- **Phase 5 (US3)**: Requires Phase 2 (independent of US1/US2)
- **Phase 6 (US4)**: Requires Phase 2 + Phase 5 (streak display on Analytics)
- **Phase 7 (US6)**: Requires Phase 2 + Phase 6 (history edit triggers streak recalc)
- **Phase 8 (US5)**: Requires Phase 2 + Phase 7 (Forest reads correct completion logs)
- **Phase 9 (Polish)**: Requires Phases 3–8

### User Story Dependencies

```
Phase 2 ──┬──> Phase 3 (US1) ─────────────────────────────────┐
          ├──> Phase 4 (US2) ─────────────────────────────────┤
          ├──> Phase 5 (US3) ──> Phase 6 (US4) ──> Phase 7 (US6) ──> Phase 8 (US5)
          └──────────────────────────────────────────────────────> Phase 9
```

### Parallel Opportunities Within Each Phase

| Phase | Parallel group |
|-------|---------------|
| 1 | T002, T003 (domain layer, no entity deps) |
| 1 | T005, T006 (enum + entity after T004) |
| 1 | T007, T008 (DAOs, no cross-dependency) |
| 2 | T011, T012, T013 (calculator, DAO extension, sealed class — different files) |
| 3 | T017, T018 (unit tests written before implementation) |
| 5 | T025 only — T030 and T037 **must NOT run in parallel** with T025 or each other; all three modify `AnalyticsUiState.kt` and must be sequenced T025 → T030 → T037 |
| 9 | T040, T041, T042, T043, T044 (different files, no deps on each other) |

---

## Implementation Strategy

### MVP Scope (Phase 1 + Phase 2 + Phase 3)

1. Complete Phase 1 (DB migration + domain types)
2. Complete Phase 2 (calculator + repository + refresh)
3. Complete Phase 3 (US1: today progress ring)
4. **STOP and validate**: `.\gradlew assembleDebug` passes; Home screen shows correct percentage
5. Deploy/demo MVP

### Incremental Delivery

| Step | Phases | What users see |
|------|--------|---------------|
| MVP | 1–3 | Fixed progress ring with "Today's progress" |
| +Urgency | +4 | Colour-coded future task cards |
| +Analytics | +5 | Period-selector heatmap, fixed labels, stats panels |
| +Streaks | +6 | Achievements badges, per-task streak display |
| +Editing | +7 | History date/status editing |
| +Forest | +8 | Tree heatmap section |
| +Polish | +9 | Schedule picker, all tests green, device validated |

---

## Task Count Summary

| Phase | Tasks | User Story |
|-------|-------|-----------|
| Phase 1: Setup | T001–T010 (10) | — |
| Phase 2: Foundational | T011–T016 (6) | — |
| Phase 3 | T017–T021 + T017b (6) | US1 (P1) |
| Phase 4 | T022–T024 (3) | US2 (P1) |
| Phase 5 | T025–T029 (5) | US3 (P2) |
| Phase 6 | T030–T033 (4) | US4 (P2) |
| Phase 7 | T034–T036 (3) | US6 (P2) |
| Phase 8 | T037–T039 (3) | US5 (P3) |
| Phase 9: Polish | T040–T047 (8) | — |
| **Total** | **48 tasks** | **6 user stories** |
