# Research — Feature 004: Task UI Polish & Scheduling

**Branch**: `004-task-ui-scheduling` | **Date**: 2026-02-28  
**Phase**: 0 — resolves all NEEDS CLARIFICATION items from Technical Context

---

## RES-001 — Emoji Encoding Root Cause & Fix

**Question**: Why do HomeScreen help-overlay strings show garbled bytes (`ðŸŒ±`, `â³`) when the same emoji renders correctly in the Analytics forest?

**Investigation findings**:
- `HomeScreen.kt` lines 348–354, 470, 1116, 1200 contain raw UTF-8 multi-byte emoji literals saved into a file that is being read by the compiler or IDE as Latin-1 (ISO-8859-1). The UTF-8 two/three-byte sequences are interpreted as individual Latin-1 characters, producing the `ðŸŒ±` pattern.
- `AnalyticsScreen.kt` avoids this because `achievementEmoji()` uses `\uXXXX` / `\uD8xx\uDCxx` Java unicode escapes — those are ASCII characters in the source file and have no encoding dependency.
- `OnboardingFlow.kt` was not found separately; the help overlay is embedded directly inside `HomeScreen.kt` (the `HelpOverlayContent` composable starting at line 325).

**Decision**: Replace every raw multi-byte emoji literal in `HomeScreen.kt` with Java/Kotlin Unicode escape sequences (`"\uXXXX"` for BMP characters, surrogate pairs for supplemental). This makes the fix encoding-agnostic and satisfies Constitution Principle VII and FR-003.

**Emoji mapping needed** (HomeScreen.kt):

| Raw (broken) | Intended | Unicode escape |
|---|---|---|
| `ðŸ'†` | 👆 | `"\uD83D\uDC46"` |
| `ðŸŒ±` | 🌱 | `"\uD83C\uDF31"` |
| `ðŸ"Š` | 📊 | `"\uD83D\uDCCA"` |
| `ðŸ"‹` | 📋 | `"\uD83D\uDCCB"` |
| `ðŸŽ¯` | 🎯 | `"\uD83C\uDFAF"` |
| `â³` (hourglass) | ⏳ | `"\u23F3"` |

**Alternatives rejected**:
- Fix file encoding in `.editorconfig` only — does not fix already-broken source literals; must be combined with literal replacement.
- Use `EmojiCompat` library — adds a dependency (violates DEC-003), unnecessary since the fix is source-level.

---

## RES-002 — Achievements Screen Navigation

**Question**: Where does the new Achievements screen live in the navigation graph and how is it reached from the top bar?

**Investigation findings**:
- `HomeScreen.kt` top bar currently has 5 `IconButton`s: Timer (`PlayArrow`), Analytics (`DateRange`), History (`History`), Help (`Info`), Settings (`Settings`). Total action area ~240 dp — a 6th icon fits on all standard phone widths (≥360 dp).
- The `Routes` object is a simple `object` with `const val` strings — trivially extensible.
- `AppNavGraph.kt` registers composables against route strings — pattern is well established.
- `Icons.Default.EmojiEvents` (trophy) is in **Material Icons Extended**, which is included transitively via `androidx.compose.material:material-icons-extended` — already present in the BOM. No new dependency.
- Spec decision (clarification 2026-02-28): dedicated icon in the top bar.

**Decision**:
1. Add `const val ACHIEVEMENTS = "achievements"` to `Routes`.
2. Register `composable(Routes.ACHIEVEMENTS)` in `AppNavGraph`.
3. Add a 6th `IconButton` to `HomeScreen` top bar with `Icons.Default.EmojiEvents` between History and Help.
4. Pass `onNavigateToAchievements` lambda from `HomeScreen` (same pattern as `onNavigateToAnalytics`).

**Alternatives rejected**:
- Navigate from inside Analytics — contradicts spec clarification Q1:B (dedicated nav icon).
- Bottom navigation bar — current app uses top-bar icon pattern; switching to BottomNav is scope creep.

---

## RES-003 — Achievement Hidden Tier & Descriptions

**Question**: How should achievement descriptions and hidden tiers be implemented without DB schema changes?

**Investigation findings**:
- `AchievementEntity` holds only `type: AchievementType`, `taskId`, `earnedAt`, `periodLabel`. No `description` or `visibility` column.
- `AchievementType` is a 6-value enum (`STREAK_10`, `STREAK_30`, `STREAK_100`, `ON_TIME_10`, `EARLY_FINISH`, `YEAR_FINISHER`).
- All description strings are static per type — no user data involved.
- Spec: "exact types designated as hidden are recorded in the implementation plan" — at least one must be hidden.

**Decision**:
- New UI-layer Kotlin `object AchievementMeta` in `presentation/achievements/` containing:
  - `descriptions: Map<AchievementType, String>` — one sentence per type.
  - `hiddenTypes: Set<AchievementType>` — initially `{YEAR_FINISHER}` as the surprise tier.
