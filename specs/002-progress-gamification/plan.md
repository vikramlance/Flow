# Implementation Plan: Progress Gamification & Analytics Enhancement

**Branch**: `002-progress-gamification` | **Date**: 2026-02-22 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/002-progress-gamification/spec.md`

## Summary

Fix the Home-screen daily progress ring to count only `dueDate = today` tasks (including recurring tasks when scheduled); add urgency colour coding on future-dated cards; add a multi-period Analytics heatmap with month-label fix; introduce schedule-aware streak tracking, 6 gamification awards, a Forest heatmap section, and full history editing with streak recalculation. Requires DB v5 → v6 (3 additive schema changes: `scheduleMask` column, `task_streaks` table, `achievements` table).

## Technical Context

**Language/Version**: Kotlin 2.0  
**Primary Dependencies**: Jetpack Compose BOM ~2024.09.02, Material3 1.3.0, Room 2.6, Hilt, KSP, Coroutines + Flow  
**Storage**: Room/SQLite (local-first, DB version 6)  
**Testing**: JVM unit tests (`testDebugUnitTest`), instrumented UI tests (`connectedDebugAndroidTest`)  
**Target Platform**: Android (API 26+ per existing `app/build.gradle.kts`)  
**Project Type**: Mobile (single Android module)  
**Performance Goals**: Home screen re-render < 16 ms per frame; streak recalculation < 50 ms for up to ~1825 daily log entries  
**Constraints**: Offline-capable (local DB only); no network calls introduced; Room reactive `Flow` must drive all live data  
**Scale/Scope**: Single user, ~50–500 tasks, ~2 000 log entries across 5+ years; ~7 new source files, ~8 modified files

## Constitution Check

*Pre-design evaluation — re-evaluated after Phase 1. All 4 gates PASS.*

### Additive Logic (Non-Regression) — Gate 1 ✅

| Affected flow | Risk | Verification |
|---------------|------|-------------|
| Home screen task list | `refreshRecurringTasks()` now sets `dueDate`; must not duplicate cards | Non-regression unit test with `scheduleMask = null` legacy tasks |
| Home progress ring | Counting changes from "all active" to "dueDate = today" | `TodayProgressStateTest`; verify seed tasks still render correctly |
| Task card rendering | Urgency colour derived per card; must not affect completed/overdue styles | `UrgencyLevelTest` covering all `TaskStatus` × date combinations |
| History screen | New edit dialog added; existing read-only path unchanged | Non-regression instrumented test on read path |
| Analytics heatmap | Period selector wraps existing rendering; CurrentYear replaces rolling 52 weeks | Comparison test — existing data must display identically |
| Task status transitions | TODO → IN_PROGRESS → COMPLETED flow | Feature 001 test suite must remain green |

### Data Integrity — Gate 2 ✅

| Change | Migration | Invariant |
|--------|-----------|-----------|
| `scheduleMask INTEGER DEFAULT NULL` | `ALTER TABLE tasks ADD COLUMN` | Existing recurring tasks default to DAILY (null) |
| `task_streaks` table | `CREATE TABLE IF NOT EXISTS` | Cascade delete with parent task |
| `achievements` table + unique index | `CREATE TABLE IF NOT EXISTS` + `UNIQUE INDEX` | `CONFLICT IGNORE` — rows never deleted |
| `TaskCompletionLog` date mutability | No schema change; new `updateLog()` DAO | Streak recalculation mandatory in same scope as edit |

Post-edit invariant: `getCompletedTaskCount()` must equal `isCompleted=true` log rows — verified in `RepositoryIntegrityTest`.

### Consistency — Gate 3 ✅

All new code follows `UI → ViewModel → Repository → DAO`:
- `StreakCalculator` is a pure domain object — no Room/Android imports.
- `AnalyticsPeriod` is UI-layer only — never passed below the ViewModel. Enforced by repository methods accepting `(startMs: Long, endMs: Long)` parameters; the ViewModel calls `period.toDateRange(earliestLogMs)` to convert before any repository call.
- `AchievementEntity` writes happen inside `TaskRepositoryImpl` only.
- History edit dispatches only to `GlobalHistoryViewModel`.
- `AnalyticsUiState` is a single data class (single source of truth per screen).

### Security — Gate 4 ✅

- No credentials, tokens, or PII in any new or modified tracked file.
- `.gitignore` coverage unchanged — no new sensitive file types.
- All new `@Query` annotations use named parameters (no string concatenation).
- No network calls introduced.
- Task titles/dates not logged at any level in new code.
- No new third-party dependencies.

## Project Structure

### Documentation (this feature)

```text
specs/002-progress-gamification/
├── plan.md                          ← this file
├── research.md                      ← Phase 0 complete
├── data-model.md                    ← Phase 1 complete
├── quickstart.md                    ← Phase 1 complete
├── contracts/
│   └── internal-contracts.md        ← Phase 1 complete (9 contracts)
└── tasks.md                         ← Phase 2 (/speckit.tasks)
```

### Source Code Changes

```text
app/src/main/java/com/flow/

