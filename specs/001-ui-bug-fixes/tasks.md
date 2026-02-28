# Tasks: UI Bug Fixes (001)

**Input**: Design documents from `specs/001-ui-bug-fixes/`
**Prerequisites**: [plan.md](plan.md) Â· [spec.md](spec.md) Â· [research.md](research.md) Â· [data-model.md](data-model.md) Â· [contracts/behavioral-contracts.md](contracts/behavioral-contracts.md)

**Total tasks**: 48 | **Parallelizable**: 30 | **User stories covered**: 11 (US1â€“US11)

**Test tiers** (all mandatory per constitution):
- Tier 1 â€” Unit tests in `app/src/test/java/com/flow/`
- Tier 2 â€” Instrumented / DAO / integration in `app/src/androidTest/java/com/flow/`
- Tier 3 â€” On-device E2E via `.\gradlew connectedDebugAndroidTest`

---

## Phase 1: Setup

**Purpose**: Baseline compile gate, static resource addition, and constitution ratification â€” all must complete before implementation begins.

> **I1 note**: Plan Phase A zero-risk static fixes (Theme, emoji, icon) have zero code dependencies and can be worked during this phase alongside Phase 12â€“13 tasks.

- [X] T001 Confirm baseline debug build compiles: run `.\.gradlew assembleDebug` and verify BUILD SUCCESSFUL; record any pre-existing errors before making any changes
- [X] T002 [P] Add `timer_complete.ogg` (public-domain chime, â‰¤ 3 seconds) to `app/src/main/res/raw/timer_complete.ogg`
- [X] T009 [P] Append Principle VII (Emoji Non-Negotiable) to `.specify/memory/constitution.md` after Principle VI; bump version header from 1.3.2 to 1.4.0 â€” principle text: "Unicode emoji MUST always render as their intended visual symbols. Any code change that processes, serializes, or transmits emoji string content MUST include an automated test asserting correct emoji rendering. Empty-string fallbacks for emoji are prohibited." *(C2 fix: NC-001 must be ratified before any implementation begins; moved here from Phase 3.)*

**Checkpoint**: Build is green; audio resource is in place; constitution Principle VII ratified at v1.4.0.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: New DAO range query required by US1, US2, and US3. Must be complete before those stories start.

**âš ï¸ CRITICAL**: Phases 4, 5, and 6 cannot begin until this phase is complete.

- [X] T003 Add `@Query("SELECT * FROM tasks WHERE dueDate BETWEEN :start AND :end") fun getTasksDueInRange(start: Long, end: Long): Flow<List<TaskEntity>>` to `app/src/main/java/com/flow/data/local/TaskDao.kt`
- [X] T004 Run `.\gradlew compileDebugAndroidTestKotlin` (compile gate â€” must pass before any Tier 2 tests are written)
- [X] T005 [P] Write Tier 2 DAO test `getTasksDueInRange_returnsTaskWithDueDateAt1159pm` in `app/src/androidTest/java/com/flow/data/local/TaskDaoTest.kt` â€” insert a task with dueDate = today 23:59, query range (today 00:00, today 23:59), assert task is returned
- [X] T006 [P] Write Tier 2 DAO test `getTasksDueInRange_excludesTaskDueTomorrow` in `app/src/androidTest/java/com/flow/data/local/TaskDaoTest.kt` â€” insert task with dueDate = tomorrow 00:00, assert NOT returned in today's range

**Checkpoint**: `getTasksDueInRange` query is live and tested at DAO tier.

---

## Phase 3: User Story 8 â€” Emoji Display Correctness (Priority: P1)

**US8 Goal**: All emoji characters render as their intended visual symbols. The regression in `achievementEmoji()` is fixed and the constitution is updated to prevent future regressions.

**Independent Test**: Open Analytics screen and verify all achievement badge emojis render as visible emoji symbols, not empty strings or replacement characters.

### Tests for US8 (Tier 1 â€” write first; must FAIL before T008, PASS after)

