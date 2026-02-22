# Tasks: Task History & Help (Feature 001-task-history-help)

**Feature**: Task History, Help Icon & App Icon Refresh  
**Branch**: `001-task-history-help`  
**Tech Stack**: Kotlin 2.0.20 · Jetpack Compose · Material 3 · Hilt · Room 2.6.1  
**DB Version**: 5 (no schema change — no migration needed)  
**Generated from**: spec.md v1.1, plan.md (Phases A–E), research.md (10 decisions)

---

## Format Legend

```
- [ ] T001           → sequential task (must run after previous)
- [ ] T002 [P]       → parallelizable (different file, no incomplete deps)
- [ ] T005 [US1]     → user story task (story label required in Phase 3+)
- [ ] T006 [P] [US2] → parallelizable user story task
```

---

## Phase 1: Setup

> **Status**: SKIPPED — Android project fully configured (Kotlin, Hilt, Room, Compose, Navigation all wired). No package creation or dependency changes required for this feature.

---

## Phase 2: Foundational — Data Layer

**Purpose**: Fix the broken sort order and add the three new repository queries that every user story depends on. Nothing can compile correctly until these are in place.

**Test Coverage Required**: Unit tests MUST verify the SQL logic correctness of all new DAO queries before proceeding to UI phases. Run with `./gradlew test` (JVM unit tests, Room in-memory DB).

- [X] T001 Fix `getAllTasks()` sort from `ORDER BY status ASC, createdAt DESC` to `ORDER BY createdAt ASC` in `app/src/main/java/com/flow/data/local/TaskDao.kt`
- [X] T002 Add `@Query getHomeScreenTasks(todayStart: Long, tomorrowStart: Long)` returning `Flow<List<TaskEntity>>` to `app/src/main/java/com/flow/data/local/TaskDao.kt`
- [X] T003 Add `@Query getCompletedNonRecurringTasks()` returning `Flow<List<TaskEntity>>` (WHERE isRecurring=0 AND status='COMPLETED') to `app/src/main/java/com/flow/data/local/TaskDao.kt`
- [X] T004 [P] Add `@Query getAllCompletedLogs()` returning `Flow<List<TaskCompletionLog>>` (WHERE isCompleted=1) to `app/src/main/java/com/flow/data/local/TaskCompletionLogDao.kt`
- [X] T005 Declare `getHomeScreenTasks()`, `getAllCompletedRecurringLogs()`, and `getCompletedNonRecurringTasks()` in `app/src/main/java/com/flow/data/repository/TaskRepository.kt`
- [X] T006 Implement the three new repository methods delegating to DAO in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt`

### Unit Tests for Phase 2

- [x] TU01 [P] Write unit tests for `getHomeScreenTasks()` in an in-memory Room database — verify: (a) today's dated tasks included regardless of status, (b) recurring tasks always included, (c) completed non-recurring general tasks excluded, (d) results ordered by `createdAt ASC` — in `app/src/test/java/com/flow/data/local/TaskDaoTest.kt`
- [x] TU02 [P] Write unit tests for `getAllCompletedLogs()` — verify only `isCompleted=1` records returned, ordered by `date DESC, timestamp DESC`; verify a log entry with `isCompleted=0` is excluded — in `app/src/test/java/com/flow/data/local/TaskCompletionLogDaoTest.kt`

**Checkpoint**: `./gradlew assembleDebug` and `./gradlew test` must both pass with zero errors before proceeding to any user story phase.

---

## Phase 3: User Story 1 — Task Persistence on Home Screen (P1)

**Goal**: Completed tasks no longer disappear from the home screen — they stay visible with a green card until the user explicitly deletes them (non-recurring) or the next day resets them (recurring). Card order is stable; tapping a card does not cause it to jump. Overdue tasks must display with an orange 2dp border (FR-007).

**Test Coverage Required**: Instrumented UI tests (Compose UI Test) verify task card visibility after completion, stable card ordering, and overdue border rendering. Run with `./gradlew connectedDebugAndroidTest`.

### Implementation for User Story 1

- [X] T007 [US1] Rename `tasks` → `homeTasks` and add `showHelp: Boolean = false` field in `app/src/main/java/com/flow/presentation/home/HomeUiState.kt`
- [X] T008 [US1] Replace `repository.getAllTasks()` with `repository.getHomeScreenTasks(todayStart, tomorrowStart)` in the combine() flow inside `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt`; add `private val _helpVisible = MutableStateFlow(false)` with `showHelp()` and `hideHelp()` public functions; map `showHelp = _helpVisible.value` into `HomeUiState`
- [ ] T009 [US1] Remove the `val activeTasks = uiState.tasks.filter { it.status != TaskStatus.COMPLETED }` line and all references to `activeTasks` in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`; replace with direct use of `uiState.homeTasks`
- [X] T009b [US1] Inspect the task card composable for overdue color logic (FR-007); if not already implemented, add `borderStroke = BorderStroke(2.dp, Color(0xFFFF6B35))` for tasks where `dueDate != null && dueDate < todayStartMs && status != TaskStatus.COMPLETED` in the card's border/decoration — in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` (or the dedicated task card composable file if extracted)

