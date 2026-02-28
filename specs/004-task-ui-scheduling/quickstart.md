# Quickstart — Feature 004: Task UI Polish & Scheduling

**Branch**: `004-task-ui-scheduling` | **Date**: 2026-02-28

---

## Prerequisites

- Android Studio Hedgehog or later
- Device or AVD running Android API 24+ (Samsung SM-S936U preferred for Tier 2 tests)
- ADB connected: run `adb devices` to confirm before any `connectedCheck` task

---

## Files Changed / Created

| Action | File | Story |
|--------|------|-------|
| EDIT | `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` | US1, US3, US4 |
| EDIT | `app/src/main/java/com/flow/navigation/Routes.kt` | US2 |
| EDIT | `app/src/main/java/com/flow/navigation/AppNavGraph.kt` | US2 |
| EDIT | `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` | US3 |
| EDIT | `app/src/main/java/com/flow/presentation/analytics/AnalyticsScreen.kt` | US2 |
| EDIT | `app/src/main/java/com/flow/util/DateUtils.kt` | US3 |
| CREATE | `app/src/main/java/com/flow/presentation/achievements/AchievementsScreen.kt` | US2 |
| CREATE | `app/src/main/java/com/flow/presentation/achievements/AchievementsViewModel.kt` | US2 |
| CREATE | `app/src/main/java/com/flow/presentation/achievements/AchievementsUiState.kt` | US2 |
| CREATE | `app/src/main/java/com/flow/presentation/achievements/AchievementMeta.kt` | US2 |
| CREATE | `app/src/main/java/com/flow/presentation/achievements/AchievementVisibility.kt` | US2 |
| CREATE (test) | `app/src/test/java/com/flow/presentation/achievements/AchievementMetaTest.kt` | US2 |
| CREATE (test) | `app/src/test/java/com/flow/util/DateUtilsDefaultTimeTest.kt` | US3 |
| CREATE (test) | `app/src/test/java/com/flow/data/repository/AddTaskDefaultTimeTest.kt` | US3 |
| CREATE (test) | `app/src/test/java/com/flow/presentation/home/ScheduleDaySelectorTest.kt` | US4 |
| CREATE (instrumented) | `app/src/androidTest/java/com/flow/AchievementsScreenTest.kt` | US2 |
| CREATE (instrumented) | `app/src/androidTest/java/com/flow/EmojiRenderTest.kt` | US1 |

---

## Build & Run

```powershell
# From D:\Android\Flow
.\gradlew assembleDebug
.\gradlew installDebug
```

---

## Unit Tests (Tier 1 — no device needed)

Run after every completed task:

```powershell
.\gradlew :app:testDebugUnitTest
```

Key test classes to watch:
- `AchievementMetaTest` — validates all 6 descriptions non-empty, `YEAR_FINISHER` is hidden, others are visible
- `DateUtilsDefaultTimeTest` — `defaultEndTime()` is today 23:59, `endTimeForDate()` preserves calendar day
- `AddTaskDefaultTimeTest` — `addTask()` no longer overrides start/end time for recurring tasks
- `ScheduleDaySelectorTest` — bitmask helpers round-trip correctly, mask=0 blocked
- Existing: `AnalyticsHelpersTest`, `DateUtilsTest`, `StreakCalculatorTest` — must remain green

---

## Instrumented Tests (Tier 2 — device required)

```powershell
# Confirm device first:
adb devices
# Then:
.\gradlew :app:connectedDebugAndroidTest
```

Key test classes:
- `EmojiRenderTest` — launches HomeScreen and help overlay, asserts emoji renders as pictographic symbol (not replacement char)
- `AchievementsScreenTest` — taps Achievements icon, verifies screen opens, earned badge shows description, placeholder shows for unearned hidden type
- Existing: `TaskPersistenceTest`, `HistoryScreenTest`, `AnalyticsPeriodSelectorTest` — must remain green

---

## Implementation Order

Follow this order to keep the build green at every step:

1. **T-001** `DateUtils.kt` — Add `defaultEndTime()` + `endTimeForDate()`. Write unit test. Green.
2. **T-002** `AchievementMeta.kt` + `AchievementVisibility.kt` — New files. Write unit test. Green.
3. **T-003** `AchievementsUiState.kt` + `AchievementsViewModel.kt` — New files. Write unit test. Green.
4. **T-004** `HomeScreen.kt` emoji fixes — Replace garbled literals with `\uXXXX`. Write instrumented test. Green.
5. **T-005** `TaskRepositoryImpl.addTask()` — Remove recurring-override block. Adapt existing tests. Green.
6. **T-006** `AddTaskDialog` default times — `dueDate = defaultEndTime()`. Update unit test. Green.
7. **T-007** `AddTaskDialog` schedule chips — Add `ScheduleDaySelector` + validation. Write unit test. Green.
8. **T-008** `Routes.kt` + `AppNavGraph.kt` — Add ACHIEVEMENTS route. No test needed (nav graph is tested via screen tests). Green.
9. **T-009** `AchievementsScreen.kt` — Full screen composable. Write instrumented test. Green.
10. **T-010** `HomeScreen.kt` nav icon — Add 6th icon button. Verify existing `IconSafeZoneTest` passes. Green.
11. **T-011** `AnalyticsScreen.kt` — Remove achievement section. Move helpers to `AchievementMeta.kt`. Verify `AnalyticsHelpersTest` still passes. Green.
12. **T-012** Full regression run — `.\gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

---

## Verifying the Spec Acceptance Criteria

| SC | How to verify |
|----|--------------|
| SC-001 | Run `EmojiRenderTest` on device; visually inspect HomeScreen help overlay |
| SC-002 | Run `AchievementsScreenTest`; tap trophies icon, confirm "Early Bird" shows description |
| SC-003 | Open New Task dialog at known time; assert start = that time, end = 23:59 |
| SC-004 | Create Mon/Wed/Fri task; on Tuesday confirm it is absent from Today list |
| SC-005 | Full `connectedDebugAndroidTest` pass |