- New `enum class AchievementVisibility { VISIBLE, HIDDEN }` — derived by checking `hiddenTypes`.
- No DB changes. No migrations. `AchievementEntity` schema untouched.
- Hidden achievements are stored normally in the DB when earned; visibility is a presentation-layer concept only.

**Hidden type rationale**: `YEAR_FINISHER` is the ideal hidden surprise — it requires 365 distinct completion days in one year, which is exceptional. Revealing it upfront would demotivate users who are far from it; discovering it is rewarding.

**Alternatives rejected**:
- Store `isHidden` in DB — no benefit since visibility is always derivable from the type; adds unnecessary migration.
- Hard-code descriptions inline in the Composable — violates Constitution Principle IX (testable helpers; not inline).

---

## RES-004 — Default Task Times: Creation vs. Refresh Split

**Question**: How do we implement the dual-mode start time (exact clock time on creation, 12:01 AM on refresh) without conflating the two code paths?

**Investigation findings**:
- `AddTaskDialog` in `HomeScreen.kt` (line 543): `var startDate by remember { mutableLongStateOf(currentMillis) }` — `currentMillis = System.currentTimeMillis()`. This correctly captures exact creation time at dialog open. ✅ Already correct.
- `dueDate` defaults to `null` in the dialog — needs to change to today at 23:59.
- `TaskRepositoryImpl.addTask()` (line 125–127): **overrides** the passed-in `startDate` to `todayMidnight + 60_000L` for recurring tasks. This is wrong per the updated spec — first creation must preserve the exact creation time.
- `refreshRecurringTasks()` (line 344–346): correctly sets `startDate = today + 60_000L` (12:01 AM), `dueDate = today + 86_340_000L` (11:59 PM). ✅ Already correct for refresh.

**Decision**:
1. **`DateUtils.kt`**: Add `fun defaultEndTime(): Long` → returns today at 23:59:00 local time.
2. **`HomeScreen.kt` `AddTaskDialog`**: Change `dueDate` default to `defaultEndTime()` instead of `null`. Add a `remember { defaultEndTime() }` initializer so it captures the correct value at dialog-open time.
3. **`TaskRepositoryImpl.addTask()`**: Remove the recurring-override block (lines 125–131). Pass `startDate` and `dueDate` through with only timezone normalization (not fixed-time override). The refresh path already handles 12:01 AM independently.
4. **Edit dialog** (`EditTaskDialog`): Apply same `defaultEndTime()` default when `dueDate == null` on open.

**Two distinct helpers**:
- `defaultEndTime()` → today 23:59 (used at dialog open time)
- `defaultRefreshStartTime(todayMidnight)` → already inline in refresh logic; extract to named constant `todayMidnight + 60_000L` for clarity.

**Alternatives rejected**:
- Always use 12:01 AM for all cases — contradicts spec clarification Q2:A (creation = exact time).
- Compute in ViewModel — `System.currentTimeMillis()` is called in dialog `remember {}` which is fine; a ViewModel method would require an extra state emission.

---

## RES-005 — Day-of-Week Schedule UI Chips

**Question**: Does the schedule chip UI need to be built from scratch or does supporting infrastructure exist?

**Investigation findings**:
- `domain/streak/DayMask.kt` exists — already provides bit-manipulation helpers.
- `domain/streak/RecurrenceSchedule.kt` exists — already converts mask to typed schedule.
- `AddTaskDialog` (line 541): `scheduleMask` state already declared and passed to `onAdd`.
- `FilterChip` already used in the existing UI (lines 1041, 1064) — no new component pattern needed.
- The dialog currently has no day-chip rendering when `isRecurring == true` — the chips section is absent.

**Decision**:
- New `internal @Composable fun ScheduleDaySelector(mask: Int, onMaskChange: (Int) -> Unit, isError: Boolean)` in `HomeScreen.kt` (contract C-006 signature — non-nullable `mask`; caller defaults to `127`; `isError` driven by `HomeUiState.scheduleMaskError` per CO-001). Pure helper functions `applySchedulePreset()`, `toggleDayBit()`, and `isScheduleMaskValid()` extracted as `internal` functions for JVM-unit-testable bit-logic (separation required by Constitution III: logic not inlined in composable body).
- Renders 7 `FilterChip`s (Mon–Sun) + 2 quick-select `TextButton`s ("Weekdays", "Weekends").
- Visible only when `isRecurring == true`.
- Validation: if `isRecurring && scheduleMask == 0` → `HomeViewModel` sets `HomeUiState.scheduleMaskError = true` and blocks `repository.addTask()` / `repository.updateTask()` (CO-001: validation at ViewModel layer, not UI layer). The dialog reads `scheduleMaskError` from UI state and passes it as `isError` to `ScheduleDaySelector`.
- Default: all 7 days selected (`mask = 127`) matching null-schedule behaviour for new recurring tasks.

**Alternatives rejected**:
- Third-party calendar picker library — Constitution IX (minimum code changes); no new dependencies (DEC-003).
- Dropdown selector — chips give direct visual feedback on selected days; matches Google Calendar pattern referenced in spec.