### Unit Tests for Phase 3

- [x] TU03 [P] [US1] Write a unit test for `HomeViewModel` that verifies `getHomeScreenTasks()` result flows correctly into `HomeUiState.homeTasks` using a fake `TaskRepository` — in `app/src/test/java/com/flow/presentation/home/HomeViewModelTest.kt`
- [x] TI01 [P] [US1] Write an instrumented UI test: create a task, cycle it to COMPLETED, assert the card is still visible on screen with a green background and its `LazyVerticalGrid` position matches the original — in `app/src/androidTest/java/com/flow/TaskPersistenceTest.kt`
- [x] TI02 [P] [US1] Write an instrumented UI test: create a task with a past `dueDate` and status TODO, assert the card displays an orange 2dp border (FR-007) — in `app/src/androidTest/java/com/flow/TaskCardOverdueTest.kt`

**Checkpoint**: `./gradlew assembleDebug`, `./gradlew test`, and `./gradlew connectedDebugAndroidTest` must all pass.

---

## Phase 4: User Story 2 — Global History Screen (P2)

**Goal**: A new History screen (clock icon in top bar) lists every completed task grouped by date with a scrollable date strip. A permanent "Show All" chip at the leading end of the date strip shows all completed tasks as a flat chronological list. Users can also filter by a specific date or by "Completed On" / "Target Date" grouping.

**Test Coverage Required**: Unit tests verify ViewModel filtering/grouping logic; instrumented UI tests verify navigation, Show All mode, and date filtering. Run with `./gradlew test connectedDebugAndroidTest`.

### Implementation for User Story 2

