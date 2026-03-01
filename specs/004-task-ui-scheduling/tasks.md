# Tasks: Task UI Polish — Emoji Fixes, Achievements Screen, Default Times & Recurring Schedules

**Branch**: `004-task-ui-scheduling` | **Date**: 2026-02-28  
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Total tasks**: 33 | **Stories**: US1 (3) · US2 (9) · US3 (6) · US4 (7) | **Shared foundation**: 6 | **Regression gate**: 2

> **TDD Rule**: Every RED test task must be committed before its GREEN implementation task begins.
> Run `.\gradlew :app:testDebugUnitTest` after every task. Device gate (adb devices) before any Tier 2 task.

---

## Phase 1 — Foundational (blocks US2 and US3; complete before starting either)

> Pure Kotlin types and utility functions with no Android-context dependency.
> T001–T002 are parallel (different files). T003–T006 are parallel after T001–T002 unblock them.

- [X] T001 [P] Write failing `AchievementMetaTest` in `app/src/test/java/com/flow/presentation/achievements/AchievementMetaTest.kt` — assert all 6 `AchievementType` values have a non-empty description, `YEAR_FINISHER` returns `HIDDEN`, the other 5 return `VISIBLE`, and `achievementEmoji()` returns a non-empty string for every type. Run test — expect **RED** (class does not exist yet).

- [X] T002 [P] Write failing `DateUtilsDefaultTimeTest` in `app/src/test/java/com/flow/util/DateUtilsDefaultTimeTest.kt` — assert `defaultEndTime()` returns a millis value whose `Calendar.HOUR_OF_DAY` = 23 and `MINUTE` = 59 for today; assert `endTimeForDate(x)` shares the same calendar date as `x` and has hour=23, minute=59. Run test — expect **RED** (functions do not exist yet).

- [X] T003 [P] Create `app/src/main/java/com/flow/presentation/achievements/AchievementVisibility.kt` — enum with `VISIBLE` and `HIDDEN` values as specified in [data-model.md](data-model.md). Verify: file compiles; no tests needed for a bare enum.

- [X] T004 Create `app/src/main/java/com/flow/presentation/achievements/AchievementMeta.kt` — Kotlin `object` containing: `descriptions: Map<AchievementType, String>` (6 entries, one sentence each per [data-model.md](data-model.md)); `hiddenTypes: Set<AchievementType>` = `{YEAR_FINISHER}`; `fun visibilityOf(type: AchievementType): AchievementVisibility`; `fun achievementEmoji(type: AchievementType): String` (move from `AnalyticsScreen.kt` lines 311–318, exact same escape sequences); `fun achievementName(type: AchievementType): String` (move from `AnalyticsScreen.kt` lines 320–327). Run `T001` test — expect **GREEN**. Run full unit suite — expect green.

- [X] T005 Add `defaultEndTime(): Long` and `endTimeForDate(dateMillis: Long): Long` to `app/src/main/java/com/flow/util/DateUtils.kt` — pure `Calendar` functions per [contracts/feature-api.md](contracts/feature-api.md) C-001. Run `T002` test — expect **GREEN**. Run full unit suite — expect green.

- [X] T006 [P] Create `app/src/main/java/com/flow/presentation/achievements/AchievementsUiState.kt` — data class with `earned: List<AchievementEntity>` and `isHowItWorksExpanded: Boolean` per [data-model.md](data-model.md). Verify: file compiles.

---

## Phase 2 — User Story 1: Emoji Fixes (P1)

> Independent of Phases 3–5. Can be worked on while Phase 1 is complete.
> Acceptance test: SC-001 — zero garbled characters on HomeScreen and help overlay.

**Story goal**: Every emoji in `HomeScreen.kt` renders as its intended pictographic symbol on all supported API levels.

**Independent test criteria**: Launch app on device → tap ℹ️ Help icon → verify 🌱, 📊, 📋, 🎯, 👆 all render as pictographic symbols, not `ðŸŒ±` / `â³` garble.

