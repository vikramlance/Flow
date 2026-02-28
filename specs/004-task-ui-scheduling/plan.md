# Implementation Plan: Task UI Polish — Emoji Fixes, Achievements Screen, Default Times & Recurring Schedules

**Branch**: `004-task-ui-scheduling` | **Date**: 2026-02-28 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/004-task-ui-scheduling/spec.md`

## Summary

Four parallel improvements to the Flow app's task UI:

1. **US1 — Emoji fix**: Replace raw multi-byte emoji literals in `HomeScreen.kt` (help overlay + task cards) with `\uXXXX` Unicode escapes so they render correctly on all API levels regardless of source-file encoding.
2. **US2 — Achievements screen**: Extract the existing achievement section from `AnalyticsScreen` into a new dedicated `AchievementsScreen` (new top-bar nav icon 🏆), add per-type one-sentence descriptions, and implement a hidden-tier placeholder ("??? — Keep going!") for `YEAR_FINISHER`.
3. **US3 — Default task times**: New task dialog defaults start to exact dialog-open clock time and end to 11:59 PM. `TaskRepositoryImpl.addTask()` no longer overrides start time for recurring tasks (that was a prior mis-feature). Daily refresh keeps its independent 12:01 AM start.
4. **US4 — Day-of-week schedule UI**: Add `ScheduleDaySelector` composable to the New/Edit Task dialog when "Track Streak" is checked, wiring to the existing `scheduleMask` field and `DayMask` domain logic already in place.

No database schema changes. No new Gradle dependencies.

## Technical Context

**Language/Version**: Kotlin 2.0 (K2 compiler)  
**Primary Dependencies**: Jetpack Compose + Material 3 (BOM 2024.09.02), Hilt 2.52, Navigation Compose 2.8.x, Material Icons Extended (already in BOM classpath)  
**Storage**: Room 2.6.1 — AppDatabase (no version bump; `scheduleMask` already exists)  
**Testing**: JUnit4 + kotlinx-coroutines-test (unit), Compose UI Test + Hilt Test (instrumented)  
**Target Platform**: Android API 24+ (Samsung SM-S936U, serial R5CY305LTGB)  
**Project Type**: Android single-module (`:app`)  
**Performance Goals**: No new async paths; all changes are UI-layer or synchronous helper additions  
**Constraints**: No new dependencies (DEC-003); no DB migration; no multi-module refactor  
**Scale/Scope**: 4 user stories across 11 source files; 5 new files; 6 new test files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Additive Logic (Non-Regression) ✅**: Affected flows: Home screen task card rendering, help overlay, New/Edit Task dialog, `addTask()` in Repository, `refreshRecurringTasks()`, Analytics screen, top navigation bar. Verification: all pre-existing unit and instrumented test suites must remain green after each task. The `addTask()` change removes an incorrect override — existing tasks are unaffected because persisted data is unchanged. Removing the achievement section from Analytics is intentional and spec-mandated; `AnalyticsHelpersTest` tests `achievementEmoji()` which moves to `AchievementMeta.kt` — the test import path will be updated, not removed.
- **Data Integrity ✅**: No schema changes. `scheduleMask` constraint (null or 1–127) enforced at ViewModel layer (FR-017, DI-001). `addTask()` change preserves caller-supplied timestamps — no data corruption. `refreshRecurringTasks()` is unchanged in its 12:01 AM / 11:59 PM behaviour.
- **Consistency ✅**: All new logic follows established patterns: UI → ViewModel → Repository → DAO. `AchievementsViewModel` is Hilt-injected standard `ViewModel`. `AchievementMeta` is a pure Kotlin object (no Android context dependency — fully unit-testable). `defaultEndTime()` and `endTimeForDate()` are pure functions in `DateUtils.kt`.
- **Security ✅**: (a) No PII or credentials; (b) No new tracked sensitive files; (c) No raw SQL added; (d) No new external dependencies — only `Icons.Default.EmojiEvents` from `material-icons-extended` already on classpath.
- **Testing ✅**: (a) Unit tests: `AchievementMetaTest`, `DateUtilsDefaultTimeTest`, `AddTaskDefaultTimeTest`, `ScheduleDaySelectorTest` — all new; (b) Instrumented: `EmojiRenderTest`, `AchievementsScreenTest` — both new; (c) E2E covered via instrumented scenarios matching spec acceptance criteria SC-001–SC-004; (d) No tier replaced by "manual testing"; (e) TDD order: failing test written before each implementation task as per tasks.md.

---

## Post-Design Constitution Re-check

Completed after Phase 1 design. All contracts reviewed against invariants:

- **INV-04** (no duplicate route strings): `ACHIEVEMENTS = "achievements"` is unique. ✅
- **INV-05** (one impl per interface): `AchievementsViewModel` does not introduce a new Repository interface — reuses `TaskRepository`. ✅
- **INV-14** (emoji renders as pictographic): Unicode escapes satisfy this. ✅
- **INV-20** (`dueDate` is UTC-midnight-aligned): `defaultEndTime()` uses `Calendar` at 23:59 local — this is *not* midnight-aligned by design (it's 23:59, not 00:00). This is correct per spec US3 / FR-010. The `normaliseToMidnight()` helper is not applied to end times — only to date-only fields. ✅ (Invariant scoped to date-only fields, not time-bearing fields.)
- **FAIL-002 / FAIL-003 regression risk**: `addTask()` change removes a forced override — it cannot re-introduce the midnight/timezone offset bug because it now preserves the caller value rather than computing a new one. ✅

---

## Project Structure

### Documentation (this feature)

```text
specs/004-task-ui-scheduling/
├── plan.md              ← this file
├── research.md          ← Phase 0 ✅
├── data-model.md        ← Phase 1 ✅
├── quickstart.md        ← Phase 1 ✅
├── contracts/
│   └── feature-api.md   ← Phase 1 ✅
└── tasks.md             ← Phase 2 (created by /speckit.tasks)
```

### Source Code (Android single-module)

```text
app/src/main/java/com/flow/