- [X] T007 [P] [US8] Write Tier 1 unit test `achievementEmoji_returnsNonEmptyUnicodeForAllSixTypes` in `app/src/test/java/com/flow/presentation/analytics/AnalyticsHelpersTest.kt` â€” call `achievementEmoji()` for each of the 6 achievement types and assert the returned string is non-empty and contains a valid emoji code point

### Implementation for US8

- [X] T008 [P] [US8] Restore 6 emoji string literals in `achievementEmoji()` in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt`: ðŸŒ± `"\uD83C\uDF31"`, ðŸŒ³ `"\uD83C\uDF33"`, ðŸ† `"\uD83C\uDFC6"`, â±ï¸ `"\u23F1\uFE0F"`, âš¡ `"\u26A1"`, ðŸŽ¯ `"\uD83C\uDFAF"` (replace the empty-string returns for all 6 achievement type branches)

> Constitution update T009 has been moved to Phase 1 (prerequisite). Verify T009 is âœ… before starting T008.

**Checkpoint**: All 6 achievement emojis render correctly; Principle VII already in constitution (T009 done in Phase 1).

---

## Phase 4: User Story 1 â€” Recurring Task Time Boundaries (Priority: P1)

**US1 Goal**: Newly created recurring tasks default to 12:01 AM start and 11:59 PM end, eliminating the midnight-to-midnight zero-duration bug.

**Independent Test**: Create a new recurring task, inspect it in the DB or task detail screen, and verify start = today 00:01 and dueDate = today 23:59.

### Tests for US1 (Tier 1 â€” write first; must FAIL before T012â€“T013, PASS after)

- [X] T010 [P] [US1] Write Tier 1 unit test `addRecurringTask_startsAt1201am_endsAt1159pm` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” invoke `addTask()` with `isRecurring=true`, assert saved entity has `startDate = todayMidnight + 60_000L` and `dueDate = todayMidnight + 86_340_000L` (23:59:00)
- [X] T011 [P] [US1] Write Tier 1 unit test `refreshRecurringTasks_appliesCorrectTimeBoundaries` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” invoke `refreshRecurringTasks()`, assert returned/updated tasks have start = 12:01 AM and dueDate = 11:59 PM

### Implementation for US1

- [X] T012 [US1] Update `addTask()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” when `isRecurring == true`, override `startDate` to `normaliseToMidnight(now) + 60_000L` (12:01 AM) and `dueDate` to `normaliseToMidnight(now) + 86_340_000L` (11:59 PM)
- [X] T013 [US1] Update `refreshRecurringTasks()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” apply the same 12:01 AM / 11:59 PM boundary logic used in T012 when building the refreshed task entity

**Checkpoint**: US1 unit tests pass; recurring tasks created at correct time boundaries.

---

## Phase 5: User Story 2 â€” Non-Recurring Task End Date Default (Priority: P1)

**US2 Goal**: Non-recurring tasks created without an explicit dueDate default to today at 11:59 PM instead of null.

**Independent Test**: Create a non-recurring task without specifying an end date; verify dueDate is set to today 23:59.

### Tests for US2 (Tier 1 â€” write first; must FAIL before T015, PASS after)

- [X] T014 [P] [US2] Write Tier 1 unit test `addNonRecurringTask_nullDueDate_defaultsToEndOfToday` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” invoke `addTask()` with `isRecurring=false, dueDate=null`, assert saved entity has `dueDate = normaliseToMidnight(now) + 86_340_000L`

### Implementation for US2

- [X] T015 [US2] Update `addTask()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” when `isRecurring == false && dueDate == null`, set `dueDate = normaliseToMidnight(System.currentTimeMillis()) + 86_340_000L`; start date behavior remains unchanged

**Checkpoint**: US2 unit test passes; non-recurring tasks always have a non-null dueDate.

---

## Phase 6: User Story 3 â€” Today's Progress Percentage Accuracy (Priority: P1)

**US3 Goal**: The home screen progress bar counts all tasks (recurring + non-recurring) whose dueDate falls within today's date range (inclusive of 11:59 PM), not only those whose dueDate equals exactly today's midnight.

**Depends on**: T003 (Phase 2 `getTasksDueInRange` DAO query)

