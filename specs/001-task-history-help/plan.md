# Implementation Plan: App Icon, Task History & Help Access

**Branch**: `001-task-history-help` | **Date**: 2026-02-21 | **Spec**: [spec.md](spec.md)

---

## Summary

Four independent improvements to the Flow Android app, delivered in priority order:

1. **P1  Task Persistence** (bug fix): Completed tasks vanish from the home screen due to a one-line filter. Root cause also includes a sort-by-status DAO query that reorders cards on every status change. Fix: replace `getAllTasks()` in HomeScreen with a new `getHomeScreenTasks()` query that returns tasks in stable `createdAt ASC` order and includes completed tasks according to per-type visibility rules.

2. **P2  Global History Screen**: New screen accessible via a clock icon in the top bar. Shows all completed tasks via a two-source union (`TaskCompletionLog` for recurring, `TaskEntity.completionTimestamp` for non-recurring). Features: horizontal date strip where highlighted (neon green) dates have data; toggle between date-grouped and chronological list views; "Completed On" / "Target Date" filter modes. No DB schema change  DB stays at version 5.

3. **P3  App Icon**: Replace broken placeholder PNGs with an adaptive icon derived from `flow_logo.png`. The PNG already contains the complete design (wave lines, circle dot, "Flow" text) so no format conversion is required. Background: solid `#0A0A0A` color drawable. Foreground: `<bitmap>` drawable XML referencing density-specific copies of the PNG scaled to the correct mipmap dimensions.

4. **P4  Help Icon + Dismissible Onboarding**: Add a persistent `?` icon to the top bar that re-opens the existing `OnboardingFlow` composable. Fix the `Dialog(onDismissRequest = {})` forced-completion blocker by making outside-tap call `onDismiss`.

---

## Technical Context

**Language/Version**: Kotlin 2.0.20  
**Primary Dependencies**: Jetpack Compose + Material 3, Hilt 2.52 (DI), Room 2.6.1 (SQLite), DataStore Preferences, Navigation Compose 2.8.x, Coroutines + StateFlow  
**Storage**: Room 2.6.1  SQLite via 3 tables: `tasks`, `task_logs`, `daily_progress`. DB version **5** (no migration needed for this feature)  
**Testing**: automated testing and verification only
**Target Platform**: Android API 24+ (minSdk=24, targetSdk=34)  
**Project Type**: Single Android mobile project  
**Performance Goals**: History screen with 500 items filters/re-groups in under 3 seconds (all in-memory, no network)  
**Constraints**: Offline-only; no network calls; `<100 MB APK`; no `fallbackToDestructiveMigration`; no DAO imports in Presentation layer  
**Scale/Scope**: ~18 source files affected; 4 new files; 0 new DB tables; 0 new DB columns

### Critical Finding: Dual Root Cause for Disappearing/Reordering Tasks

| Issue | Location | Root Cause |
|---|---|---|
| Completed tasks disappear | `HomeScreen.kt:52` | `activeTasks = uiState.tasks.filter { it.status != TaskStatus.COMPLETED }` |
| Cards reorder on status change | `TaskDao.kt:12` | `ORDER BY status ASC, createdAt DESC`  alphabetical status sort moves cards |
| Both fixed by | New `getHomeScreenTasks()` DAO query with `ORDER BY createdAt ASC` | |

### Key Existing Infrastructure Reused (No Changes Needed)

| Component | Already-working behaviour reused |
|---|---|
| `refreshRecurringTasks()` | Lazy daily reset on app open  already correct |
| `updateTaskStatus()` | `TaskCompletionLog` write on completion + REPLACE on undo  history preservation already correct |
| `deleteTask()` transactional cascade | Deletes logs with task  no orphaned history |
| `Routes.HISTORY` route + placeholder composable | Exists; swap AnalyticsScreen  GlobalHistoryScreen |
| `SettingsManager.isFirstLaunch` | Drives first-launch onboarding  no change needed |

---

## Constitution Check

### Additive Logic Gate (AL-001, AL-002)