├── data/
│   ├── local/
│   │   ├── AchievementEntity.kt            (no change)
│   │   ├── AchievementType.kt              (no change)
│   │   └── TaskEntity.kt                   (no change)
│   └── repository/
│       └── TaskRepositoryImpl.kt           (EDIT — US3: remove start-time override in addTask)

├── domain/streak/
│   ├── DayMask.kt                          (no change — already implements bitmask helpers)
│   └── RecurrenceSchedule.kt              (no change)

├── navigation/
│   ├── Routes.kt                           (EDIT — add ACHIEVEMENTS route)
│   └── AppNavGraph.kt                      (EDIT — register AchievementsScreen)

├── presentation/
│   ├── achievements/                       (NEW package)
│   │   ├── AchievementsScreen.kt           (CREATE)
│   │   ├── AchievementsViewModel.kt        (CREATE)
│   │   ├── AchievementsUiState.kt          (CREATE)
│   │   ├── AchievementMeta.kt              (CREATE — descriptions, hiddenTypes, emoji, name)
│   │   └── AchievementVisibility.kt        (CREATE)
│   ├── analytics/
│   │   └── AnalyticsScreen.kt              (EDIT — remove AchievementsSection + helpers)
│   └── home/
│       └── HomeScreen.kt                   (EDIT — emoji escapes, default times, schedule chips, nav icon)

└── util/
    └── DateUtils.kt                        (EDIT — add defaultEndTime(), endTimeForDate())

app/src/test/java/com/flow/
├── presentation/achievements/
│   └── AchievementMetaTest.kt              (CREATE)
├── util/
│   └── DateUtilsDefaultTimeTest.kt         (CREATE)
├── data/repository/
│   └── AddTaskDefaultTimeTest.kt           (CREATE)
└── presentation/home/
    └── ScheduleDaySelectorTest.kt          (CREATE)