- [X] T010 [P] [US2] Create `HistoryItem` data class (fields: `taskId: Int`, `taskTitle: String`, `targetDate: Long?`, `completedDayMidnight: Long`, `completedAtMs: Long`, `isRecurring: Boolean`) in `app/src/main/java/com/flow/presentation/history/HistoryItem.kt`
- [X] T011 [P] [US2] Create `HistoryViewMode` enum (DATE_GROUPED, CHRONOLOGICAL), `HistoryFilterMode` enum (COMPLETED_ON, TARGET_DATE), and `GlobalHistoryUiState` data class with fields: `items: List<HistoryItem> = emptyList()`, `filteredItems: List<HistoryItem> = emptyList()`, `datesWithData: Set<Long> = emptySet()`, `selectedDate: Long? = null`, `viewMode: HistoryViewMode = DATE_GROUPED`, `filterMode: HistoryFilterMode = COMPLETED_ON`, `showAll: Boolean = false`, `isLoading: Boolean = false`, `error: String? = null` — in `app/src/main/java/com/flow/presentation/history/GlobalHistoryUiState.kt`
- [X] T012 [US2] Create `@HiltViewModel GlobalHistoryViewModel` that combines `repository.getAllCompletedRecurringLogs()` + `repository.getCompletedNonRecurringTasks()` + `repository.getAllTasks()` into a Flow of `List<HistoryItem>`; expose `selectDate(Long?)`, `setViewMode(HistoryViewMode)`, `setFilterMode(HistoryFilterMode)`, and `setShowAll(Boolean)` functions; derived `filteredItems` StateFlow applies: if `showAll=true` → flat chronological list of all items regardless of date; if `selectedDate != null` → filter to that date then apply view mode; otherwise → apply view mode to all items — in `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt`
- [X] T013 [US2] Create `GlobalHistoryScreen` composable in `app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt`:
  - `Scaffold` with `TopAppBar` (title "History", back button)
  - **Loading state**: if `uiState.isLoading` → show `CircularProgressIndicator` centered in body
  - **Error state**: if `uiState.error != null` → show an error banner/`Snackbar` with message
  - **Row 1 — Date strip** (`LazyRow`): **"Show All" chip always at the leading end** (calls `viewModel.setShowAll(true)`; highlighted when `uiState.showAll == true`); followed by date chips for each date in `datesWithData` — highlighted (neon green `#39FF14` fill) when dates have completions, dim gray otherwise; tapping a date chip calls `viewModel.selectDate(date)` + `viewModel.setShowAll(false)`; tapping the active date chip again calls `viewModel.selectDate(null)` to clear
  - **Row 2 — Toggle row**: "Grouped" | "List" (view mode) on left; "Completed On" | "Target Date" (filter mode) on right
  - **Body**: if `isLoading` → loading indicator (above); if `showAll == true` OR `viewMode == CHRONOLOGICAL` → flat `LazyColumn` (most-recent-first, inline date/time per row); if `viewMode == DATE_GROUPED` → `LazyColumn` with sticky date headers and task rows under each
  - **Empty state**: if `filteredItems` is empty → centered message + icon (e.g., "No completed tasks yet. Keep going!")
- [X] T014 [US2] Wire `Routes.HISTORY` composable destination to `GlobalHistoryScreen` (replacing any placeholder) in `app/src/main/java/com/flow/navigation/AppNavGraph.kt`; add `onNavigateToHistory: () -> Unit` parameter to the `HomeScreen` composable call in AppNavGraph and pass `navController.navigate(Routes.HISTORY)` lambda

### Unit Tests for Phase 4

- [x] TU04 [P] [US2] Write unit tests for `GlobalHistoryViewModel.filteredItems` using a fake repository — verify: (a) `showAll=true` returns all items in chronological order regardless of date, (b) `selectedDate=X` filters to only items from date X, (c) `viewMode=DATE_GROUPED` groups by `completedDayMidnight`, (d) `viewMode=CHRONOLOGICAL` returns flat list most-recent-first, (e) `filterMode=TARGET_DATE` groups by `targetDate` — in `app/src/test/java/com/flow/presentation/history/GlobalHistoryViewModelTest.kt`
- [x] TI03 [P] [US2] Write an instrumented UI test: complete two tasks, navigate to History screen via History icon, assert screen opens, items appear in the list, and tapping "Show All" chip shows all items — in `app/src/androidTest/java/com/flow/HistoryScreenTest.kt`

**Checkpoint**: `./gradlew test` (unit) and `./gradlew connectedDebugAndroidTest` (instrumented) must pass.

---

## Phase 5: User Story 3 — App Icon Refresh (P3)

**Goal**: The broken app icon is replaced with an adaptive icon using `app/src/main/res/mipmap-hdpi/flow_logo.png` as the source. The PNG already contains the full design (dark `#0A0A0A` background, neon green `#39FF14` wave lines, "Flow" text) so no format conversion is needed. The foreground layer uses a `<bitmap>` drawable XML referencing density-specific copies of the PNG scaled to the required mipmap dimensions (108dp adaptive foreground). FR-014 is fully satisfied because the PNG artwork is used as-is.

**Test Coverage Required**: `./gradlew assembleDebug` confirms the bitmap drawable inflates without error. Visual verification via installed APK confirms icon rendering.

### Implementation for User Story 3