**Independent Test**: Add 2 recurring tasks + 2 non-recurring tasks all ending today; complete 2; verify progress bar shows 50%.

### Tests for US3 (Tier 1 â€” write first; must FAIL before T018, PASS after)

- [X] T016 [P] [US3] Write Tier 1 unit test `getTodayProgress_includesRecurringTasksEndingAt1159pm` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” mock DAO to return tasks with dueDate = today 23:59, assert they appear in progress count
- [X] T017 [P] [US3] Write Tier 2 instrumented test `progress_countsMixOfRecurringAndNonRecurringEndingToday` in `app/src/androidTest/java/com/flow/data/repository/RepositoryIntegrityTest.kt` â€” insert 2 recurring (dueDate 23:59) + 2 non-recurring (dueDate 23:59), complete 2, assert progress = 2/4

### Implementation for US3

- [X] T018 [US3] Update `getTodayProgress()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” replace `getTasksDueOn(todayMidnight)` with `getTasksDueInRange(todayMidnight, todayMidnight + 86_399_999L)` using the query added in T003
- [X] T046 [P] [US3] Write Tier 2 instrumented test `existingMidnightTask_afterRepositoryChange_fieldValuesUnchanged` in `app/src/androidTest/java/com/flow/data/repository/RepositoryIntegrityTest.kt` â€” seed DB with a `TaskEntity` having `startDate = todayMidnight` and `dueDate = todayMidnight`, perform a readâ†’write cycle via `updateTask()`, assert both fields remain unchanged; guards spec Assumption 2 that existing rows are never silently back-filled *(covers finding U3)*

**Checkpoint**: US3 unit and instrumented tests pass; progress bar counts all today-tasks correctly; existing midnight-timed rows are not altered by the fix.

---

## Phase 7: User Story 4 â€” Task History Deduplication (Priority: P2)

**US4 Goal**: History holds exactly one row per task (upserted by task ID). Moving a task back to non-completed removes it from history. The date filter row updates immediately on any completion event.

**Independent Test**: Move task: To Do â†’ In Progress â†’ Completed â†’ In Progress; verify it disappears from history. Re-complete it; verify it reappears with the new date and filter chip updates without screen reload.

### Tests for US4 (Tier 1 â€” write first; must FAIL before T023â€“T024, PASS after)

- [X] T019 [P] [US4] Write Tier 1 unit test `updateTask_whenStatusNotCompleted_clearsCompletionTimestamp` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” call `updateTask()` with a task in IN_PROGRESS state; assert that the saved entity has `completionTimestamp = null`
- [X] T020 [P] [US4] Write Tier 1 unit test `updateTaskStatus_completedToInProgress_isAllowedAndClearsTimestamp` in `app/src/test/java/com/flow/data/repository/TaskRepositoryImplTest.kt` â€” call `updateTaskStatus(COMPLETED -> IN_PROGRESS)`; assert it succeeds and `completionTimestamp` is null in saved entity

### Tests for US4 (Tier 2 â€” instrumented)

- [X] T021 [P] [US4] Write Tier 2 instrumented test `nonRecurringTask_movedToInProgress_disappearsFromHistory` in `app/src/androidTest/java/com/flow/data/repository/RepositoryIntegrityTest.kt` â€” complete a task, move it back to IN_PROGRESS, query history, assert zero rows for that task ID
- [X] T022 [P] [US4] Write Tier 2 instrumented test `recurringTask_recompletedSameDay_onlyOneLogEntry` in `app/src/androidTest/java/com/flow/data/repository/RepositoryIntegrityTest.kt` â€” complete task, complete again (upsert), query task_logs for that taskId, assert exactly one row
- [X] T047 [P] [US4] Write Tier 2 Compose UI test `globalHistoryScreen_filterChipRow_updatesReactivelyOnNewCompletion` in `app/src/androidTest/java/com/flow/presentation/history/GlobalHistoryScreenTest.kt` â€” set up `GlobalHistoryScreen` with a test `StateFlow`; emit a newly completed task; assert a date chip for that completion date appears in the filter row WITHOUT any manual screen navigation or reload; covers FR-008b reactive update requirement *(covers finding G2)*

### Implementation for US4

- [X] T023 [US4] Fix `updateTask()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” before upserting, if `task.status != COMPLETED`, set `completionTimestamp = null` in the entity; do NOT carry forward the existing timestamp from DB when status is non-completed
- [X] T024 [US4] Fix `updateTaskStatus()` in `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` â€” add `COMPLETED -> IN_PROGRESS` (clears `completionTimestamp`) and confirm `COMPLETED -> TODO` (also clears `completionTimestamp`) to the valid-transition table

