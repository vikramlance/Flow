# Tasks: Flow — Productivity & Task Management App

**Branch**: `001-app-analysis` | **Generated**: 2026-02-20
**Input**: `spec.md` (7 US, 28 FR), `plan.md`, `data-model.md`, `contracts/internal-contracts.md`
**Tests**: Not requested — constitution gates only (AL-002, DI-001, DI-002, CO-001)

---

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (operates on a different file / no incomplete dependency)
- **[US#]**: User Story this task belongs to (maps to spec.md)
- No story label = Setup or Foundational phase

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create all new files and wire all new dependencies so that user-story phases can proceed without structural blockers. No business logic here.

- [X] T001 Create typed route constants in `app/src/main/java/com/flow/navigation/Routes.kt` (HOME, ANALYTICS, HISTORY, SETTINGS, TASK_STREAK)
- [X] T002 Create `AppNavGraph.kt` in `app/src/main/java/com/flow/navigation/AppNavGraph.kt` wiring NavHost with all routes from Routes.kt and replacing any inline NavHost in MainActivity.kt
- [X] T003 [P] Create `HomeUiState.kt` data class in `app/src/main/java/com/flow/presentation/home/HomeUiState.kt` (tasks, todayProgress: Float, isFirstLaunch, isLoading)
- [X] T004 [P] Create `AnalyticsUiState.kt` data class in `app/src/main/java/com/flow/presentation/analytics/AnalyticsUiState.kt` (heatMapData, totalCompleted, completedOnTime, missedDeadlines, currentStreak, bestStreak, isLoading)
- [X] T005 [P] Create `TimerUiState.kt` data class in `app/src/main/java/com/flow/presentation/timer/TimerUiState.kt` (durationSeconds, remainingSeconds, isRunning, isPaused, isFinished)
- [X] T005a [P] Create `SettingsUiState.kt` data class in `app/src/main/java/com/flow/presentation/settings/SettingsUiState.kt` (defaultTimerMinutes: Int = 25, isLoading: Boolean = false, error: String? = null)
- [X] T006 [P] Create `SettingsRepository` interface in `app/src/main/java/com/flow/data/repository/SettingsRepository.kt` (isFirstLaunch, hasSeenTutorial, defaultTimerMinutes flows + suspend setters per Contract 2)
- [X] T007 [P] Create `SettingsRepositoryImpl.kt` in `app/src/main/java/com/flow/data/repository/SettingsRepositoryImpl.kt` wrapping existing `SettingsManager`
- [X] T008 Update `RepositoryModule.kt` in `app/src/main/java/com/flow/di/RepositoryModule.kt` to add `@Binds` for `SettingsRepository → SettingsRepositoryImpl`

**Checkpoint**: All new skeleton files exist; project compiles (no new business logic yet)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Data layer correctness gates — MUST complete before any user-story phase touches persistence.

**⚠️ CRITICAL**: Room schema migration and repository contract extensions are required by all downstream phases.

- [X] T009 Add `MIGRATION_4_5` object to `app/src/main/java/com/flow/data/local/AppDatabase.kt` — SQL: `CREATE UNIQUE INDEX IF NOT EXISTS idx_task_logs_task_date ON task_logs(taskId, date)` (per data-model.md Migration Plan)
- [X] T010 Update `AppDatabase.kt` to `version = 5` and register `MIGRATION_4_5` via `.addMigrations(MIGRATION_4_5)` in the database builder
- [X] T011 Remove `fallbackToDestructiveMigration()` call from `AppModule.kt` in `app/src/main/java/com/flow/di/AppModule.kt` (DI-002 gate)
- [X] T012 [P] Extend `TaskRepository` interface in `app/src/main/java/com/flow/data/repository/TaskRepository.kt` with all Contract 1 methods: `getTodayProgressRatio()`, `getCompletedOnTimeCount()`, `getMissedDeadlineCount()`, `getBestStreak()`, `getHeatMapData()`, `getTaskStreak(taskId)`, `getTaskHistory(taskId)`
- [X] T013 [P] Add `has_seen_tutorial` (`Boolean`, default `false`) and `default_timer_minutes` (`Int`, default `25`) keys to `SettingsManager.kt` in `app/src/main/java/com/flow/data/local/SettingsManager.kt` and expose them as `Flow<>`

**Checkpoint**: DB is at version 5 with unique index; `fallbackToDestructiveMigration` removed; repository interface has all required signatures; project compiles

---

## Phase 3: User Story 1 — Task Management (Priority: P1) 🎯 MVP

**Goal**: A user can capture, progress (To Do → In Progress → Completed), and delete tasks. Task cards display colour by status. Home screen shows bounded active task list.

**Independent Test**: Create a task with a target date two days away — card shows default (To Do) colour. Tap to In Progress — card turns yellow. Tap to Completed — card turns green. Long-press → edit title and target date → save → card updates immediately. Delete task — it disappears from all views.

- [X] T014 [P] [US1] Audit and update `TaskEntity.kt` in `app/src/main/java/com/flow/data/local/TaskEntity.kt` to confirm all required fields exist: `id`, `title`, `description`, `status`, `startDate`, `dueDate`, `isRecurring`, `createdAt`, `completionTimestamp`; add any missing `@ColumnInfo` annotations
- [X] T015 [P] [US1] Add `getTasksActiveToday(today: Long)` and overdue query helpers to `TaskDao.kt` in `app/src/main/java/com/flow/data/local/TaskDao.kt`
- [X] T016 [US1] Implement `updateTaskStatus()` in `TaskRepositoryImpl.kt` — enforce status FSM (TODO→IN_PROGRESS, TODO→COMPLETED, IN_PROGRESS→COMPLETED, COMPLETED→TODO only); set `completionTimestamp` on `→ COMPLETED`; do NOT overwrite it on re-edit (DI-001)
- [X] T017 [US1] Implement `deleteTask()` in `TaskRepositoryImpl.kt` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` — delete task row AND all its `TaskCompletionLog` rows atomically in a single Room transaction
- [X] T018 [US1] Refactor `HomeViewModel.kt` in `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt` to replace the three separate `StateFlow` fields with a single `StateFlow<HomeUiState>` combined via `combine()` and `stateIn(WhileSubscribed(5000))`; inject `SettingsRepository` instead of `SettingsManager` directly
- [X] T019 [US1] Update `HomeScreen.kt` in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` to collect `HomeUiState` and render task cards with derived display colour: green (COMPLETED), yellow (IN_PROGRESS), orange (overdue = dueDate past + not COMPLETED), default card (TODO)
- [X] T020 [US1] Implement add-task bottom sheet / dialog in `HomeScreen.kt` that calls `HomeViewModel.addTask()` with title, optional target date, and recurring flag
- [X] T021 [US1] Implement long-press → edit panel in `HomeScreen.kt` that calls `HomeViewModel.updateTask()` and `HomeViewModel.updateTaskStatus()` on save; the panel MUST include a Delete button that shows an `AlertDialog` confirmation ("Delete task?" with Cancel / Delete actions) before calling `HomeViewModel.deleteTask()` — required by FR-006 and Constitution Principle II
- [X] T022 [US1] Cap home screen to active (non-completed) tasks; pass completed/old tasks to history route — update `HomeScreen.kt` and `HomeViewModel.kt` accordingly

**Checkpoint**: US1 fully functional and independently testable — task CRUD with colour-coded status cards works end-to-end

---

## Phase 4: User Story 2 — Recurring Tasks & Individual Streaks (Priority: P2)

**Goal**: Any task can be marked recurring. The app tracks each recurring task's consecutive-day completion streak and resets it each calendar day.

**Independent Test**: Create one recurring task. Mark it complete on Day 1 — streak shows 1. Mark it complete on Day 2 — streak shows 2. Miss Day 3 — streak resets to 0. Task's detail card shows per-task history.

- [X] T023 [P] [US2] Add `getLogsForTask(taskId: Long): Flow<List<TaskCompletionLog>>` and `getLogForTaskDate(taskId: Long, date: Long): TaskCompletionLog?` to `TaskCompletionLogDao.kt` in `app/src/main/java/com/flow/data/local/TaskCompletionLogDao.kt` (if not already present)
- [X] T024 [US2] Implement `getTaskStreak(taskId: Long): Flow<Int>` in `TaskRepositoryImpl.kt` — count consecutive calendar days (ending today) where a `TaskCompletionLog` row exists with `isCompleted = true`; emit 0 if any day is missing
- [X] T025 [US2] Implement `getTaskHistory(taskId: Long): Flow<List<TaskCompletionLog>>` in `TaskRepositoryImpl.kt` — delegate to `TaskCompletionLogDao.getLogsForTask()`
- [X] T026 [US2] Implement `refreshRecurringTasks()` in `TaskRepositoryImpl.kt` — on each call, ensure every recurring task has a `TaskCompletionLog` row for today with `isCompleted = false` if one doesn't already exist; call this from `HomeViewModel.init {}` on app open
- [X] T027 [US2] Show current streak count badge on recurring task cards in `HomeScreen.kt` — display "🔥 N days" label sourced from `HomeViewModel.getRawTaskStreak(taskId)`
- [X] T028 [US2] Create `TaskHistoryScreen.kt` in `app/src/main/java/com/flow/presentation/history/TaskHistoryScreen.kt` — displays per-task `TaskCompletionLog` list and current streak; includes a `StreakHeatMapGrid` composable (reuse `HeatMapGrid` layout from T032, filtered to this task's completion dates only) to satisfy FR-016; navigates to using `Routes.taskStreak(taskId)`

**Checkpoint**: US2 fully functional — recurring tasks show streak counts; refreshRecurringTasks resets daily; history screen accessible by tapping a recurring task

---

## Phase 5: User Story 3 — Activity Heat Map (Priority: P3)

**Goal**: A GitHub-style contribution grid shows every calendar day coloured by task activity. Weekday labels stay fixed; date columns scroll horizontally. Month labels appear on the top axis.

**Independent Test**: Complete tasks on three different calendar dates. Open heat map — those three dates show coloured cells; all others are grey. Weekday labels (Mon–Sun) remain stationary while scrolling. Month labels are correctly aligned.

- [X] T029 [P] [US3] Add `getAllDailyProgress(): Flow<List<DailyProgressEntity>>` to `DailyProgressDao.kt` in `app/src/main/java/com/flow/data/local/DailyProgressDao.kt` ordered by date ASC for heat map rendering
- [X] T030 [US3] Implement `getHeatMapData(): Flow<Map<Long, Int>>` in `TaskRepositoryImpl.kt` — map `DailyProgressEntity.date → tasksCompletedCount` from `DailyProgressDao`
- [X] T031 [US3] Implement `upsertDailyProgress()` in `TaskRepositoryImpl.kt` — call this inside `updateTaskStatus()` whenever a task is marked COMPLETED or un-completed (COMPLETED→TODO) to keep `DailyProgress` rows accurate
- [X] T032 [US3] Build `HeatMapGrid` composable in `AnalyticsScreen.kt` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt` using a `Row { fixed Column(weekday labels) + LazyHorizontalGrid(date cells) }` layout — the weekday Column MUST be outside the lazy grid to stay fixed during scroll (fixes roadmap bug)
- [X] T033 [US3] Colour heat-map cells by ratio: 0 = grey; 0–0.33 = light green; 0.33–0.66 = mid green; 0.66–1.0 = dark green; any day with tasks whose `dueDate` passed + status ≠ COMPLETED = orange tint overlay — wire to `AnalyticsUiState.heatMapData`
- [X] T034 [US3] Add month labels above the date columns in `HeatMapGrid` — derive month boundaries from the sorted date key list in `AnalyticsUiState.heatMapData`
- [X] T035 [US3] Refactor `AnalyticsViewModel.kt` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt` to emit `StateFlow<AnalyticsUiState>` — combine `getHeatMapData()` stream into `AnalyticsUiState.heatMapData`

**Checkpoint**: US3 fully functional — heat map renders all historical dates; weekday labels are fixed; month labels are visible; cells update after task date edits without restart

---

## Phase 6: User Story 4 — Focus Timer (Priority: P4)

**Goal**: A countdown timer with custom input and 6 presets (5–30 min). Pause freezes display. Resume continues from paused value. Expiry plays an audible alert, shows "Time is up", and display persists. Timer continues when app is backgrounded.

**Independent Test**: Set 1-minute timer. Start → pause at 30 s — display shows 30 s. Resume — counts from 30 s. At zero: alert sounds, "Time is up" stays visible. Background app mid-countdown — alert still fires.

- [X] T036 [P] [US4] Create `TimerViewModel.kt` in `app/src/main/java/com/flow/presentation/timer/TimerViewModel.kt` — `@HiltViewModel`; emits `StateFlow<TimerUiState>`; implements `setDuration()`, `start()`, `pause()`, `resume()`, `reset()`, `dismiss()` (per Contract 5); uses `viewModelScope` + `kotlinx.coroutines.delay` for countdown tick
- [X] T037 [US4] Implement pause/resume in `TimerViewModel.kt` — `pause()` sets `isPaused = true`, stops tick coroutine, preserves `remainingSeconds`; `resume()` restarts tick from `remainingSeconds`; `isFinished = true` when `remainingSeconds == 0`; derive `remainingSeconds` from a `SystemClock.elapsedRealtime()` anchor recorded at start/resume rather than decrementing a counter — required for SC-006 ±1 s accuracy over 30 min
- [X] T038 [US4] Create `TimerForegroundService.kt` in `app/src/main/java/com/flow/presentation/timer/TimerForegroundService.kt` — Android `Service` that runs countdown independently of UI lifecycle; broadcasts `ACTION_TIMER_FINISHED` intent; manages its own `MediaPlayer` for the alert sound
- [X] T039 [US4] Register `TimerForegroundService` in `AndroidManifest.xml` at `app/src/main/AndroidManifest.xml`; add `android.permission.FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions
- [X] T040 [US4] Create `TimerPanel.kt` in `app/src/main/java/com/flow/presentation/timer/TimerPanel.kt` — bottom sheet composable with: numeric input for custom duration, 6 preset chips (5/10/15/20/25/30 min), large countdown display, Start / Pause / Resume / Reset buttons, "Time is up" banner when `isFinished`; collect `TimerUiState` from `TimerViewModel`
- [X] T041 [US4] Wire `TimerPanel` into `HomeScreen.kt` — show via `ModalBottomSheet`; bind to `TimerForegroundService` when timer starts so background execution is active; play alert via `RingtoneManager.getRingtone().play()` when `SideEffect.PlayAlert` fires and app is foregrounded

**Checkpoint**: US4 fully functional — timer counts down; pause/resume work; "Time is up" persists; audio alert fires in foreground and background

---

## Phase 7: User Story 5 — Daily Progress Dashboard (Priority: P5)

**Goal**: Home screen shows a progress bar/percentage for today — completed tasks ÷ total active today — updating in real time on status change.

**Independent Test**: Add 3 tasks for today. Indicator shows 0%. Complete 1 → 33%. Complete all 3 → 100%. No manual refresh needed.

- [X] T042 [US5] Implement `getTodayProgressRatio(): Flow<Float>` in `TaskRepositoryImpl.kt` — query `DailyProgressDao` for today's `DailyProgressEntity`; emit `tasksCompletedCount.toFloat() / tasksTotalCount` (or 0f if null / total is 0)
- [X] T043 [US5] Wire `getTodayProgressRatio()` into `HomeViewModel.kt` — merge into `HomeUiState.todayProgress` via `combine()` so it updates reactively when any task status changes
- [X] T044 [US5] Render daily progress indicator at the top of `HomeScreen.kt` — `LinearProgressIndicator` or `CircularProgressIndicator` bound to `HomeUiState.todayProgress`; display "X% complete today" text label; show "No tasks today" if `todayProgress == 0f` and no tasks loaded

**Checkpoint**: US5 fully functional — progress indicator visible on home screen; updates within 0.5 s of completing a task (SC-003)

---

## Phase 8: User Story 6 — Analytics & History (Priority: P6)

**Goal**: Analytics screen shows total completed, on-time vs missed, current + best-ever streak, and a contribution chart. All driven by `AnalyticsUiState`.

**Independent Test**: Complete tasks across 5+ days. Open Analytics — total count is accurate; on-time vs missed counts match task target dates; current streak reflects today; heat map cells align to correct calendar dates.

- [X] T045 [P] [US6] Implement `getCompletedOnTimeCount(): Int` in `TaskRepositoryImpl.kt` — count tasks where `completionTimestamp != null && dueDate != null && completionTimestamp <= dueDate`
- [X] T046 [P] [US6] Implement `getMissedDeadlineCount(): Int` in `TaskRepositoryImpl.kt` — count tasks where `dueDate != null && dueDate < now() && status != COMPLETED`
- [X] T047 [P] [US6] Implement `getBestStreak(): Int` in `TaskRepositoryImpl.kt` — across all recurring tasks, find the maximum consecutive-day streak ever recorded in `TaskCompletionLog`
- [X] T048 [US6] Complete `AnalyticsViewModel.kt` refactor — combine `getHeatMapData()`, `getCompletedTaskCount()`, `getCompletedOnTimeCount()`, `getMissedDeadlineCount()`, `calculateCurrentStreak()`, `getBestStreak()` into a single `StateFlow<AnalyticsUiState>` using `combine()`
- [X] T049 [US6] Update `AnalyticsScreen.kt` to read `AnalyticsUiState` and render: stats summary row (total, on-time, missed, current streak, best streak) above the heat map; ensure the `HeatMapGrid` composable from Phase 5 is reused here

**Checkpoint**: US6 fully functional — all analytics stats visible and accurate; heat map reused unchanged from Phase 5

---

## Phase 9: User Story 7 — Onboarding & Discoverability (Priority: P7)

**Goal**: First-time users see an automatic multi-step tutorial. Demo tasks are seeded on completion. Returning users can replay from Settings.

**Independent Test**: Clear app data. Launch — onboarding starts automatically, walks through 4 steps (add task, set status, use timer, read heat map). On finish, demo tasks are visible. Relaunch — no onboarding. Settings → Tutorial → onboarding replays.

- [X] T050 [P] [US7] Create `OnboardingFlow.kt` in `app/src/main/java/com/flow/presentation/onboarding/OnboardingFlow.kt` — multi-step Compose composable (4 steps: add task, change status, use timer, read heat map); calls `HomeViewModel.completeOnboarding()` on final step; uses `HorizontalPager` or manual step state
- [X] T051 [P] [US7] Create `SettingsScreen.kt` in `app/src/main/java/com/flow/presentation/settings/SettingsScreen.kt` — shows: default timer minutes `OutlinedTextField` / slider; "Replay Tutorial" list item that navigates to `OnboardingFlow`
- [X] T052 [P] [US7] Create `SettingsViewModel.kt` in `app/src/main/java/com/flow/presentation/settings/SettingsViewModel.kt` — `@HiltViewModel`; injects `SettingsRepository`; emits `StateFlow<SettingsUiState>` (defined in T005a) combined via `combine()`; exposes `saveDefaultTimerMinutes()`, `replayTutorial()` actions
- [X] T053 [US7] Wire `OnboardingFlow` auto-launch in `HomeScreen.kt` — if `HomeUiState.isFirstLaunch == true`, show `OnboardingFlow` as overlay; on completion call `HomeViewModel.completeOnboarding()` which calls `SettingsRepository.setFirstLaunchCompleted()`
- [X] T054 [US7] Seed demo tasks on onboarding completion in `HomeViewModel.completeOnboarding()` — insert: one regular task ("Try completing a task"), one recurring task ("Daily check-in") via `TaskRepository.addTask()`
- [X] T055 [US7] Add `Routes.SETTINGS` to `AppNavGraph.kt` wired to `SettingsScreen`; add settings icon `IconButton` to `HomeScreen.kt` top-app-bar that navigates to `Routes.SETTINGS`

**Checkpoint**: US7 fully functional — onboarding auto-launches once; demo tasks seeded; Settings accessible; tutorial replayable

---

## Final Phase: Polish & Constitution Gates

**Purpose**: Non-regression verification and cross-cutting concerns. Confirms ALL constitution invariants hold across the full build.

- [X] T056 Run the manual verification checklist from `specs/001-app-analysis/quickstart.md §4` — walk through every AL-002 flow: create task → change status → card colour; mark recurring task complete → streak increments; view heat map → cell dates match calendar; run timer to zero → alert plays + display persists
- [X] T057 [P] Verify layer-boundary compliance (CO-001) — inspect `HomeScreen.kt`, `AnalyticsScreen.kt`, `TaskHistoryScreen.kt`, `SettingsScreen.kt`, `OnboardingFlow.kt` and confirm none import or reference a DAO (`TaskDao`, `DailyProgressDao`, `TaskCompletionLogDao`) or `SettingsManager` directly
- [X] T058 [P] Verify `completionTimestamp` immutability (DI-001) — in `TaskRepositoryImpl.updateTask()`, confirm the implementation reads the existing `completionTimestamp` from the DB before writing and preserves it regardless of what the caller passes
- [X] T059 Confirm Room schema is `version = 5`, `MIGRATION_4_5` is registered, `fallbackToDestructiveMigration()` is absent (DI-002) — build a fresh install and a schema-upgrade path, verify no data loss
- [X] T060 Verify release APK size — run `./gradlew assembleRelease` and confirm the output APK in `app/build/outputs/apk/release/` is ≤ 100 MB (plan.md Technical Constraints)

---

## Dependencies (Story Completion Order)

```
Phase 1 (Setup)
    └── Phase 2 (Foundation)
            ├── Phase 3 (US1 — Task Management)   ← MVP
            │       ├── Phase 4 (US2 — Recurring / Streaks)
            │       │       └── Phase 5 (US3 — Heat Map)
            │       │               └── Phase 8 (US6 — Analytics)
            │       ├── Phase 7 (US5 — Daily Progress)
            │       └── Phase 6 (US4 — Focus Timer)   ← independent after US1
            └── Phase 9 (US7 — Onboarding)              ← independent after US1
```

US4 (Timer) and US7 (Onboarding) are independent of US2/US3 and can be worked in parallel with those phases after US1 is complete.

---

## Parallel Execution Examples (within a single story phase)

| Phase | Tasks that can run in parallel |
|---|---|
| Phase 1 | T003, T004, T005, T006, T007 (all different new files) |
| Phase 2 | T012, T013 (different files: repository interface, SettingsManager) |
| Phase 3 (US1) | T014, T015 (entity audit + DAO queries are independent) |
| Phase 5 (US3) | T029 (DailyProgressDao) independent of T030–T034 |
| Phase 6 (US4) | T036 (TimerViewModel) and T038 (TimerForegroundService) different files |
| Phase 8 (US6) | T045, T046, T047 (three separate aggregates in impl) |
| Phase 9 (US7) | T050, T051, T052 (three separate new composable/viewmodel files) |
| Final Phase | T057, T058 (read-only verification in different files) |

---

## Implementation Strategy

**MVP scope**: Complete Phase 1 + Phase 2 + Phase 3 (US1) only. This delivers a fully functional task management app — data-safe, architecture-compliant, and independently verifiable per SC-001, SC-002, SC-003.

**Recommended delivery order**:
1. Phase 1 + 2 (one session — structural scaffolding)
2. Phase 3 (US1 — the core; every other feature depends on it)
3. Phase 4 + Phase 7 in parallel (US2 streak logic + US5 progress bar both extend Phase 3 work)
4. Phase 5 (US3 heat map — builds on Phase 4's DailyProgress upsert)
5. Phase 6 (US4 timer — self-contained, no data-model dependency)
6. Phase 8 (US6 analytics — reads from all prior phases)
7. Phase 9 (US7 onboarding — wraps everything)
8. Final Phase (polish + gate verification)

---

## Summary

| Metric | Count |
|---|---|
| Total tasks | 61 |
| Phase 1 — Setup | 9 |
| Phase 2 — Foundation | 5 |
| Phase 3 — US1 Task Management | 9 |
| Phase 4 — US2 Recurring / Streaks | 6 |
| Phase 5 — US3 Heat Map | 7 |
| Phase 6 — US4 Focus Timer | 6 |
| Phase 7 — US5 Daily Progress | 3 |
| Phase 8 — US6 Analytics | 5 |
| Phase 9 — US7 Onboarding | 6 |
| Final Phase — Polish / Gates | 5 |
| **Parallelisable tasks [P]** | **26** |