data/local/
├── TaskEntity.kt              MODIFY  + scheduleMask: Int? = null
├── AppDatabase.kt             MODIFY  version=6; add 2 entities; MIGRATION_5_6
├── TaskCompletionLogDao.kt    MODIFY  + updateLog(); + getRecurringLogsBetween()
├── TaskStreakEntity.kt        NEW     taskId(PK), currentStreak, longestStreak, longestStreakStartDate, lastUpdated
├── TaskStreakDao.kt            NEW     upsertStreak(), getStreakForTask(), deleteByTaskId()
├── AchievementType.kt         NEW     enum STREAK_10/30/100, ON_TIME_10, EARLY_FINISH, YEAR_FINISHER
├── AchievementEntity.kt       NEW     id(PK), type, taskId?, earnedAt, periodLabel?
└── AchievementDao.kt          NEW     insertOnConflictIgnore(), getAll(), getByTask()

domain/streak/              NEW PACKAGE — pure Kotlin, no Android imports
├── DayMask.kt                 NEW     bitmask helpers; fromDayOfWeek(), isScheduled()
├── StreakResult.kt            NEW     data class(currentStreak, longestStreak, longestStreakStartDate)
└── StreakCalculator.kt        NEW     compute(completionDates, scheduleMask, today): StreakResult

data/repository/
├── TaskRepository.kt          MODIFY  + 9 new method signatures
└── TaskRepositoryImpl.kt      MODIFY  implement new methods; extend refreshRecurringTasks()

presentation/home/
├── HomeUiState.kt             MODIFY  + TodayProgressState, UrgencyLevel
├── HomeViewModel.kt           MODIFY  today progress Flow; urgency derivation
└── HomeScreen.kt              MODIFY  urgency card colour; updated progress ring label/colour/empty state

presentation/analytics/
├── AnalyticsPeriod.kt         NEW     sealed class CurrentYear/Last12Months/SpecificYear/Lifetime
├── AnalyticsUiState.kt        MODIFY  + period, heatmapData, forestData, forestTreeCount, streaks, achievements, stats
├── AnalyticsViewModel.kt      MODIFY  period selector; forest + streak + achievement flows
└── AnalyticsScreen.kt         MODIFY  PeriodSelectorRow chips; month label fix; ForestSection; AchievementsSection

presentation/history/
├── GlobalHistoryViewModel.kt  MODIFY  + updateLogEntry(); streak recalc trigger
└── GlobalHistoryScreen.kt     MODIFY  + HistoryEditDialog (dates + status)

di/
└── AppModule.kt               MODIFY  bind TaskStreakDao, AchievementDao
```

### Test Coverage Plan

```text
app/src/test/                         JVM — no device required
├── domain/streak/
│   └── StreakCalculatorTest.kt       NEW  DAILY/MWF/edge cases (~25 tests)
├── data/local/
│   └── TodayProgressTest.kt         NEW  boundary conditions for dueDate filter
└── presentation/home/
    └── UrgencyLevelTest.kt          NEW  elapsed % → colour mapping (~12 tests)

app/src/androidTest/                  instrumented — device/emulator
├── presentation/analytics/
│   └── AnalyticsPeriodSelectorTest.kt  NEW  chip selection, heatmap reloads
├── presentation/history/
│   └── HistoryEditDialogTest.kt     NEW  date edit, status change, validation error
└── data/repository/
    └── RepositoryIntegrityTest.kt   NEW  streak consistency post-edit
```

## Complexity Tracking

*No constitution violations. No entries required.*

## Phase 0 Output

- [research.md](research.md) — 5 research decisions, all NEEDS CLARIFICATION resolved.

## Phase 1 Output

- [data-model.md](data-model.md) — entities, fields, DB v5→v6 migration, repository interface additions.
- [contracts/internal-contracts.md](contracts/internal-contracts.md) — 9 cross-layer contracts.
- [quickstart.md](quickstart.md) — developer onboarding, file locations, test recipes.

## Next Step

Run `/speckit.tasks` to generate `tasks.md`.