**Checkpoint**: Zero duplicate history rows in all test scenarios; COMPLETEDâ†’IN_PROGRESS transition works.

---

## Phase 8: User Story 5 â€” Analytics Completed Count Accuracy (Priority: P2)

**US5 Goal**: The "Total completed" chip on the Analytics Page 0 and the heat map graph draw from the same computed value (`heatMapData.values.sum()`), making divergence structurally impossible.

**Independent Test**: Complete exactly 5 tasks, view analytics; confirm summary text shows 5 and graph bars sum to 5.

### Tests for US5 (Tier 1 â€” write first; must FAIL before T026, PASS after)

- [X] T025 [P] [US5] Write Tier 1 unit test `totalChipValue_equalsHeatMapSum_forCurrentYearPeriod` in `app/src/test/java/com/flow/presentation/analytics/AnalyticsViewModelTest.kt` â€” seed ViewModel with known heatMapData, assert the value exposed for the "Total" chip equals `heatMapData.values.sum()`

### Implementation for US5

- [X] T026 [US5] Update the Page 0 "Total" chip binding in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt` â€” replace `uiState.totalCompleted` with `uiState.heatMapData.values.sum()` so both text label and graph draw from the same source

**Checkpoint**: Total chip and graph can never diverge; unit test passes.

---

## Phase 9: User Story 6 â€” Heat Map Default Date Range (Priority: P2)

**US6 Goal**: `ContributionHeatmap` accepts explicit `startMs` / `endMs` parameters and renders only the weeks that fall within the supplied range; the hardcoded 52-week rolling window is eliminated. Default view: Jan 1 â†’ today.

**Independent Test**: Open heat map in February; verify only Januaryâ€“February columns visible. Select "Last Year"; verify 12 months of prior year.

### Tests for US6 (Tier 1 â€” write first; must FAIL before T028â€“T029, PASS after)

- [X] T027 [P] [US6] Write Tier 1 unit test `contributionHeatmap_inFebruary_showsOnlyJanAndFeb` in `app/src/test/java/com/flow/presentation/analytics/ContributionHeatmapTest.kt` â€” pass `startMs = jan1Millis`, `endMs = feb28Millis`; assert rendered week count = weeks from Jan 1 to last day of Feb

### Implementation for US6

- [X] T048 [US6] Extend the `AnalyticsUiState` data class (locate in or near `app/src/main/java/com/flow/presentation/analytics/AnalyticsViewModel.kt`) with two new fields: `heatMapStartMs: Long` and `heatMapEndMs: Long`; compute them in the ViewModel from the selected period (`CurrentYear` â†’ Jan 1 current year millis to `min(Dec 31 current year millis, endOfTodayMs)`; `LastYear` â†’ Jan 1 prior year to Dec 31 prior year millis); expose via the existing StateFlow *(prerequisite for T028 and T029; covers finding U1)*
- [X] T028 [US6] Refactor `ContributionHeatmap()` signature in `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt` â€” add `startMs: Long` and `endMs: Long` parameters; replace the hardcoded `weeksToShow = 52` calculation with `((endMs - startMs) / (7L * 24 * 60 * 60 * 1000)).toInt() + 1` weeks, anchored at `startMs`
- [X] T029 [US6] Update the `ContributionHeatmap` call site in the general analytics section of `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt` â€” pass `startMs = currentPeriod.startMs` (Jan 1 for `CurrentYear`, Jan 1 of prior year for `LastYear`) and `endMs = min(currentPeriod.endMs, todayEndMs)`

**Checkpoint**: Heat map renders only Janâ€“today in default view; no future empty months.

---

## Phase 10: User Story 11 â€” Recurring Task Streak Heat Map Visual (Priority: P2)

**US11 Goal**: The streak heat map inside a recurring task shows Janâ€“current month only (not 52 rolling weeks), uses a darker neon green for completed cells, and matches general heat map layout. **Depends on T028** (Phase 9 refactor).

**Independent Test**: Complete a recurring task entry; open its streak heat map in February; verify only Janâ€“Feb shown and cells are visibly darker neon green.

### Tests for US11 (Tier 1 â€” write first; must FAIL before T031â€“T032, PASS after)

- [X] T030 [P] [US11] Write Tier 1 unit test `streakHeatmap_inFebruary_showsOnlyJanAndFeb` in `app/src/test/java/com/flow/presentation/analytics/ContributionHeatmapTest.kt` â€” same helper as US6 test; seed with streak data; assert week range ends at current month boundary. Also add assertion `streakHeatmap_cellDimensions_matchAnalyticsHeatmap` â€” instantiate `ContributionHeatmap` with analytics data and with streak data using identical `startMs`/`endMs` parameters; assert the rendered week count and column count are equal to verify FR-022 layout parity *(covers finding G3)*

### Implementation for US11

- [X] T031 [US11] Update the `ContributionHeatmap` call site in `app/src/main/java/com/flow/presentation/history/TaskHistoryScreen.kt` â€” pass `startMs = jan1OfCurrentYearMs` and `endMs = endOfTodayMs`; remove any hardcoded `weeksToShow` overrides; **depends on T028**
- [X] T032 [P] [US11] Before coding: grep for `ContributionHeatmap` in `TaskHistoryScreen.kt` to determine if cell color is a parameter or hardcoded â€” if it is a parameter, update the call site in `TaskHistoryScreen.kt` as part of this task; if hardcoded inside the composable, update it there only. Change filled-cell color to a darker neon green achieving â‰¥ 3:1 WCAG AA contrast ratio against `DarkBackground` (#121212); e.g., `Color(0xFF00C853)` â†’ `Color(0xFF00891A)` achieves 4.6:1 â€” verify with a contrast checker before committing *(U2: color ownership resolved before coding; B1: measurable WCAG AA criterion replaces "confirmed against UI")*

**Checkpoint**: Streak heat map shows Janâ€“today only; cells are visibly darker neon green.

---

## Phase 11: User Story 7 â€” Focus Timer Live Settings Update and Completion Sound (Priority: P2)

**US7 Goal**: Timer reacts to settings changes immediately (no restart required). A completion chime plays via the notification audio stream when the timer reaches zero.

**Independent Test**: Change timer to 10 min in settings; navigate directly to timer screen; verify 10:00 shown. Let a (shortened) test timer run to zero; verify sound plays.

### Tests for US7 (Tier 1 â€” write first; must FAIL before T034â€“T035, PASS after)

- [X] T033 [P] [US7] Write Tier 1 unit test `settingsChange_whenTimerIsIdle_updatesDisplayedDuration` in `app/src/test/java/com/flow/presentation/timer/TimerViewModelTest.kt` â€” emit a new `defaultTimerMinutes` value from a fake SettingsManager Flow; assert `uiState.remainingSeconds` updates to match new value when timer is not running

### Implementation for US7

- [X] T034 [US7] Fix `TimerViewModel.kt` at `app/src/main/java/com/flow/presentation/timer/TimerViewModel.kt` â€” in the `init` block, replace the `firstOrNull()` one-shot read of `settingsManager.defaultTimerMinutes` with a `viewModelScope.launch { settingsManager.defaultTimerMinutes.collect { newMinutes -> if (!isRunning && !isPaused) { updateTimerDuration(newMinutes) } } }` coroutine
- [X] T035 [US7] Add completion sound in `app/src/main/java/com/flow/presentation/timer/TimerPanel.kt` â€” add `LaunchedEffect(uiState.isFinished) { if (uiState.isFinished) { val mp = MediaPlayer.create(context, R.raw.timer_complete); mp.setAudioAttributes(AudioAttributes.Builder().setUsage(USAGE_NOTIFICATION).build()); mp.setOnCompletionListener { it.release() }; mp.start() } }` (depends on T002 for the OGG resource)
- [X] T049 [P] [US7] Write Tier 1 unit test `timerCompletion_usesNotificationAudioStream_forDNDSuppression` in `app/src/test/java/com/flow/presentation/timer/TimerPanelTest.kt` â€” capture the `AudioAttributes` instance passed to `MediaPlayer` (via a fake/spy) and assert `usage == AudioAttributes.USAGE_NOTIFICATION`; this attribute is what grants DND and silent-mode suppression and satisfies FR-015 without requiring a physical silent-mode device test *(covers finding G1; FR-015)*

**Checkpoint**: Settings change reflects in timer without restart; chime plays on completion; `USAGE_NOTIFICATION` stream usage verified by unit test (satisfies FR-015 DND suppression).

---

## Phase 12: User Story 9 â€” Background Color Refinement (Priority: P3)

**US9 Goal**: `FlowTheme(dynamicColor = false)` prevents Android 12+ from overriding the defined `DarkBackground = Color(0xFF121212)`.

**Independent Test**: Open any primary screen on an Android 12+ device; verify background is visibly softer dark (#121212) rather than pure black or a dynamic system color.

### Tests for US9 (Tier 1 â€” write first; must FAIL before T037, PASS after)

- [X] T036 [P] [US9] Write Tier 1 unit/Compose test `theme_dynamicColorDisabled_backgroundIsCanonicalDarkSurface` in `app/src/test/java/com/flow/ui/theme/ThemeTest.kt` â€” construct `FlowTheme(dynamicColor = false, darkTheme = true)` (the new explicit default) and assert `MaterialTheme.colorScheme.background == Color(0xFF121212)`; passing `dynamicColor = false` explicitly makes the assertion valid on ANY API level without a device-level guard; optionally add a second test variant with `assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)` if also testing the API 31+ override path *(B2: removes vague platform-condition qualifier; test is now unconditionally valid)*

### Implementation for US9

- [X] T037 [P] [US9] Update `FlowTheme()` default parameter in `app/src/main/java/com/flow/ui/theme/Theme.kt` â€” change `dynamicColor: Boolean = true` to `dynamicColor: Boolean = false`

**Checkpoint**: Background is #121212 on all API levels; dynamic color no longer overrides it.

---

## Phase 13: User Story 10 â€” App Icon Centering Fix (Priority: P3)

**US10 Goal**: All artwork in the launcher icon foreground drawable is within the 66dp safe zone of the 108dp adaptive icon canvas so nothing is clipped on any launcher.

**Independent Test**: Install the build and inspect the launcher icon; verify text and wave graphics are fully visible and centered at all icon sizes.

### Tests for US10

- [X] T038 [P] [US10] Write Tier 2 instrumented test `launcherIcon_contentWithinSafeZone` in `app/src/androidTest/java/com/flow/IconSafeZoneTest.kt` â€” inflate `ic_launcher_foreground` as a `VectorDrawable` and assert its intrinsic width and height fit within the 66dp safe zone of the 108dp adaptive icon canvas. If the `VectorDrawable` API does not expose per-path bounds sufficient for this assertion, add an `@Ignore("T050: automate when tooling supports path-level bounds")` stub documenting the assertion intent AND create a separate blocking follow-up task (T050) to automate it â€” do NOT substitute PR description text for an automated test; do NOT mark T038 complete without either a passing assertion or the tracked T050 stub *(C1 fix: removes constitution Gate 5 violation; "document in PR" substitution prohibited)*

### Implementation for US10

- [X] T039 [P] [US10] Update `ic_launcher_foreground.xml` in all `app/src/main/res/mipmap-*/` density folders â€” add padding so all path content is within the central 66dp safe zone of the 108dp canvas (effective padding â‰¥ 21dp on each side); verify with Android Studio's Adaptive Icon preview tool

**Checkpoint**: Launcher icon fully contained; no visual clipping at any supported density.

---

## Phase 14: Polish & Cross-Cutting Concerns

**Purpose**: Final compile gate, full regression verification, security check, and E2E on-device validation.

- [X] T040 Run `.\.gradlew test` â€” all new unit tests (T007, T010, T011, T014, T016, T025, T027, T030, T033, T036, T049) MUST PASS; zero regressions in existing suite; document final pass/fail counts in PR description *(A1 fix: merged with former T045; `gradlew test` is a superset of `testDebugUnitTest` â€” running both was redundant)*
- [X] T041 Run `.\gradlew compileDebugAndroidTestKotlin` â€” final compile gate; all instrumented test sources compile cleanly
- [X] T042 [P] Security check â€” confirm: (a) no credentials or PII in any tracked file; (b) all new Room `@Query` methods use `:name` bind parameters only; (c) `timer_complete.ogg` contains no embedded metadata with personal data; (d) `gradle.properties` / `local.properties` not tracked for new secrets
- [X] T043 [P] Run `.\gradlew connectedDebugAndroidTest` on physical or emulator device — all Tier 2 instrumented tests pass (TaskDaoTest, RepositoryIntegrityTest, GlobalHistoryScreenTest, and any new UI tests)
- [X] T044 Tier 3 on-device E2E walkthrough — following [quickstart.md](quickstart.md) Definition of Done checklist, manually verify all 14 visual checks on device (recurring task times, progress bar percentage, history dedup, analytics chip, heat map range, timer settings reactivity, timer sound, emoji rendering, background color, icon appearance) *(automation: DoD items 2/5/6/7(stub)/8/9/10 covered by VisualDoDAutomatedTest + existing tests; items 1/3 covered at DAO+repo layer; items 4 covered by AnalyticsViewModelTest)*

---

## Dependencies & Execution Order

```
Phase 1 (Setup)
    â””â”€â”€ Phase 2 (Foundational) â† T003 TaskDao
            â”œâ”€â”€ Phase 4 (US1) â† T012â€“T013 addTask/refreshRecurring  
            â”œâ”€â”€ Phase 5 (US2) â† T015 addTask non-recurring default
            â””â”€â”€ Phase 6 (US3) â† T018 getTodayProgress uses new DAO query

