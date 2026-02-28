# Implementation Plan: UI Bug Fixes (001)

**Branch**: `001-ui-bug-fixes` | **Date**: 2026-02-25 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-ui-bug-fixes/spec.md`

---

## Summary

Fix 11 distinct bugs across the Flow Android app spanning the data layer, presentation
layer, and static resources. No new features are being added; all changes are
corrective. The primary technical approach is:

1. **Data layer-first**: Fix root causes in `TaskRepositoryImpl.kt` and `TaskDao.kt`
   before touching any UI, so all UI fixes read from correct data.
2. **Single-source analytics**: Align the analytics "Total" chip to draw from the same
   `task_logs`-based aggregation already used by the heat map.
3. **Reactive settings**: Replace one-shot `firstOrNull()` reads in `TimerViewModel`
   with ongoing `collect {}` to honour settings changes without app restart.
4. **Static resource fixes**: Restore emoji strings, disable dynamic theming, add
   completion sound, and adjust app icon safe-zone padding  all zero-dependency changes.
5. **No new DB migrations**: The UNIQUE index on `task_logs(taskId, date)` already
   exists from migration 45. Schema version stays at 7.

Detailed root-cause analysis: [research.md](research.md)
Behavioral contracts: [contracts/behavioral-contracts.md](contracts/behavioral-contracts.md)
Data model: [data-model.md](data-model.md)

---

## Technical Context

**Language/Version**: Kotlin 2.0, JVM target 11
**Primary Dependencies**: Jetpack Compose (BOM aligned), Material 3, Hilt 2.x, Room 2.6, DataStore Preferences, Kotlin Coroutines + Flow
**Storage**: Room / SQLite  schema version 7; `task_logs` has UNIQUE(taskId, date)
**Testing**: JUnit 4 unit tests (`src/test/`), instrumented tests via AndroidJUnitRunner (`src/androidTest/`)
**Target Platform**: Android, minSdk >= API 24, compileSdk 35; fixes target API 31+ dynamic-color regression
**Project Type**: Mobile (single Android module)
**Performance Goals**: No new work  fixes must not regress startup time or DB query latency
**Constraints**: Offline-only app; no network calls; `task_logs` ON CONFLICT REPLACE must continue to deduplicate on (taskId, date)
**Scale/Scope**: Single-user productivity app; Room DB with <= a few thousand task rows

---

## Constitution Check

*GATE: Checked before Phase 0 research and re-checked after Phase 1 design.*

### Additive Logic (Non-Regression) PASS

Existing flows touched and regression guarantees:

| Affected Flow | Change | Regression Guard |
|---------------|--------|-----------------|
| Add recurring task | Defaults now 12:01 AM / 11:59 PM | Unit test: `addRecurringTask_startsAt1201am_endsAt1159pm` |
| Add non-recurring task | Non-null `dueDate` default = end-of-today | Unit test: `addNonRecurringTask_nullDueDate_defaultsToEndOfToday` |
| Complete recurring task | `completionTimestamp` written to `task_logs` | Instrumented: `recurringTaskRecompletedSameDay_onlyOneLogEntry` |
| Move task to IN_PROGRESS | `completionTimestamp` cleared; log entry removed | Unit test: `updateTask_nonCompletedStatus_clearsCompletionTimestamp` |
| Analytics total chip | Source changed from `totalCompleted` to `heatMapData.sum()` | Unit test: `totalChipValue_equalsHeatMapSum_forCurrentYear` |
| Heat map period filter | `ContributionHeatmap` now takes `startMs/endMs` | Unit + Compose tests with explicit date range assertions |
| Timer settings reactivity | `collect {}` replaces `firstOrNull()` | Unit test: `settingsChange_whenTimerIdle_updatesRemainingSeconds` |
| Theme on API 31+ | `dynamicColor = false` hard-disables system override | Visual check; no unit path (theme applied at host level) |

### Data Integrity PASS

- **No schema migration**: UNIQUE index on `task_logs(taskId, date)` from migration 4->5 is sufficient. Schema version stays at 7.
- **`completionTimestamp` invariant**: Must be non-null iff `status == COMPLETED`. Fixed in `updateTask()` and `updateTaskStatus()`.
- **Time boundary invariant**: `startDate < dueDate` always; recurring: 12:01 AM to 11:59 PM; non-recurring: midnight to end-of-day.
- **`getTasksDueInRange(start, end)`**: New DAO query uses BETWEEN with parameterised Long values.

### Consistency PASS

All fixes respect the existing UI -> ViewModel -> Repository -> DAO/Storage layering:

- Composable changes only read UiState emitted by ViewModel.
- `ContributionHeatmap` receives `startMs`/`endMs` from the ViewModel's period state.
- `TimerViewModel` collects from `SettingsManager` Flow  no DataStore reads in Composable.
- Sound playback wrapped in `LaunchedEffect` side-effect.

### Security PASS

- No credentials, API keys, or PII added to tracked files.
- New `res/raw/timer_complete.ogg` is a static audio asset.
- `getTasksDueInRange()` uses Room `@Query` with `:start`/`:end` bind parameters  no string concatenation.
- No new dependencies introduced.

### Testing PASS

- Unit tests planned for all repository method changes (10 new test methods).
- Instrumented DAO tests planned for `getTasksDueInRange()`.
- Instrumented repository integrity tests planned for history deduplication.
- Compose UI tests planned for heat map date range rendering.
- No testing tier is replaced by manual testing.
- Full test matrix in [quickstart.md](quickstart.md).

---

## Project Structure

### Documentation (this feature)

```text
specs/001-ui-bug-fixes/
+-- plan.md              <- this file
+-- spec.md              <- requirements + clarifications
+-- research.md          <- 11 root causes with file+line citations
+-- data-model.md        <- entity changes, DAO contracts, validation rules
+-- quickstart.md        <- dev onboarding, test plan, definition of done
+-- contracts/
|   +-- behavioral-contracts.md  <- precise per-layer behavioral contracts
+-- tasks.md             <- generated by /speckit.tasks (NOT this command)
```

### Source Code (Android app module)

```text
app/src/
+-- main/
|   +-- java/com/flow/
|   |   +-- data/
|   |   |   +-- local/
|   |   |   |   +-- TaskDao.kt                   <- ADD getTasksDueInRange()
|   |   |   |   +-- TaskEntity.kt                <- read-only (no field changes)
|   |   |   |   +-- AppDatabase.kt               <- read-only (version stays 7)
|   |   |   +-- repository/
|   |   |       +-- TaskRepositoryImpl.kt        <- FIX: addTask, refreshRecurring,
|   |   |                                               updateTask, updateTaskStatus,
|   |   |                                               getTodayProgress
|   |   +-- presentation/
|   |   |   +-- analytics/
|   |   |   |   +-- AnalyticsScreen.kt           <- FIX: emoji, heatmap range, total chip
|   |   |   |   +-- AnalyticsPeriod.kt           <- read-only reference
|   |   |   +-- history/
|   |   |   |   +-- GlobalHistoryViewModel.kt    <- verify no regressions
|   |   |   |   +-- TaskHistoryScreen.kt         <- FIX: streak heatmap range args
|   |   |   +-- timer/
|   |   |   |   +-- TimerViewModel.kt            <- FIX: reactive settings collect
|   |   |   |   +-- TimerPanel.kt                <- FIX: completion sound effect
|   |   |   +-- home/
|   |   |       +-- HomeViewModel.kt             <- verify addTask() default propagates
|   |   +-- ui/theme/
|   |       +-- Theme.kt                         <- FIX: dynamicColor = false
|   +-- res/
|       +-- raw/
|           +-- timer_complete.ogg               <- NEW: completion chime
+-- test/java/com/flow/                          <- 10 new unit test methods
+-- androidTest/java/com/flow/                   <- 5 new instrumented tests
```

**Structure Decision**: Option 3 (Mobile)  single Android Gradle module with standard
`src/main`, `src/test`, `src/androidTest` source sets. No additional modules or API layer.

---

## Implementation Phases

### Phase A — Zero-Risk Static Fixes *(zero code dependencies; can be started in parallel with Phase 1 setup — see tasks.md Phases 12–13 for task IDs; I1 fix)*

| Step | File | Change | LOC |
|------|------|--------|-----|
| A1 | `Theme.kt` | `FlowTheme(dynamicColor: Boolean = false)` | 1 |
| A2 | `AnalyticsScreen.kt` | Restore 6 emoji in `achievementEmoji()` | 6 |
| A3 | `res/raw/timer_complete.ogg` | Add bundled chime file |  |
| A4 | `res/mipmap-*/ic_launcher_foreground*` | Pad content within 66dp safe zone |  |

### Phase B  Data Layer Fixes

| Step | File | Change |
|------|------|--------|
| B1 | `TaskDao.kt` | Add `getTasksDueInRange(start: Long, end: Long): Flow<List<TaskEntity>>` |
| B2 | `TaskRepositoryImpl.kt` | `addTask()`: default `dueDate` to end-of-day; recurring: set start=12:01 AM / due=11:59 PM |
| B3 | `TaskRepositoryImpl.kt` | `refreshRecurringTasks()`: apply same boundary logic as B2 |
| B4 | `TaskRepositoryImpl.kt` | `updateTask()`: clear `completionTimestamp` when `status != COMPLETED` |
| B5 | `TaskRepositoryImpl.kt` | `updateTaskStatus()`: allow COMPLETED to IN_PROGRESS (clears timestamp) |
| B6 | `TaskRepositoryImpl.kt` | `getTodayProgress()`: replace `getTasksDueOn(midnight)` with `getTasksDueInRange(dayStart, dayEnd)` |

### Phase C  Analytics & Heat Map UI Fixes

| Step | File | Change |
|------|------|--------|
| C1 | `AnalyticsScreen.kt` | Refactor `ContributionHeatmap` to accept `startMs: Long` and `endMs: Long`; compute weeks from range |
| C2 | `AnalyticsScreen.kt` | Page 0 "Total" chip: replace `uiState.totalCompleted` with `uiState.heatMapData.values.sum()` |
| C3 | `TaskHistoryScreen.kt` | Pass `startMs = jan1OfCurrentYear`, `endMs = todayEndMs` to `ContributionHeatmap` |

### Phase D  Timer Fixes

| Step | File | Change |
|------|------|--------|
| D1 | `TimerViewModel.kt` | Replace `firstOrNull()` with `collect { if (!isRunning && !isPaused) updateSettings(it) }` |
| D2 | `TimerPanel.kt` | Add `LaunchedEffect(uiState.isFinished)` to play completion sound via `MediaPlayer` with `USAGE_NOTIFICATION` |

### Phase E — Tests

See [tasks.md](tasks.md) for the authoritative test method names, file paths, and tier assignments; they are not duplicated here to prevent drift. *(I2/A2 fix: the table below was a duplicate of per-phase test tasks in tasks.md.)*

### Phase F  Constitution

| Step | File | Change |
|------|------|--------|
| F1 | `.specify/memory/constitution.md` | Append Principle VII (Emoji Non-Negotiable); bump version 1.3.2 -> 1.4.0 |

---

## Complexity Tracking

No constitution gate violations. All changes are corrective and stay within the existing
architecture. No new architectural patterns, abstractions, or dependencies are introduced.

---

## Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| `getTodayProgress()` performance regression | Low | Low | Range BETWEEN on indexed `dueDate` column  same index already used |
| `completionTimestamp` clear breaks existing incomplete-task flows | Medium | High | Unit test `updateTask_nonCompletedStatus_clearsCompletionTimestamp` |
| Dynamic color disable breaks any Material You test | Low | Low | No Material You tests exist; visual regression only |
| `MediaPlayer` resource leak on recompose | Low | Medium | `LaunchedEffect` cleanup lambda disposes `MediaPlayer` |
| `ContributionHeatmap` call site missed during C1 refactor | Low | Medium | `grep ContributionHeatmap` all callers before merging C3 |

---

## Artifacts Generated

| Artifact | Path | Status |
|---------|------|--------|
| Specification | `specs/001-ui-bug-fixes/spec.md` | Complete (clarified) |
| Requirements checklist | `specs/001-ui-bug-fixes/checklists/requirements.md` | 14/14 pass |
| Research | `specs/001-ui-bug-fixes/research.md` | Complete |
| Data model | `specs/001-ui-bug-fixes/data-model.md` | Complete |
| Behavioral contracts | `specs/001-ui-bug-fixes/contracts/behavioral-contracts.md` | Complete |
| Quickstart | `specs/001-ui-bug-fixes/quickstart.md` | Complete |
| Plan | `specs/001-ui-bug-fixes/plan.md` | Complete |
| Tasks | `specs/001-ui-bug-fixes/tasks.md` | /speckit.tasks next |