- [X] T015 [P] [US3] Create `res/drawable/ic_launcher_background.xml` as a `<shape>` rectangle fill with color `#0A0A0A` in `app/src/main/res/drawable/ic_launcher_background.xml`
- [X] T016 [P] [US3] Scale `app/src/main/res/mipmap-hdpi/flow_logo.png` to the following sizes and save as `ic_launcher_foreground.png` in each mipmap density folder (use Android Studio **Image Asset** tool or any image editor):
  - `mipmap-mdpi/ic_launcher_foreground.png` — 108×108 px
  - `mipmap-hdpi/ic_launcher_foreground.png` — 162×162 px
  - `mipmap-xhdpi/ic_launcher_foreground.png` — 216×216 px
  - `mipmap-xxhdpi/ic_launcher_foreground.png` — 324×324 px
  - `mipmap-xxxhdpi/ic_launcher_foreground.png` — 432×432 px
  Then create `app/src/main/res/drawable/ic_launcher_foreground.xml` as:
  ```xml
  <bitmap xmlns:android="http://schemas.android.com/apk/res/android"
      android:src="@mipmap/ic_launcher_foreground"
      android:gravity="center" />
  ```
  No VectorDrawable conversion or SVG text export required.
- [X] T017 [US3] Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` as `<adaptive-icon>` XMLs referencing `@drawable/ic_launcher_background` and `@drawable/ic_launcher_foreground`
- [X] T018 [US3] Verify `AndroidManifest.xml` `android:icon` and `android:roundIcon` still point to `@mipmap/ic_launcher`; confirm existing PNG mipmap fallbacks at `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxdpi` are present (replace with solid-color placeholder PNGs if the originals are broken/missing)

**Checkpoint**: `./gradlew assembleDebug` succeeds (bitmap drawable inflates without error). Install on device and visually confirm the icon shows the full design (wave lines + "Flow" text) in both normal and round shapes.

---

## Phase 6: User Story 4 — Help Icon & Dismissible Onboarding (P4)

**Goal**: A Help (?) icon in the top bar lets users re-open the onboarding/instructions overlay at any time. Tapping outside the overlay card (or the dismiss button) closes it — previously it was impossible to dismiss.

**Test Coverage Required**: Instrumented UI tests verify Help icon visibility, overlay open/dismiss via "Let's Go!" button, and overlay dismiss via outside-tap. Run with `./gradlew connectedDebugAndroidTest`.

### Implementation for User Story 4

- [X] T019 [US4] Add `onDismiss: () -> Unit = onComplete` parameter to `OnboardingFlow` composable and change `Dialog(onDismissRequest = {})` to `Dialog(onDismissRequest = onDismiss)` in `app/src/main/java/com/flow/presentation/onboarding/OnboardingFlow.kt`
- [X] T020 [US4] Add History icon (`Icons.Default.History` or equivalent, size 28.dp) and Help icon (`Icons.Default.HelpOutline`, size 28.dp) to the `actions` block of the TopAppBar in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`; user-visible left-to-right top bar icon order: **Timer | Analytics | History | Help | Settings**; wire `onNavigateToHistory` lambda to History icon `onClick` and `viewModel.showHelp()` to Help icon `onClick`; add `onNavigateToHistory: () -> Unit` as a parameter to the `HomeScreen` composable if not already present
- [X] T021 [US4] Add conditional `if (uiState.showHelp) { OnboardingFlow(onComplete = { viewModel.hideHelp() }, onDismiss = { viewModel.hideHelp() }) }` in `HomeScreen` composable body in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt`

### Unit & Integration Tests for Phase 6

- [x] TI04 [P] [US4] Write an instrumented UI test: assert Help icon is visible in the HomeScreen top bar; tap it; assert the onboarding overlay appears (content description or text visible on screen) — in `app/src/androidTest/java/com/flow/HelpOverlayTest.kt`
- [x] TI05 [P] [US4] Write an instrumented UI test (continuation of TI04): with overlay open, click outside the card (click on the scrim/background); assert the overlay is dismissed (composable no longer in composition) — in `app/src/androidTest/java/com/flow/HelpOverlayTest.kt`

**Checkpoint**: `./gradlew assembleDebug connectedDebugAndroidTest` must pass.

---

## Phase 7: Polish, Cross-Cutting Concerns & Full Test Suite

**Purpose**: Confirm all four user stories work together, layer boundaries are respected, and the full test suite passes.

- [x] T022 [P] Verify layer boundaries — no `HistoryItem` or UI state classes in `data/` package; `GlobalHistoryViewModel` only holds presentation-layer types; run `grep -r "import com.flow.presentation" app/src/main/java/com/flow/data/` and confirm zero hits
- [x] T023 [P] Run full unit test suite: `./gradlew test` — all tests in `TU01`–`TU04` (DAO queries, HomeViewModel, GlobalHistoryViewModel) must pass with zero failures
- [x] T024 Run full instrumented test suite: `./gradlew connectedDebugAndroidTest` — all tests in `TI01`–`TI05` (task persistence, overdue border, history screen, help overlay open, help overlay dismiss) must pass with zero failures
- [x] T025 Verify `./gradlew assembleDebug` produces a clean APK with no lint errors related to this feature's new files; install on physical device and confirm the app launches without crash

**Checkpoint**: `./gradlew test connectedDebugAndroidTest assembleDebug` all pass. Zero regressions detected.

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 2 (Foundational/Data Layer)
  └─ BLOCKS all user story phases
       ├─ Phase 3 (US1 — Task Persistence)  ← start here after Foundational
       ├─ Phase 4 (US2 — History Screen)    ← can start after Foundational
       ├─ Phase 5 (US3 — App Icon)          ← fully independent after Foundational
       └─ Phase 6 (US4 — Help/Onboarding)   ← best after Phase 3 (shares HomeScreen.kt edits)
            └─ Phase 7 (Polish) ← after all stories
```