app/src/androidTest/java/com/flow/
├── EmojiRenderTest.kt                      (CREATE)
└── AchievementsScreenTest.kt               (CREATE)
```

---

## Fix-001 — Emoji Encoding (US1, FR-001–FR-003)

**Root cause**: `HomeScreen.kt` contains raw UTF-8 multi-byte emoji literals that the compiler reads as Latin-1, producing garbled output. `AnalyticsScreen.kt` is unaffected because it uses `\uXXXX` escapes.

**Files**: `HomeScreen.kt` only.

**Changes**:

| Line(s) | Broken literal | Replacement |
|---------|---------------|-------------|
| 348 | `"ðŸ'† Tap a task..."` | `"\uD83D\uDC46 Tap a task..."` |
| 350 | `"ðŸŒ± Recurring tasks..."` | `"\uD83C\uDF31 Recurring tasks..."` |
| 352 | `"ðŸ"Š Tap Analytics..."` | `"\uD83D\uDCCA Tap Analytics..."` |
| 353 | `"ðŸ"‹ Tap History..."` | `"\uD83D\uDCCB Tap History..."` |
| 354 | `"ðŸŽ¯ The dashboard..."` | `"\uD83C\uDFAF The dashboard..."` |
| 470 | `Text("ðŸŒ±", ...)` | `Text("\uD83C\uDF31", ...)` |
| 1116 | `"Take a break! ðŸ"‹"` | `"Take a break! \uD83D\uDCCB"` |
| 1200 | `Text("ðŸŒ±", ...)` | `Text("\uD83C\uDF31", ...)` |

**Test**: New `EmojiRenderTest` (instrumented) — launches HomeScreen, taps Help icon, asserts the text content does not contain the replacement-character sequence `\ufffd` or the Latin-1 garble pattern.

---

## Fix-002 — Achievements Screen (US2, FR-004–FR-008a)

**Files**: New `presentation/achievements/` package (5 files), edit `AnalyticsScreen.kt`, edit `Routes.kt`, edit `AppNavGraph.kt`, edit `HomeScreen.kt`.

### Step 1 — New `AchievementMeta.kt`

Move `achievementEmoji()` and `achievementName()` from `AnalyticsScreen.kt` into `AchievementMeta`. Add:
- `descriptions: Map<AchievementType, String>` (6 entries, one sentence each)
- `hiddenTypes: Set<AchievementType>` = `{YEAR_FINISHER}`
- `fun visibilityOf(type): AchievementVisibility`

### Step 2 — New `AchievementsScreen.kt`

Full composable with `TopAppBar` (back navigation), `LazyColumn` containing:
- One `EarnedBadgeCard` per earned achievement (sorted by `earnedAt` DESC)
- One `HiddenPlaceholderCard` per `hiddenTypes` member not yet earned
- One `VisibleUnearnedRow` per VISIBLE type not yet earned
- `HowItWorksSection` (expandable via `AnimatedVisibility`) listing all VISIBLE types

### Step 3 — New `AchievementsViewModel.kt`

Hilt ViewModel. Collects `repository.getAllAchievements()`. Exposes `AchievementsUiState`. Handles `toggleHowItWorks()`.

### Step 4 — Navigation wiring

- `Routes.ACHIEVEMENTS = "achievements"`
- `AppNavGraph`: register composable for `Routes.ACHIEVEMENTS`
- `HomeScreen` topBar: add 6th `IconButton` with `Icons.Default.EmojiEvents`, between History and Help

### Step 5 — Remove from `AnalyticsScreen.kt`

- Remove `AchievementsSection` composable and its `LazyColumn` `item{}` call
- Remove `achievementEmoji()` and `achievementName()` private functions (now in `AchievementMeta`)
- Update any import that referenced them
- Verify `AnalyticsUiState.achievements` field — if no other Analytics tab uses it, remove it too (check `AnalyticsViewModel`)