> **⛔ Device Gate (Constitution VIII — mandatory before T007)**: Run the ADB preflight check first:
> ```powershell
> # Uses ANDROID_HOME env var (set in .local/env.ps1) or falls back to PATH.
> $adb = if ($env:ANDROID_HOME) { "$env:ANDROID_HOME\platform-tools\adb.exe" } else { "adb" }
> $devices = & $adb devices 2>&1 | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "^List" }
> if (-not $devices) { Write-Host "No device found. Connect device (USB debugging on) or start AVD in Android Studio → Device Manager → ▶, then re-run."; exit 1 }
> Write-Host "Device found: $devices"
> ```
> **If no device is detected: STOP. Do not proceed. Resolve device connectivity, then restart Phase 2 from this gate.**

- [ ] T007 [US1] Write failing `EmojiRenderTest` in `app/src/androidTest/java/com/flow/EmojiRenderTest.kt` — launches `HomeScreen` via `ActivityScenario`, taps the Help/Info icon, and asserts none of the `Text` nodes in the help overlay contain the byte sequence `ðŸ` or the replacement character `\uFFFD`. Confirm device connected (`adb devices`) before writing. Run test — expect **RED** (garbled strings exist).

- [X] T008 [US1] Fix 8 garbled emoji literals in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` using `\uXXXX` surrogate-pair escapes per the Fix-001 table in [plan.md](plan.md) (locate each literal by its **broken string content** as listed in the table — do not rely on line numbers, which will have drifted). No other changes. Run `T007` instrumented test — expect **GREEN**. Run full unit suite — expect green.

  > **⛔ INV-04 Gate**: Before executing this task, confirm that `invariants.md` INV-04 has been updated to permit `\uXXXX` surrogate-pair escapes (update was applied on 2026-02-28 as part of this spec review cycle). If you are reading a copy of `invariants.md` that still says *"never an escaped sequence"*, stop — do not execute until the contradiction is resolved.

- [ ] T009 [US1] Verify regression: run `.\gradlew :app:testDebugUnitTest` — all pre-existing unit tests green. Acceptance: SC-001 satisfied.

---

## Phase 3 — User Story 2: Achievements Screen (P2)

> Depends on Phase 1 complete (AchievementMeta, AchievementsUiState, AchievementVisibility exist).
> T010 (instrumented test) and T011 (ViewModel) are parallel — different files.
> T012 (Routes) is parallel with T010 and T011.

**Story goal**: A dedicated Achievements screen is reachable via a new top-bar icon. Each badge shows a description. Hidden achievements show "??? — Keep going!". Analytics screen has no achievement content.

**Independent test criteria**: Tap Achievements icon in top bar → screen opens → earned "Early Bird" badge shows description → YEAR_FINISHER (if unearned) shows "???" → tap "How Achievements Work" → section expands with visible-type criteria → navigate to Analytics → no achievement section visible.

- [ ] T010 [P] [US2] Write failing `AchievementsScreenTest` in `app/src/androidTest/java/com/flow/AchievementsScreenTest.kt` — (a) taps Achievements icon in HomeScreen top bar and asserts navigation to Achievements screen; (b) seeds one earned `EARLY_BIRD` achievement, opens screen, asserts badge displays "Early Bird" text and a non-empty description string; (c) with no earned `YEAR_FINISHER`, asserts placeholder text "???" is visible; (d) taps "How Achievements Work" and asserts it expands; (e) opens AnalyticsScreen and asserts no node with `contentDescription = "Achievements"` exists; (f) with the Achievements screen open, calls `emit()` on a test-double `getAllAchievements()` flow to publish a new `YEAR_FINISHER` `AchievementEntity` and asserts the "???" placeholder node is replaced by the full badge card (text "Year Finisher" visible) without navigating away — verifying FR-007 real-time transition. Run test — expect **RED** (screen and icon do not exist yet).

- [X] T011 [P] [US2] Create `app/src/main/java/com/flow/presentation/achievements/AchievementsViewModel.kt` — `@HiltViewModel` collecting `repository.getAllAchievements()` into `AchievementsUiState.earned`; `toggleHowItWorks()` flips `isHowItWorksExpanded`. Per contract C-003 in [contracts/feature-api.md](contracts/feature-api.md). Also create `app/src/test/java/com/flow/presentation/achievements/AchievementsViewModelTest.kt` — (a) assert `toggleHowItWorks()` inverts `isHowItWorksExpanded` in `uiState`; (b) assert a list emitted by a fake `TaskRepository.getAllAchievements()` `StateFlow` populates `uiState.earned`. Run full unit suite including `AchievementsViewModelTest` — expect green.

- [X] T012 [P] [US2] Add `const val ACHIEVEMENTS = "achievements"` to `app/src/main/java/com/flow/navigation/Routes.kt`. Verify: no duplicate route strings. Run full unit suite — expect green.

- [X] T013 [US2] Create `app/src/main/java/com/flow/presentation/achievements/AchievementsScreen.kt` — full `@Composable` per contract C-004: `TopAppBar` with back icon + "Achievements" title; `LazyColumn` with: `EarnedBadgeCard` (emoji, name, description, earnedAt date) for each earned achievement sorted by `earnedAt` DESC; `HiddenPlaceholderCard` ("???", "Keep going!") for each `HIDDEN` type not yet in earned list; `VisibleUnearnedRow` (greyed) for each `VISIBLE` type not yet earned; `HowItWorksSection` with `AnimatedVisibility` toggled by `viewModel.toggleHowItWorks()` listing all `VISIBLE` types with their criterion. Screen must never be empty. Run full unit suite — expect green.

- [X] T014 [US2] Register `AchievementsScreen` in `app/src/main/java/com/flow/navigation/AppNavGraph.kt` — add `composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onBack = { navController.popBackStack() }) }` per contract C-005. **Scope**: this task adds only the new `composable(Routes.ACHIEVEMENTS)` registration block. The lambda wiring at the existing `HomeScreen(...)` call site in this same file is handled by T015(c) and is a distinct edit. Run full unit suite — expect green.

- [X] T015 [US2] Add Achievements nav to `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` (primary) and `app/src/main/java/com/flow/navigation/AppNavGraph.kt` (secondary) — (a) add `onNavigateToAchievements: () -> Unit` lambda parameter to `HomeScreen`; (b) insert `IconButton(onClick = onNavigateToAchievements) { Icon(Icons.Default.EmojiEvents, contentDescription = "Achievements") }` between the History and Help icon buttons in the `TopAppBar` actions; (c) in `AppNavGraph.kt`, locate the **existing** `HomeScreen(...)` composable call and add `onNavigateToAchievements = { navController.navigate(Routes.ACHIEVEMENTS) }` — this is a different edit from T014’s `composable(Routes.ACHIEVEMENTS)` registration block which is already complete by this point. Per contract C-005. **Before marking complete**: grep the entire project for all call sites of `HomeScreen(` (`grep -r "HomeScreen(" app/src`) and verify that every call site — including any `@Preview` functions and non-NavGraph usages — has been updated to include the new `onNavigateToAchievements` parameter. Run full unit suite — expect green.

- [X] T016a [US2] Clean up `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt` and `app/src/test/java/com/flow/presentation/analytics/AnalyticsHelpersTest.kt` — **Scope: exactly 2 files.** (a) In `AnalyticsScreen.kt`: remove the `if (uiState.achievements.isNotEmpty()) { item { AchievementsSection(...) } }` block; remove the `AchievementsSection` composable; remove `internal fun achievementEmoji()` (move to `AchievementMeta` in T004); remove `private fun achievementName()` (move to `AchievementMeta` in T004); remove the `AchievementType` import if no longer used. (b) In `AnalyticsHelpersTest.kt`: update the import for `achievementEmoji` from `AnalyticsScreen` to `com.flow.presentation.achievements.AchievementMeta`. Run `AnalyticsHelpersTest` — expect **GREEN**. Run full unit suite — expect green. **Commit T016a independently before starting T016b.**

  > **Rollback note**: T016a and T016b are separate commits. If T016b causes a compilation failure, `git revert <T016b-sha>` alone restores a compilable state with the Analytics UI already cleaned up.

  > *Requires T004 complete* — `AchievementMeta.kt` must exist before removing the functions from `AnalyticsScreen.kt`.

- [X] T016b [US2] Remove the now-unused `achievements` field from the Analytics data layer — **Scope: 2 files.** (a) In `app/src/main/java/com/flow/presentation/analytics/AnalyticsUiState.kt`: remove the `achievements: List<AchievementEntity>` field and the `AchievementEntity` import (**audit confirmed**: after T016a this field has zero remaining consumers in the Analytics screen). (b) In `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt`: remove `repository.getAchievements()` from the `combine()` call (reduce from 5-source to 4-source combine), and remove the `achievements` parameter from the combine lambda and the `AnalyticsUiState(...)` constructor call. Verify no other file references `AnalyticsUiState.achievements`. Run full unit suite — expect green.

  > **Rollback note**: `git revert <T016b-sha>` restores the `achievements` field without affecting T016a’s screen cleanup.

- [ ] T017 [US2] Verify acceptance: run `AchievementsScreenTest` instrumented test — expect **GREEN**. Run `.\gradlew :app:testDebugUnitTest` — expect green. Acceptance: SC-002, FR-004–FR-008a satisfied.

## Phase 4 — User Story 3: Default Task Times (P2)

> Depends on Phase 1 complete (defaultEndTime, endTimeForDate exist in DateUtils).
> T018 (unit test) and T019 (repository fix) are parallel — different files.

**Story goal**: New Task dialog start field shows exact current clock time; end field shows 11:59 PM. Recurring task daily refresh still uses 12:01 AM start independently.

**Independent test criteria**: Open New Task dialog at 3:15 PM without changing anything → start = 3:15 PM, end = 11:59 PM → save task → `startDate` in DB matches creation time (not midnight). Check `refreshRecurringTasks()` output → `startDate` = 12:01 AM that day.

- [X] T018 [P] [US3] Write failing `AddTaskDefaultTimeTest` in `app/src/test/java/com/flow/data/repository/AddTaskDefaultTimeTest.kt` — (a) call `addTask()` with `isRecurring=true` and `startDate=2:37 PM millis`; assert stored `startDate` equals the passed-in 2:37 PM value (not overwritten to midnight+1 min); (b) call `refreshRecurringTasks()` and assert the refreshed task's `startDate` hour=0, minute=1; (c) call `addTask()` with `isRecurring=false` and any `startDate`; assert stored value is unchanged. Run test — expect **RED** (addTask currently overrides startDate for recurring).

- [X] T019 [P] [US3] Fix `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` — in `addTask()`, remove the `if (isRecurring) todayMidnight + 60_000L` override block (plan Fix-003 Step 2); replace with timezone-safe pass-through that preserves the caller's `startDate` and `dueDate` values. `refreshRecurringTasks()` is **not touched**. Run `T018` test — expect **GREEN**. Run `TaskRepositoryImplTest` — expect green. Run full unit suite — expect green.

- [X] T020 [US3] Fix `AddTaskDialog` in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` — (a) add `val dialogOpenTime = remember { System.currentTimeMillis() }` and use it as the initial value for `startDate` (replacing the inline `System.currentTimeMillis()` call so the time is captured once at dialog open, not re-sampled); (b) change `dueDate` initial state from `null` to `remember { defaultEndTime() }`; (c) in the target date picker `confirmButton` handler, set `dueDate = endTimeForDate(utcDateToLocalMidnight(it))` so changing the date keeps the 23:59 time-component; (d) in the start date picker `confirmButton` handler, set `startDate = mergeDateTime(selectedDateMillis, dialogOpenTime)` — where `mergeDateTime` keeps the time-of-day from `dialogOpenTime` and the calendar date from the picker selection — satisfying the spec Edge Case "if the user changes the start date, the time component MUST stay at the exact clock time captured when the dialog opened." Implement `internal fun mergeDateTime(datePart: Long, timePart: Long): Long` as a pure function in `DateUtils.kt`. Per contract C-007. Run full unit suite — expect green.

- [X] T021-test [US3] Write failing `EditTaskDialogDefaultTimeTest` in `app/src/test/java/com/flow/presentation/home/EditTaskDialogDefaultTimeTest.kt` (or extend `AddTaskDefaultTimeTest` as a new test method) — (a) simulate opening `EditTaskDialog` with a task whose `dueDate == null`; assert the dialog’s initialised `dueDate` state value has `Calendar.HOUR_OF_DAY == 23` and `MINUTE == 59`; (b) simulate opening with a task whose `startDate == 0L`; assert the initialised `startDate` state value is within 5 seconds of `System.currentTimeMillis()` (i.e., not epoch zero, not 12:01 AM). Run test — expect **RED** (EditTaskDialog has not been updated yet).

- [X] T021 [US3] Fix `EditTaskDialog` in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` — when the task’s `dueDate == null` on dialog open, initialise the `dueDate` state to `defaultEndTime()` instead of `null`; when task’s `startDate` equals epoch zero or was never explicitly set, default to `System.currentTimeMillis()`. Apply same `endTimeForDate()` pattern in the date picker confirm handler for the edit dialog’s target date. Run `T021-test` — expect **GREEN**. Run full unit suite — expect green.

- [X] T022 [US3] Verify acceptance: run `.\.gradlew :app:testDebugUnitTest` — all unit tests green including `AddTaskDefaultTimeTest`, `DateUtilsDefaultTimeTest`, `HomeViewModelTest`, `TaskRepositoryImplTest`. Acceptance: SC-003, FR-009–FR-012 satisfied.

---

## Phase 5 — User Story 4: Recurring Schedule Options (P3)

> Depends on Phase 1 complete (AchievementVisibility available — no hard dependency, but Phase 1 should already be done).
> T023 (test) and T024 (composable) can be written in parallel — different logical concerns in same file, but write test first per TDD.

**Story goal**: When "Track Streak" is checked in the task dialog, 7 day-of-week chips appear (all selected by default) with "Weekdays" and "Weekends" quick-selects. Schedule persists via existing `scheduleMask` field. Tasks not scheduled for today are skipped on refresh.

**Independent test criteria**: Create recurring task with Mon/Wed/Fri chips selected → on Tuesday task absent from Today list, streak unbroken → on Wednesday task resets to TODO.

- [X] T023 [US4] Write failing `ScheduleDaySelectorTest` in `app/src/test/java/com/flow/presentation/home/ScheduleDaySelectorTest.kt` — tests target the **pure helper functions** to be extracted in T024 (not the composable, which cannot run in JVM unit tests): (a) `applySchedulePreset(ALL_DAYS)` returns 127; (b) `applySchedulePreset(WEEKDAYS)` returns 31 (bits 0–4); (c) `applySchedulePreset(WEEKENDS)` returns 96 (bits 5–6); (d) `toggleDayBit(127, 0)` returns 126 (Monday off); (e) `isScheduleMaskValid(0)` returns false; (f) `isScheduleMaskValid(null)` returns true (null treated as 127, FR-018). Run test — expect **RED** (functions do not exist yet).

- [X] T024 [US4] Implement the schedule day-selector in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` in two steps: **Step 1 — extract pure functions** (makes T023 green): add `internal fun applySchedulePreset(preset: SchedulePreset): Int` (where `SchedulePreset` is an enum with `ALL_DAYS=127`, `WEEKDAYS=31`, `WEEKENDS=96`), `internal fun toggleDayBit(mask: Int, bit: Int): Int` (XOR toggle), and `internal fun isScheduleMaskValid(mask: Int?): Boolean` — all `internal` for JVM unit-test access. Run `T023` test — expect **GREEN**. **Step 2 — composable**: implement `internal fun ScheduleDaySelector(mask: Int, onMaskChange: (Int) -> Unit, isError: Boolean)` per contract C-006 — Row of `TextButton`s calling `applySchedulePreset()`; `FlowRow` of 7 `FilterChip`s calling `toggleDayBit()`; error `Text` visible when `isError=true`. The composable contains no logic beyond delegation to the extracted functions. Run full unit suite — expect green.

- [X] T024B [US4] **FR-016 — Schedule Filtering in Daily Refresh**: Write failing `RefreshScheduleFilterTest` in `app/src/test/java/com/flow/data/repository/RefreshScheduleFilterTest.kt` — (a) insert a recurring task with `scheduleMask=62` (all bits set except bit 6 = Sunday); call `refreshRecurringTasks()` on a Sunday; assert that task's state is **not** changed to TODO; (b) same task on a Monday (bit 0 set in mask=62); assert the task **is** reset to TODO; (c) task with `scheduleMask=null`; call refresh; assert reset to TODO (null = every day, FR-018). Run test — expect **RED** (refresh has no schedule check yet). Then edit `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` — in `refreshRecurringTasks()`, before resetting each recurring task to TODO, call `DayMask.isScheduledToday(task.scheduleMask)` (or equivalent bit-check using `applySchedulePreset` / `DayMask` helpers already in `domain/streak/DayMask.kt`); skip the reset if it returns false. Run `RefreshScheduleFilterTest` — expect **GREEN**. Run full unit suite including `RefreshScheduleFilterTest`, `AddTaskDefaultTimeTest` — expect green. Acceptance: FR-016 satisfied.

- [X] T025 [US4] Wire `ScheduleDaySelector` into `AddTaskDialog` in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` — (a) change `scheduleMask` initialisation from `null` to `127` so all chips appear selected when dialog opens with `isRecurring=true`; (b) show `ScheduleDaySelector` below the "Track Streak" checkbox when `isRecurring == true`; (c) do **not** compute `isError` inline in the dialog — instead, add a `scheduleMaskError: Boolean` field to `HomeUiState` in `HomeViewModel.kt`; the dialog reads it from the UI state and passes it as `isError` to `ScheduleDaySelector`; (d) keep `Add Task` button enabled but show the error text via `isError=true` — the ViewModel blocks the actual save in T027 (this respects CO-001: validation lives at ViewModel layer, not UI layer). Run full unit suite — expect green.

- [X] T026-test [US4] Write failing `EditTaskDialogScheduleTest` in `app/src/test/java/com/flow/presentation/home/EditTaskDialogScheduleTest.kt` — (a) simulate loading an existing recurring task with `scheduleMask == null` into `EditTaskDialog`; assert the chip initialisation state is `127` (all 7 days selected, FR-018 backwards-compat); (b) simulate changing the schedule to Mon+Wed (`scheduleMask = 5`, bits 0+2) and calling the ViewModel save; assert `repository.updateTask()` is called with `scheduleMask = 5`; (c) assert that no `TaskCompletionLog` rows are deleted or modified during the save path (use a spy/fake repository to verify `deleteCompletionLog` is never called). Run test — expect **RED** (EditTaskDialog has no schedule selector yet).

- [X] T026 [US4] Wire `ScheduleDaySelector` into `EditTaskDialog` in `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` — when editing a recurring task whose `scheduleMask == null`, initialise chip state to `127` (FR-018 backwards-compat); show selector when `isRecurring == true`; apply same `isError` logic. Editing the schedule MUST NOT clear or modify any `TaskCompletionLog` rows. Run `T026-test` — expect **GREEN**. Run full unit suite — expect green.

- [X] T027 [US4] Add `scheduleMask == 0` validation to `HomeViewModel` in `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt` — (a) when `isRecurring == true && scheduleMask == 0`, set `scheduleMaskError = true` in `HomeUiState` (from T025) and return without calling `repository.addTask()` or `repository.updateTask()`; (b) emit a `Snackbar` or `ValidationError` event. Per CO-001. Add two new test cases to `app/src/test/java/com/flow/presentation/home/HomeViewModelTest.kt` — (i) `addTask(isRecurring=true, scheduleMask=0)` asserts: `repository.addTask()` is **never called** and `uiState.scheduleMaskError == true`; (ii) `addTask(isRecurring=true, scheduleMask=127)` asserts: `repository.addTask()` **is called** and `uiState.scheduleMaskError == false`. Run full unit suite including `HomeViewModelTest` — expect green. Acceptance: SC-004, FR-013–FR-018 satisfied.

---

## Final Phase — Regression Gate

> Run only after all story phases complete. Requires connected device.

- [X] T028 Run full unit test suite: `.\gradlew :app:testDebugUnitTest` — all tests green. Failing tests block completion.

- [X] T029 Confirm device connected (`adb devices` — must show device, not empty; if none found, stop and connect before proceeding). Run `.\gradlew :app:connectedDebugAndroidTest` — all instrumented tests green, including `EmojiRenderTest`, `AchievementsScreenTest`, `TaskPersistenceTest`, `HistoryScreenTest`, `AnalyticsPeriodSelectorTest`. Acceptance: SC-005 satisfied — zero regressions.

---

## Dependencies

```
Phase 1 (T001–T006)
  └─ US1 Phase 2 (T007–T009)   ← independent of Phase 1 content; parallel is safe
  └─ US2 Phase 3 (T010–T017)   ← requires T001–T006 (AchievementMeta, AchievementsUiState)
  └─ US3 Phase 4 (T018–T022)   ← requires T005 (defaultEndTime in DateUtils)
  └─ US4 Phase 5 (T023–T027)   ← no Phase 1 dependency; can start any time after plan
       └─ Final (T028–T029)     ← requires ALL phases complete
```

Within US2:
```
T010, T011, T012  ← parallel (different files)
T013              ← requires T010, T011, T012
T014              ← requires T012, T013
T015              ← requires T014
T016a             ← requires T004 (AchievementMeta must exist before removing functions
                     from AnalyticsScreen.kt)
T016b             ← requires T016a (AnalyticsUiState cleanup depends on T016a proving
                     the field is unused)
T017 (gate)       ← requires T015, T016b
```

Within US3:
```
T018, T019    ← parallel (test + impl)
T020          ← requires T005 (defaultEndTime) + T019 (addTask fix)
T021-test     ← parallel with T020 (different dialog, new test file)
T021          ← requires T021-test (TDD red) + T005 (defaultEndTime)
T022 (gate)   ← requires T021
```

Within US4:
```
T023        ← write first (TDD red)
T024        ← requires T023
T024B       ← requires T024 (refresh filtering: TaskRepositoryImpl + RefreshScheduleFilterTest)
T025        ← requires T024B (ScheduleDaySelector wired into AddTaskDialog)
T026-test   ← write before T026 (TDD red); can be parallel with T025
T026        ← requires T026-test + T025
T027        ← requires T026
```

---

## Parallel Execution Opportunities

| Session | Parallel tasks | Notes |
|---------|---------------|-------|
| Start | T001, T002, T003 | All new files, no dependencies between them |
| After T001–T003 | T004, T005, T006 | All new files; T004 makes T001 green, T005 makes T002 green |
| After Phase 1 | T007 (US1) + T010 (US2 test) + T018 (US3 test) | Different files, different stories |
| US2 core | T011, T012 | ViewModel + Routes — different files |
| US3 mid | T021-test, T020 | Different dialogs/files; both can be written in parallel |

---

## Acceptance Criteria Summary

| SC | Task(s) | Acceptance |
|----|--------|-----------|
| SC-001 Emoji renders correctly | T007, T008 | `EmojiRenderTest` green on device |
| SC-002 Achievements screen with descriptions | T010–T016b, T017 | `AchievementsScreenTest` green; Analytics has no achievements |
| SC-003 Default times correct | T018–T021-test, T021, T022 | `AddTaskDefaultTimeTest` + `DateUtilsDefaultTimeTest` + `EditTaskDialogDefaultTimeTest` green; startDate = creation time, end = 23:59 |
| SC-004 Schedule filtering works | T023–T026-test, T026, T027, T024B | `ScheduleDaySelectorTest` + `RefreshScheduleFilterTest` + `EditTaskDialogScheduleTest` green; Mon/Wed/Fri task absent on Tuesday |
| SC-005 Zero regressions | T028, T029 | Full test suite green on device |
| SC-005 Zero regressions | T028, T029 | Full test suite green on device |
