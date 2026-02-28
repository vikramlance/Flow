# Quickstart: Implementing 001-ui-bug-fixes

**Audience**: Developer picking up this branch to implement the fixes.  
**Branch**: `001-ui-bug-fixes`  
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Contracts**: [contracts/behavioral-contracts.md](contracts/behavioral-contracts.md) | **Data Model**: [data-model.md](data-model.md)

---

## What You Are Fixing

11 distinct bugs, all in the existing Android/Kotlin/Compose/Room/Hilt app at `app/src/main/java/com/flow/`.

| # | Bug | Primary File(s) |
|---|-----|----------------|
| 1 | Recurring task time defaults to midnight-to-midnight | `TaskRepositoryImpl.kt` |
| 2 | Non-recurring task end date defaults to null | `TaskRepositoryImpl.kt` |
| 3 | Today's progress excludes recurring tasks | `TaskRepositoryImpl.kt`, `TaskDao.kt` |
| 4 | History shows duplicate entries; history filter chips don't update | `TaskRepositoryImpl.kt` |
| 5 | Analytics "Total" chip and graph draw from different sources | `AnalyticsScreen.kt` |
| 6 | Heat map shows full 52-week rolling view instead of Jan→today | `AnalyticsScreen.kt` |
| 7 | Focus timer doesn't react to settings changes without restart | `TimerViewModel.kt` |
| 8 | Focus timer plays no sound on completion | `TimerPanel.kt`, `res/raw/` |
| 9 | Achievement emojis show as empty strings | `AnalyticsScreen.kt` |
| 10 | Background color overridden by Android 12+ dynamic theming | `Theme.kt` |
| 11 | App icon content clipped by launcher safe zone | `res/mipmap-*/ic_launcher_foreground*` |

---

## Recommended Implementation Order

Work bottom-up (data layer first, UI last) to stay within architecture boundaries and keep tests green at every step.

1. **`Color.kt` / `Theme.kt`** — 1-line fix, zero dependencies, verify visually
2. **`AnalyticsScreen.kt` emoji** — restore `achievementEmoji()` string literals
3. **`TaskDao.kt`** — add `getTasksDueInRange()` query
4. **`TaskRepositoryImpl.kt`** — fix `addTask()`, `refreshRecurringTasks()`, `updateTask()`, `updateTaskStatus()`, `getTodayProgress()`
5. **`AnalyticsScreen.kt` heat map + total chip** — refactor `ContributionHeatmap` to accept `startMs/endMs`, update Page 0 chip
6. **`TaskHistoryScreen.kt`** — pass explicit Jan-1/today range to `ContributionHeatmap`
7. **`TimerViewModel.kt`** — replace `firstOrNull()` with `collect { }`
8. **`TimerPanel.kt` + `res/raw/timer_complete.ogg`** — add completion sound
9. **App icon** — adjust foreground vector drawable padding
10. **Constitution** — append Principle VII emoji rule
11. **Tests** — add regression tests at DAO, repository, and ViewModel tiers (see Test Plan below)

---

## Key Files Reference

```
app/src/main/java/com/flow/
├── data/
│   ├── local/
│   │   ├── TaskDao.kt              ← add getTasksDueInRange()
│   │   └── TaskEntity.kt           ← read-only reference
│   └── repository/
│       └── TaskRepositoryImpl.kt   ← fixes 1, 2, 3, 4
├── presentation/
│   ├── analytics/
│   │   ├── AnalyticsScreen.kt      ← fixes 5, 6, 9
│   │   └── AnalyticsPeriod.kt      ← read for date range
│   ├── history/
│   │   ├── GlobalHistoryViewModel.kt ← saveEditTask() — review after repo fix
│   │   └── TaskHistoryScreen.kt    ← fix 6 (streak heat map range)
│   ├── timer/
│   │   ├── TimerViewModel.kt       ← fix 7
│   │   └── TimerPanel.kt           ← fix 8
│   └── home/
│       └── HomeViewModel.kt        ← review addTask() default after repo fix
└── ui/theme/
    ├── Color.kt                    ← verify DarkBackground = 0xFF121212 (already correct)
    └── Theme.kt                    ← fix 10: dynamicColor = false

app/src/
├── main/res/raw/
│   └── timer_complete.ogg          ← NEW: bundled chime for fix 8
└── main/res/mipmap-*/
    └── ic_launcher_foreground*     ← fix 11: safe-zone padding

.specify/memory/constitution.md     ← fix 9 follow-up: add Principle VII
```