**Test**: `AchievementMetaTest` (unit) — all 6 descriptions non-null/non-empty; `YEAR_FINISHER` is hidden; other 5 are visible; `achievementEmoji()` returns non-empty string for all types. `AchievementsScreenTest` (instrumented) — screen opens via icon tap; earned badge shows description; hidden unearned shows "???"; "How Achievements Work" section expands on tap; Analytics screen contains no achievement section.

---

## Fix-003 — Default Task Times (US3, FR-009–FR-012)

**Files**: `DateUtils.kt`, `HomeScreen.kt` (`AddTaskDialog` + `EditTaskDialog`), `TaskRepositoryImpl.kt`.

### Step 1 — `DateUtils.kt` additions

Add `defaultEndTime(): Long` and `endTimeForDate(dateMillis: Long): Long`. See [contracts/feature-api.md](contracts/feature-api.md) C-001 for signatures.

### Step 2 — `TaskRepositoryImpl.addTask()` fix

Remove lines 125–131 (the recurring-override block that forces 12:01 AM for creation). Replace with timezone-safe pass-through. `refreshRecurringTasks()` is untouched — it already correctly sets 12:01 AM independently.

### Step 3 — `AddTaskDialog` defaults

Change `dueDate` initial state from `null` to `remember { defaultEndTime() }`. Ensure the date picker `confirmButton` handler sets `dueDate = endTimeForDate(utcDateToLocalMidnight(it))` (preserving 23:59 when user picks a date).

### Step 4 — `EditTaskDialog` defaults

Apply same `defaultEndTime()` default when the task's existing `dueDate == null` on dialog open.

**Test**: `DateUtilsDefaultTimeTest` (unit) — `defaultEndTime()` returns today 23:59; `endTimeForDate()` preserves calendar day at 23:59. `AddTaskDefaultTimeTest` (unit) — `addTask()` with recurring=true no longer overwrites a non-midnight `startDate`; `refreshRecurringTasks()` still produces 12:01 AM start.

---

## Fix-004 — Day-of-Week Schedule UI (US4, FR-013–FR-018)

**Files**: `HomeScreen.kt` (adds `ScheduleDaySelector` composable + pure helpers), `TaskRepositoryImpl.kt` (adds schedule filter in refresh), `HomeViewModel.kt` (adds validation state).

### Step 1 — `ScheduleDaySelector` composable

Private composable visible in test scope (`internal`). Renders:
- Quick-select `TextButton`s: "Every day" (mask=127), "Weekdays" (mask=31=0b0011111), "Weekends" (mask=96=0b1100000)
- 7 `FilterChip`s (Mon–Sun) toggling individual bits
- Error text "Select at least one day" when `isError = true`

**Pure-function extraction requirement** (Constitution III / H1 fix): before implementing the composable body, extract three `internal` pure functions so they are JVM-unit-testable without a Compose runtime: `applySchedulePreset(preset: SchedulePreset): Int`, `toggleDayBit(mask: Int, bit: Int): Int`, and `isScheduleMaskValid(mask: Int?): Boolean`. The composable delegates entirely to these functions — no logic inlined in the composable body.

### Step 1b — `refreshRecurringTasks()` schedule filter (FR-016)

**File**: `data/repository/TaskRepositoryImpl.kt`

FR-016 requires that recurring tasks are only reset to TODO if today's day-of-week is included in the stored `scheduleMask`. Without this step the schedule chips are cosmetic — tasks would still appear on days they are not scheduled for.

**Change**: in `refreshRecurringTasks()`, wrap the per-task reset block in a `DayMask.isScheduledToday(task.scheduleMask)` guard:
- If `scheduleMask == null` → treat as 127 (all days, FR-018) → always reset.
- If the bit for today's weekday is set in `scheduleMask` → reset to TODO.
- If the bit for today is **not** set → skip this task entirely; state is preserved.