| Existing Flow | Impact | Verification |
|---|---|---|
| Task status cycling | `getHomeScreenTasks()` replaces `getAllTasks()` in HomeScreen; cycling logic in `updateTaskStatus()` unchanged | Manual: cycle all 3 states, verify card stays visible and in position |
| Recurring streak tracking | `TaskCompletionLog` writes unchanged; `getTaskStreak()` and `getTaskHistory()` unchanged | Manual: complete recurring task, verify streak count increments |
| Daily progress % dashboard | `getTodayProgressRatio()` uses `getTasksActiveToday()` (unchanged); progress header still reflects completed/total ratio | Manual: complete tasks, verify % increases |
| Timer panel | `TimerPanel` composable and `TimerViewModel` untouched | No regression expected; confirm Timer icon still works |
| Analytics heatmap | `getHeatMapData()` reads `DailyProgressEntity` via `dailyProgressDao`  no changes to either | No regression expected |
| Per-task streak screen (`task_streak/{taskId}`) | `TaskHistoryScreen` uses own `hiltViewModel()` instance with `getAllTasks()`  not affected by HomeUiState changes | Manual: tap  on recurring task, verify streak screen opens with history |
| Settings screen navigation | Route + composable unchanged | No regression expected |

**Non-regression verification plan**: Automated UI/integration tests verify each regression row; the test suite is run via `./gradlew connectedDebugAndroidTest` after each task set is implemented.

### Data Integrity Gate (DI-001, DI-002)

- **No new tables, columns, or indexes**: DB version stays at 5. No Room migration is triggered.
- **`completionTimestamp` invariant**: Preserved. `updateTaskStatus()` sets on COMPLETED, clears on COMPLETEDTODO undo. Nothing in this feature touches that logic.
- **`TaskCompletionLog` write/undo invariant**: Preserved. Unique index on `(taskId, date)` with REPLACE ensures reversal via `isCompleted=false` is atomic and correct. Nothing in this feature changes this.
- **No destructive migration**: Not applicable (no schema change).
- **`deleteTask()` cascade**: Unchanged  logs deleted with task in single transaction.

### Consistency Gate (CO-001)

All new code follows the established boundary:

| New component | Layer | Depends on |
|---|---|---|
| `GlobalHistoryScreen` | Presentation (UI) | `GlobalHistoryViewModel` only |
| `GlobalHistoryViewModel` | Presentation (VM) | `TaskRepository` interface only |
| `GlobalHistoryUiState`, `HistoryItem` | Presentation (model) | No dependencies |
| `TaskRepository` additions | Data (interface) | DAO types only |
| `TaskRepositoryImpl` additions | Data (implementation) | DAO implementations only |
| DAO additions | Data (persistence) | Room annotations only |

**Constitution violation**: None.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-task-history-help/
 spec.md                            complete
 plan.md                            this file
 research.md                        Phase 0
 data-model.md                      Phase 1
 quickstart.md                      Phase 1
 contracts/
    repository-additions.md       Phase 1
 checklists/
    requirements.md               complete
 tasks.md                           created by /speckit.tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/flow/
 data/
    local/
       TaskDao.kt                MODIFY  new queries, sort fix
       TaskCompletionLogDao.kt   MODIFY  add getAllCompletedLogs()
       TaskEntity.kt             no change
       TaskCompletionLog.kt      no change
       AppDatabase.kt            no change (version stays 5)
       SettingsManager.kt        no change
    repository/
        TaskRepository.kt         MODIFY  3 new method declarations
        TaskRepositoryImpl.kt     MODIFY  3 new implementations
 presentation/
    home/
       HomeScreen.kt             MODIFY  remove filter, +2 top bar icons, help overlay
       HomeUiState.kt            MODIFY  homeTasks, showHelp
       HomeViewModel.kt          MODIFY  new data source, showHelp/hideHelp
    history/
       TaskHistoryScreen.kt      no change (per-task streak screen preserved)
       GlobalHistoryScreen.kt    NEW
       GlobalHistoryViewModel.kt NEW
       GlobalHistoryUiState.kt   NEW
       HistoryItem.kt            NEW
    onboarding/
        OnboardingFlow.kt         MODIFY  add onDismiss param, wire Dialog
 navigation/
    Routes.kt                     no change (Routes.HISTORY already exists)
    AppNavGraph.kt                MODIFY  wire HISTORY route, add onNavigateToHistory