### User Story Dependencies

| Story | Depends On | Notes |
|---|---|---|
| US1 (P1) | Phase 2 complete | Modifies HomeUiState, HomeViewModel, HomeScreen |
| US2 (P2) | Phase 2 complete | New files; T014 touches AppNavGraph + HomeScreen param |
| US3 (P3) | Nothing | Fully independent — only resource files |
| US4 (P4) | **US1 complete** (HomeScreen.kt), T014 complete (AppNavGraph wired) | Adds more edits to HomeScreen.kt on top of US1 changes |

### Within Each Phase

- Data Layer: T001 → T002 → T003 (same file, sequential); T004 can parallel with T001-T003 block
- US2: T010 and T011 are parallel (different files) → T012 → T013 → T014
- US3: T015 and T016 are parallel (different files) → T017 → T018

### Critical File Edit Order for HomeScreen.kt

`HomeScreen.kt` is edited in three separate phases — always apply in this order to avoid conflicts:

1. **T009** (US1): Remove `activeTasks` filter, use `uiState.homeTasks`
2. **T014** (US2): Add `onNavigateToHistory: () -> Unit` parameter
3. **T020** (US4): Add History + Help icons to TopAppBar actions
4. **T021** (US4): Add help overlay conditional block

---

## Parallel Opportunities

### Phase 2 — Foundational

```
[Parallel group A]  T001 + T002 + T003  → same file TaskDao.kt, sequential
[Parallel group B]  T004                → TaskCompletionLogDao.kt, run alongside A
→ T005  → T006
```

### Phase 4 — US2 History Screen

```
[Parallel]  T010 HistoryItem.kt  +  T011 GlobalHistoryUiState.kt
→ T012 GlobalHistoryViewModel.kt
→ T013 GlobalHistoryScreen.kt
→ T014 AppNavGraph.kt
```

### Phase 5 — US3 App Icon

```
[Parallel]  T015 ic_launcher_background.xml  +  T016 ic_launcher_foreground.xml
→ T017 adaptive icon XMLs
→ T018 manifest/PNG verification
```

### Independent Story Parallelism

US3 (App Icon) has **zero** dependencies on any other user story and can be worked at any time after Phase 2.

---

## Implementation Strategy

### MVP (User Story 1 Only)

1. Complete Phase 2 (Foundational) — T001–T006 + TU01, TU02 (DAO unit tests)
2. Complete Phase 3 (US1) — T007–T009b + TU03, TI01, TI02 (unit + UI tests)
3. **VALIDATE**: Run `./gradlew test connectedDebugAndroidTest` — all Phase 2 + Phase 3 tests pass
4. Ship / demo MVP

### Incremental Delivery