---

## Test Plan Summary

Run the full test pyramid before and after each fix group.

### Tier 1 — Unit Tests (`src/test/`)

New tests to add:

| Test Class | Test Name | Verifies |
|-----------|-----------|---------|
| `TaskRepositoryImplTest` (new) | `addRecurringTask_startsAt1201am_endsAt1159pm` | Fix 1 |
| `TaskRepositoryImplTest` | `addNonRecurringTask_nullDueDate_defaultsToEndOfToday` | Fix 2 |
| `TaskRepositoryImplTest` | `updateTask_nonCompletedStatus_clearsCompletionTimestamp` | Fix 4A |
| `TaskRepositoryImplTest` | `updateTaskStatus_completedToInProgress_clearsTimestamp` | Fix 4B |
| `HomeViewModelTest` | `todayProgress_includesRecurringTasksEndingToday` | Fix 3 |
| `AnalyticsViewModelTest` (new) | `totalChipValue_equalsHeatMapSum_forCurrentYear` | Fix 5 |
| `TimerViewModelTest` | `settingsChange_whenTimerIdle_updatesRemainingSeconds` | Fix 7 |

### Tier 2 — Instrumented Tests (`src/androidTest/`)

New tests to add:

| Test Class | Test Name | Tier | Verifies |
|-----------|-----------|------|---------|
| `TaskDaoTest` | `getTasksDueInRange_returnsTasksWithDueDateIn11pmRange` | Instrumented DAO | Fixes 1, 3 |
| `TaskDaoTest` | `getTasksDueInRange_excludesTasksWithDueDateTomorrow` | Instrumented DAO | Fix 3 |
| `RepositoryIntegrityTest` | `nonRecurringTaskMovedToInProgress_disappearsFromHistory` | Instrumented Repo | Fix 4 |
| `RepositoryIntegrityTest` | `recurringTaskRecompletedSameDay_onlyOneLogEntry` | Instrumented Repo | Fix 4C |
| `HistoryScreenTest` | `dateChipStrip_updatesWhenTaskCompletionDateChanges` | Compose UI | Fix 4 filter |

### Run Commands

```powershell
# Tier 1 (no device needed)
.\gradlew testDebugUnitTest

# Tier 2 compile gate (no device needed)
.\gradlew compileDebugAndroidTestKotlin

# Tier 2 full (device required)
.\gradlew connectedDebugAndroidTest
```

---

## Definition of Done

- [ ] All 10 new unit tests pass
- [ ] All 5 new instrumented tests pass
- [ ] All existing tests continue to pass (zero regressions)
- [ ] Visual check: recurring task shows 12:01 AM / 11:59 PM in task detail
- [ ] Visual check: progress bar goes to 50% with 1 of 2 today-tasks completed
- [ ] Visual check: history screen deduplicates after moving task back to IN_PROGRESS
- [ ] Visual check: analytics "Total" chip matches sum of heatmap cells
- [ ] Visual check: heat map shows only Jan–Feb in February, not full year
- [ ] Visual check: settings timer change → navigate to timer → correct duration shown
- [ ] Visual check: timer completion plays a sound
- [ ] Visual check: achievement emojis render on Analytics screen
- [ ] Visual check: background is visibly #121212, not pure black
- [ ] Visual check: launcher icon fully contained
- [ ] Constitution file updated with Principle VII