app/src/main/res/
 drawable/
    ic_launcher_background.xml    NEW  dark #0A0A0A background
    ic_launcher_foreground.xml    NEW  neon green wave VectorDrawable
 mipmap-anydpi-v26/
     ic_launcher.xml               NEW  adaptive icon
     ic_launcher_round.xml         NEW  adaptive icon (same as above)
```

**Existing mipmap PNG files** (`mipmap-hdpi/` through `mipmap-xxxhdpi/`): the `ic_launcher.png` and `ic_launcher_round.png` files are currently corrupted/oversized placeholder files. They should be replaced with correctly-sized PNGs generated from the vector art as legacy fallbacks. This is done as part of P3.

---

## Implementation Phases

### Phase A  Data Layer (Enabler for all user stories)

**Goal**: Add new DAO queries and repository methods. No UI change. Buildable and testable in isolation.

**A1  Fix `TaskDao.getAllTasks()` sort order**
- File: `TaskDao.kt`
- Change: `ORDER BY status ASC, createdAt DESC`  `ORDER BY createdAt ASC`
- Risk: Low. No caller depends on the current status-based sort order. All callers either do count operations, find-by-id, or proceed to further filtering.
- Verify: Build compiles. `getAllTasks()` still returns all tasks.

**A2  Add `getHomeScreenTasks()` to `TaskDao`**
- File: `TaskDao.kt`
- Add: New `@Query` method with 4-rule visibility logic (see contracts)
- Parameters: `todayStart: Long`, `tomorrowStart: Long`
- Returns: `Flow<List<TaskEntity>>` ordered by `createdAt ASC`

**A3  Add `getCompletedNonRecurringTasks()` to `TaskDao`**
- File: `TaskDao.kt`
- Add: New `@Query` for history Source B

**A4  Add `getAllCompletedLogs()` to `TaskCompletionLogDao`**
- File: `TaskCompletionLogDao.kt`
- Add: Returns all logs where `isCompleted = 1`, ordered by `date DESC, timestamp DESC`

**A5  Extend `TaskRepository` interface**
- File: `TaskRepository.kt`
- Add: `getHomeScreenTasks()`, `getAllCompletedRecurringLogs()`, `getCompletedNonRecurringTasks()`

**A6  Implement new repository methods in `TaskRepositoryImpl`**
- File: `TaskRepositoryImpl.kt`
- Implement the three interface additions. `getHomeScreenTasks()` computes `todayStart` and `tomorrowStart` internally using `normaliseToMidnight()` (helper already present).

---

### Phase B  P1: Task Persistence Fix (Home Screen)

**Goal**: Completed tasks remain visible. Cards never reorder. All 5 task categories display correctly.

**B1  Update `HomeUiState`**
- File: `HomeUiState.kt`
- Changes:
  - Rename `tasks`  `homeTasks` (home-screen-specific list)
  - Add `showHelp: Boolean = false`

**B2  Update `HomeViewModel`**
- File: `HomeViewModel.kt`
- Changes:
  - Replace `repository.getAllTasks()` with `repository.getHomeScreenTasks()` in the `combine()` in `uiState`
  - Update field mapping: `tasks = tasks`  `homeTasks = homeTasks`
  - Add `private val _helpVisible = MutableStateFlow(false)`
  - Combine `_helpVisible` into `uiState` (now 4 flows  still within `combine()` limit)
  - Add `fun showHelp()` and `fun hideHelp()`

**B3  Fix `HomeScreen.kt`  remove filter, use homeTasks**
- File: `HomeScreen.kt`
- Remove: `val activeTasks = uiState.tasks.filter { it.status != TaskStatus.COMPLETED }`
- Replace: Use `uiState.homeTasks` directly in the `LazyVerticalGrid items(...)` call
- Update all other references from `activeTasks`  `uiState.homeTasks`

**B4  Verify stable card ordering**
- The `LazyVerticalGrid` already uses `key = { it.id }` with stable keys. Combined with `ORDER BY createdAt ASC` from the DAO, cards will not reorder on status change.
- Manual test: complete/undo tasks and verify grid order is stable.

---

### Phase C  P4: Help Icon & Dismissible Onboarding

> **Sequencing note**: Tasks sequence Phase C (US4) after Phase D (US2) to avoid `HomeScreen.kt` edit conflicts — both phases touch the same file (top bar actions and composable parameters). See tasks.md dependency section for the full edit order.

**Goal**: Persistent Help icon in top bar. Outside-tap dismisses the overlay. Both first-launch and help-triggered flows work.

**C1  Fix `OnboardingFlow.kt`**
- File: `OnboardingFlow.kt`
- Change function signature: add `onDismiss: () -> Unit = onComplete` parameter
- Change: `Dialog(onDismissRequest = {})`  `Dialog(onDismissRequest = onDismiss)`

**C2  Add History + Help icons to `HomeScreen.kt` top bar**
- File: `HomeScreen.kt`
- User-visible left-to-right order in top bar: **Timer | Analytics | History | Help | Settings**
- Add to `TopAppBar` actions block:
  ```kotlin
  // History icon
  IconButton(onClick = onNavigateToHistory) {
      Icon(Icons.Default.History, contentDescription = "History", tint = NeonGreen,
          modifier = Modifier.size(28.dp))
  }
  // Help icon
  IconButton(onClick = { viewModel.showHelp() }) {
      Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = NeonGreen,
          modifier = Modifier.size(28.dp))
  }
  ```
  Note: icon size 28.dp for better visibility per user request ("make top bar icons a little bigger").

**C3  Add `onNavigateToHistory` parameter to `HomeScreen` composable**
- File: `HomeScreen.kt`
- Add: `onNavigateToHistory: () -> Unit` to the function signature

**C4  Wire Help overlay in `HomeScreen.kt`**
- Change the existing `OnboardingFlow` invocation:
  ```kotlin
  // Before:
  if (uiState.isFirstLaunch) {
      OnboardingFlow(onComplete = { viewModel.completeOnboarding() })
  }
  // After:
  if (uiState.isFirstLaunch || uiState.showHelp) {
      OnboardingFlow(
          onComplete = {
              viewModel.completeOnboarding()  // idempotent; safe to call even if not first launch
              viewModel.hideHelp()
          },
          onDismiss = {
              viewModel.completeOnboarding()  // first-launch: mark done so it won't re-show
              viewModel.hideHelp()
          }
      )
  }
  ```

---

### Phase D  P2: Global History Screen

**Goal**: Full history screen with date strip, view mode toggle, filter mode toggle.

**D1  Create `HistoryItem.kt`**
- File: `presentation/history/HistoryItem.kt`
- New data class (see data-model.md for fields)

**D2  Create `GlobalHistoryUiState.kt`**
- File: `presentation/history/GlobalHistoryUiState.kt`
- New: `GlobalHistoryUiState`, `HistoryViewMode` enum, `HistoryFilterMode` enum

**D3  Create `GlobalHistoryViewModel.kt`**
- File: `presentation/history/GlobalHistoryViewModel.kt`
- Hilt ViewModel. Combines three repository flows to produce `List<HistoryItem>`.
- Exposes: `selectDate()`, `setViewMode()`, `setFilterMode()`
- Derived: `filteredAndGroupedItems()`  applies current filter + view mode to `allItems`

**D4  Create `GlobalHistoryScreen.kt`**
- File: `presentation/history/GlobalHistoryScreen.kt`
- Layout:
  - `Scaffold` with `TopAppBar` (title "History", back button)
  - **Row 1**: Horizontal date strip (`LazyRow`)  
    - **"Show All" chip** always present at the leading (left) end; tapping it calls `viewModel.selectDate(null)` + `viewModel.setViewMode(CHRONOLOGICAL)` to show all completed tasks as a flat list  
    - Date chips follow, one per unique date in `datesWithData`; highlighted chip = neon green filled, no-data dates shown in dim gray  
    - Tapping a date chip calls `viewModel.selectDate(date)` to filter to that day; tapping the same chip again calls `viewModel.selectDate(null)` to clear the date filter (but does NOT activate Show All mode)
  - **Row 2**: Toggle row  "Grouped" | "List" (view mode) on left; "Completed On" | "Target Date" (filter mode) on right
  - **Body**: If `DATE_GROUPED`  `LazyColumn` with sticky date headers and task rows under each. If `CHRONOLOGICAL`  flat `LazyColumn` with inline date/time on each row.
  - Empty state: if filtered list is empty  centered message + icon

**D5  Wire navigation in `AppNavGraph.kt`**
- Change `Routes.HISTORY` composable from `AnalyticsScreen` placeholder  `GlobalHistoryScreen`
- Add `onNavigateToHistory = { navController.navigate(Routes.HISTORY) }` to the `HomeScreen()` call

---

### Phase E  P3: App Icon

**Goal**: Adaptive icon using the `flow_logo.png` PNG bitmap (dark background, neon green wave lines, "Flow" text).

**E1  Create `ic_launcher_background.xml`**
- File: `app/src/main/res/drawable/ic_launcher_background.xml`
- A simple color drawable: `#0A0A0A` (matches the dark background in `flow_logo.png`)