1. Phase 2 → Phase 3 (US1) → **Validate** → deploy
2. Phase 4 (US2 History) → **Validate** → deploy
3. Phase 5 (US3 Icon) → **Validate** → deploy
4. Phase 6 (US4 Help) → **Validate** → deploy
5. Phase 7 (Polish) → final release

### Suggested Sequencing for Single Developer

```
T001→T002→T003→T004→T005→T006  (data layer, ~1 hr)
TU01→TU02                       (DAO unit tests, ~45 min) [parallel with above]
T007→T008→T009→T009b            (US1 fix + overdue border, ~45 min)
TU03→TI01→TI02                  (US1 unit+UI tests, ~1 hr)
T010→T011→T012→T013→T014        (US2 history, ~2 hrs)
TU04→TI03                       (US2 unit+UI tests, ~1 hr)
T015→T016→T017→T018             (US3 icon, ~45 min — scale PNG + bitmap drawable)
T019→T020→T021                  (US4 help, ~30 min)
TI04→TI05                       (US4 UI tests, ~30 min)
T022→T023→T024→T025             (polish + test suite run, ~30 min)
```

---

## Task Summary

| Phase | Tasks | Story | File(s) Touched |
|---|---|---|---|
| 2 Foundational | T001–T006 | — | TaskDao, TaskCompletionLogDao, TaskRepository, TaskRepositoryImpl |
| 2 Unit Tests | TU01–TU02 | — | TaskDaoTest (new), TaskCompletionLogDaoTest (new) |
| 3 US1 | T007–T009b | US1 P1 | HomeUiState, HomeViewModel, HomeScreen |
| 3 Unit+UI Tests | TU03, TI01–TI02 | US1 P1 | HomeViewModelTest (new), TaskPersistenceTest (new), TaskCardOverdueTest (new) |
| 4 US2 | T010–T014 | US2 P2 | HistoryItem (new), GlobalHistoryUiState (new), GlobalHistoryViewModel (new), GlobalHistoryScreen (new), AppNavGraph |
| 4 Unit+UI Tests | TU04, TI03 | US2 P2 | GlobalHistoryViewModelTest (new), HistoryScreenTest (new) |
| 5 US3 | T015–T018 | US3 P3 | flow_logo.png (scale to densities), ic_launcher_background.xml (new), ic_launcher_foreground.xml (new, bitmap), ic_launcher_foreground.png (new ×5 densities), mipmap-anydpi-v26/ (new), AndroidManifest |
| 6 US4 | T019–T021 | US4 P4 | OnboardingFlow, HomeScreen |
| 6 UI Tests | TI04–TI05 | US4 P4 | HelpOverlayTest (new) |
| 7 Polish | T022–T025 | — | grep check, test suites, APK install |

**Total implementation tasks**: 27 (T001–T025 + T009b)  
**Total test tasks**: 9 (TU01–TU04, TI01–TI05)  
**Parallelizable [P] tasks**: T004, T010, T011, T015, T016, T022, T023, T024, TU01, TU02, TU03, TI01, TI02, TI03, TI04, TI05  
**New source files created**: 8 production + 7 test (TaskDaoTest, TaskCompletionLogDaoTest, HomeViewModelTest, GlobalHistoryViewModelTest, TaskPersistenceTest, TaskCardOverdueTest, HistoryScreenTest, HelpOverlayTest)  
**Existing files modified**: 10 (TaskDao, TaskCompletionLogDao, TaskRepository, TaskRepositoryImpl, HomeUiState, HomeViewModel, HomeScreen, OnboardingFlow, AppNavGraph, AndroidManifest verification)

---

## Notes

- DB version stays at **5** — no `@Database` version bump, no migration file needed
- `TaskHistoryScreen.kt` (per-task streak screen) is **not touched** — preserved as-is
- `refreshRecurringTasks()` in `TaskRepositoryImpl` already handles lazy daily reset correctly — no changes
- `updateTaskStatus()` already writes `TaskCompletionLog` on completion and handles reversal — no changes
- The `Routes.HISTORY` constant already exists — T014 only rewires its destination
- `SettingsManager.isFirstLaunch` logic is untouched — onboarding still shows on true first launch