`DayMask.isScheduledToday()` (or equivalent helper in `domain/streak/DayMask.kt`) should already exist or be a one-line addition reusing `DayMask` bit-check logic already in place.

**Test**: `RefreshScheduleFilterTest` (unit) — (a) mask excluding Sunday → no reset on Sunday; (b) mask including Monday → reset on Monday; (c) null mask → always reset.

### Step 2 — Wire into `AddTaskDialog`

When `isRecurring == true`, show `ScheduleDaySelector`. Default `scheduleMask` to `127` (not `null`) at dialog open so all chips appear selected. On save: if `scheduleMask == 0`, show error and block dismiss. Pass `scheduleMask` to `onAdd`.

### Step 3 — Wire into `EditTaskDialog`

Same chip selector when editing a recurring task. Null `scheduleMask` in existing edited task displays all chips selected (backwards-compat, FR-018).

### Step 4 — ViewModel validation

Move the `scheduleMask == 0` rejection to `HomeViewModel.addTask()`. Add a `scheduleMaskError: Boolean` field to `HomeUiState` — set to `true` when `isRecurring == true && scheduleMask == 0`, `false` otherwise. The dialog **reads** `scheduleMaskError` from `uiState` (via `collectAsStateWithLifecycle()`) and passes it as `isError` to `ScheduleDaySelector` — no inline `isError` computation in the composable (Constitution III: validation at ViewModel layer, not UI layer — FR-017, CO-001). Emit a `Snackbar` event through the ViewModel's side-effect channel to accompany the inline error text.

**Test**: `ScheduleDaySelectorTest` (unit) — tests `applySchedulePreset()`, `toggleDayBit()`, and `isScheduleMaskValid()` pure functions (not the composable itself, which requires Compose runtime); "Weekdays" → bits 0–4; "Weekends" → bits 5–6; mask=0 invalid; mask=null treated as 127. `RefreshScheduleFilterTest` (unit) — verifies `refreshRecurringTasks()` skips tasks whose `scheduleMask` excludes today. Existing `StreakCalculatorTest` verifies that `DayMask` round-trips are unaffected.

---

## Test Plan Summary

| Test class | Tier | Story | Assertion |
|-----------|------|-------|-----------|
| `AchievementMetaTest` | Unit | US2 | Descriptions non-empty; YEAR_FINISHER hidden; emoji correct |
| `AchievementsViewModelTest` | Unit | US2 | `toggleHowItWorks()` flips state; `earned` list populated from faked Flow |
| `DateUtilsDefaultTimeTest` | Unit | US3 | `defaultEndTime()` = today 23:59; `endTimeForDate()` same-day |
| `AddTaskDefaultTimeTest` | Unit | US3 | `addTask()` preserves startDate; refresh still 12:01 AM |
| `ScheduleDaySelectorTest` | Unit | US4 | Pure-function unit tests: `applySchedulePreset()`, `toggleDayBit()`, `isScheduleMaskValid()` |
| `RefreshScheduleFilterTest` | Unit | US4 | `refreshRecurringTasks()` skips tasks whose mask excludes today; null = all days |
| `EmojiRenderTest` | Instrumented | US1 | No garble/replacement chars in HomeScreen help overlay |
| `AchievementsScreenTest` | Instrumented | US2 | Nav icon opens screen; badge descr shown; FR-007 real-time unlock; Analytics clean |
| `AnalyticsHelpersTest` | Unit (existing) | US2 | `achievementEmoji()` still returns correct values after move |
| `DateUtilsTest` | Unit (existing) | US3 | `utcDateToLocalMidnight()` unaffected |
| `StreakCalculatorTest` | Unit (existing) | US4 | DayMask unchanged |
| `TaskPersistenceTest` | Instrumented (existing) | US3/US4 | Task create/edit round-trips persist correct values |