**E2  Create `ic_launcher_foreground.xml` and scale PNG to all mipmap densities**
- Source file: `app/src/main/res/mipmap-hdpi/flow_logo.png` (the user-supplied PNG, 320×320px at hdpi = 213dp)
- Scale `flow_logo.png` to the required foreground dimensions (108dp at each density) and save as `ic_launcher_foreground.png` in each mipmap density folder:
  - `mipmap-mdpi/ic_launcher_foreground.png` — 108px
  - `mipmap-hdpi/ic_launcher_foreground.png` — 162px
  - `mipmap-xhdpi/ic_launcher_foreground.png` — 216px
  - `mipmap-xxhdpi/ic_launcher_foreground.png` — 324px
  - `mipmap-xxxhdpi/ic_launcher_foreground.png` — 432px
- Create `app/src/main/res/drawable/ic_launcher_foreground.xml` as a `<bitmap>` drawable:
  ```xml
  <bitmap xmlns:android="http://schemas.android.com/apk/res/android"
      android:src="@mipmap/ic_launcher_foreground"
      android:gravity="center" />
  ```
- No VectorDrawable conversion or text path export is required — the PNG preserves the full design including wave lines and "Flow" text.

**E3  Ensure `res/mipmap-anydpi-v26/` directory exists and create adaptive icons**
- File: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- File: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Both use `<adaptive-icon>` with `@drawable/ic_launcher_background` and `@drawable/ic_launcher_foreground`

