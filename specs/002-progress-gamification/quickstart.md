# Quickstart: Progress Gamification & Analytics Enhancement

**Feature**: `002-progress-gamification`  
**Branch**: `002-progress-gamification`  
**DB version**: 5 → 6  

---

## What This Feature Adds

| Area | What changes |
|------|-------------|
| **Home screen** | Progress ring counts only `dueDate = today` tasks (including recurring when scheduled); urgency colour (green/yellow/orange) on future-dated cards |
| **Recurring tasks** | `scheduleMask` field; DAILY or specific weekdays; `refreshRecurringTasks()` sets `startDate`/`dueDate` to scheduled date |
| **Analytics screen** | Period selector (Current Year / Last 12 Months / Specific Year / Lifetime); fixed month label clipping |
| **Streaks & Achievements** | Per-task streak tracking; 6 award types; Achievements section in Analytics |
| **Forest** | Recurring-task tree heatmap section inside Analytics |
| **History editing** | Edit dates/status on any history entry; recalculates streaks/heatmap |

---

## Key File Locations

```
app/src/main/java/com/flow/
├── data/
│   └── local/
│       ├── TaskEntity.kt              (+ scheduleMask: Int?)
│       ├── TaskStreakEntity.kt        (NEW)
│       ├── TaskStreakDao.kt           (NEW)
│       ├── AchievementEntity.kt      (NEW)
│       ├── AchievementType.kt        (NEW enum)
│       ├── AchievementDao.kt         (NEW)
│       ├── TaskCompletionLogDao.kt   (+ updateLog, getRecurringLogsBetween)
│       └── AppDatabase.kt            (version 6, MIGRATION_5_6, new entities/DAOs)
├── domain/
│   └── streak/                       (NEW package)
│       ├── DayMask.kt
│       ├── StreakResult.kt
│       └── StreakCalculator.kt
├── data/
│   └── repository/
│       ├── TaskRepository.kt         (+ 8 new method signatures)
│       └── TaskRepositoryImpl.kt     (implement new methods)
├── presentation/
│   ├── home/
│   │   ├── HomeUiState.kt            (+ TodayProgressState, UrgencyLevel)
│   │   ├── HomeViewModel.kt          (updated getTodayProgress, urgency derivation)
│   │   └── HomeScreen.kt             (urgency card colouring, updated progress ring)
│   └── analytics/
│       ├── AnalyticsPeriod.kt        (NEW sealed class)
│       ├── AnalyticsUiState.kt       (expanded with period, forest, stats, achievements)
│       ├── AnalyticsViewModel.kt     (period selector, forest, streaks, achievements)
│       └── AnalyticsScreen.kt        (period chips, Forest section, Achievements section)
└── presentation/
    └── history/
        ├── GlobalHistoryViewModel.kt (+ updateLogEntry, streak recalc trigger)
        └── GlobalHistoryScreen.kt    (edit dialog for dates/status)
```

---

## Running the App

```powershell
# Standard build
.\gradlew assembleDebug

# Run all JVM unit tests (includes StreakCalculator tests)
.\gradlew testDebugUnitTest

# Run full instrumented suite (requires connected device)
.\gradlew connectedDebugAndroidTest

# Install on device
.\gradlew installDebug
```

---

## DB Migration Note

Room auto-detects version 6 and runs `MIGRATION_5_6` on first app launch. The migration:
1. Adds `scheduleMask INTEGER DEFAULT NULL` column to `tasks`
2. Creates `task_streaks` table
3. Creates `achievements` table with a unique index

**If you need to reset during development**:
```powershell
adb shell pm clear com.flow   # clears DB + prefs
```
Or increment version to 7 with a destructive migration during active development only.

---

## Testing the Urgency Colours

1. Create a task with `startDate = 20 days ago`, `dueDate = 10 days from now` → 67% elapsed → **yellow** card.
2. Create a task with `startDate = 9 days ago`, `dueDate = 1 day from now` → 90% elapsed → **orange** card.
3. Create a task with `startDate = today`, `dueDate = 10 days from now` → 0% elapsed → **green** card.
4. Complete any of the above → card reverts to default completed styling.

---

## Testing Streaks (DAILY recurring task)

1. Create a recurring task (DAILY = default).
2. Mark complete today → streak = 1.
3. The streak resets when `refreshRecurringTasks()` runs the following morning.
4. Complete again → streak = 2. Miss a day → current streak resets to 0; longestStreak is preserved.

---

## Testing Streaks (MWF recurring task)

1. Create a recurring task with `scheduleMask = DayMask.MON or DayMask.WED or DayMask.FRI`.
2. The task only appears on Home on those weekdays.
3. Completing on Mon and Wed → streak = 2. Tuesday gap does NOT break the streak.
4. Missing Wednesday → current streak resets to 0 at the next scheduled day (Friday).

---

## Architecture Guardrails

- `StreakCalculator` has **zero Android imports** — run it in `testDebugUnitTest` without a device.
- `AnalyticsPeriod` is a UI-layer sealed class — never instantiate it in the data layer.
- All new DAO queries use `@Query` with named parameters (`WHERE taskId = :taskId`) — no string concatenation.
- New `TaskCompletionLog` mutations trigger streak recalculation and achievement checks in the same coroutine scope as the write — no eventual-consistency gaps.