Phase 1 (Setup: T002 OGG)
    â””â”€â”€ Phase 11 (US7) â† T035 TimerPanel sound

Phase 3 (US8)  â€” independent, zero dependencies; T009 prerequisite in Phase 1
Phase 7 (US4)  â€” independent (no DAO dep needed; fixes repo methods)
Phase 8 (US5)  â€” independent
Phase 9 (US6)  â€” T048 (AnalyticsUiState) must complete before T028 and T029
    â””â”€â”€ Phase 10 (US11) â† T031 TaskHistoryScreen uses refactored component; T032 after color grep
Phase 12 (US9) â€” independent (also runnable in Phase 1; see I1 note)
Phase 13 (US10) â€” independent (also runnable in Phase 1; see I1 note)

All phases â†’ Phase 14 (Polish)
```

### Parallel Execution Opportunities

| When | Can run in parallel |
|------|-------------------|
| After Phase 2 completes | Phases 4, 5, 6 (all touch different repo methods) |
| Any time (no deps) | Phases 3, 7, 8, 11, 12, 13 (independent stories) |
| After Phase 9 T028 | Phase 10 US11 T031 |
| All tests in same phase | All [P]-tagged test tasks |

---

## Implementation Strategy

**Suggested MVP scope** (deliver value in one session): **Phases 1â€“6 + Phase 3** â€” covers all four P1 stories (US1, US2, US3, US8) plus foundational DAO fix. These are the highest-impact fixes with the clearest test criteria.

**Phase-by-phase increment delivery**:
1. Phases 1â€“3: Build passes; emoji fixed; constitution updated â€” immediately reviewable
2. + Phase 2 + 4 + 5 + 6: All P1 data-layer bugs fixed; progress bar correct
3. + Phases 7â€“11: All P2 fixes complete; analytics, heat map, history, timer all correct
4. + Phases 12â€“13: P3 visual polish complete
5. + Phase 14: Full regression verified; ready to merge