**E4  Replace PNG fallbacks**
- Current PNGs in `mipmap-hdpi/`, `mipmap-xhdpi/`, `mipmap-xxhdpi/`, `mipmap-xxxhdpi/` are corrupted/oversized
- Replace with correctly-sized solid-color placeholder PNGs or generate from the vector art
- Note: On API 26+ devices the PNG files are ignored in favour of the adaptive icon; they serve only as legacy fallback for API 24-25

---

## Complexity Tracking

No constitution violations. All implementation follows established patterns. No new architecture patterns introduced.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `combine()` with 4 flows in HomeViewModel hits Kotlin limit | Low | Medium | `combine` supports up to 5 type parameters; 4 is within limit |
| `getHomeScreenTasks()` SQL logic misses an edge case (e.g., recurring task with dueDate set) | Medium | Low | Recurring tasks are caught by Rule 1 (`isRecurring=1`) regardless of dueDate; other rules are OR'd |
| `History` icon conflicts with `Icons.Default.History` not existing in current Material Icons set | Low | Low | Use `Icons.AutoMirrored.Filled.ArrowBack` pattern; fall back to `Icons.Filled.DateRange` or `Icons.Outlined.CalendarMonth` |
| `ic_launcher_foreground.xml` VectorDrawable path calculations incorrect at scale | Medium | Low | Visual-only; doesn't break functionality. Can be iterated on without app logic changes |
| `combine()` of 3 flows in `GlobalHistoryViewModel` causes emission storms | Low | Low | All three flows (`getAllCompletedLogs`, `getCompletedNonRecurringTasks`, `getAllTasks`) are cold Room flows; they only emit on DB change |
