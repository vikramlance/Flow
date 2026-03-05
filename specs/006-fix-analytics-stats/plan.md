# Implementation Plan: Fix Analytics Statistics & History State Sync

**Branch**: `006-fix-analytics-stats` | **Date**: 2026-02-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/006-fix-analytics-stats/spec.md`

## Summary

Six analytics and history bugs share a common root cause: the app stores recurring task completions in `task_logs` and non-recurring completions in `tasks.completionTimestamp`, but every aggregation query was written for only one of the two storage locations. The analytics heatmap (and the total chip derived from it), on-time count, missed count, lifetime stats, and current-year stats all undercount because they query only one source. Additionally, the history list fails to reactively remove recurring tasks when their status is changed directly from the History screen, because the `GlobalHistoryViewModel.saveEditTask` path never updates the originating `task_logs` row.

All fixes are additive DAO query additions + repository computation corrections + one ViewModel logic fix. No schema changes, no migrations.

## Technical Context

**Language/Version**: Kotlin 2.0  
**Primary Dependencies**: Room 2.6 (DAO queries), Hilt (DI), Kotlin Coroutines + Flow (reactive state), Jetpack Compose + Material 3 (UI — read-only for this fix)  
**Storage**: Room / SQLite — tables `tasks` and `task_logs`; no schema changes  
**Testing**: JUnit 4 (unit, `testDebugUnitTest`), Hilt + Room in-memory (instrumented, `connectedDebugAndroidTest`)  
**Target Platform**: Android (minSdk per project; Compose UI)  
**Project Type**: Single Android project  
**Performance Goals**: All new DAO queries are simple `COUNT(*)` or indexed range scans; no performance concern  
**Constraints**: No schema migrations; no breaking changes to existing reactive chains; `getCompletedTaskCount()` reactive Flow on Home screen unchanged  
**Scale/Scope**: Data layer + ViewModel fixes; no new screens; no new navigation

## Constitution Check

### Additive Logic (Non-Regression) — Gate 1

Existing flows and their verification:

| Existing flow | Expected behavior after fix | Verification |
|---|---|---|
| One-time task status change from History screen removes it from history | `saveEditTask` new `if` block only runs when `isRecurring = true`; non-recurring path unchanged | Unit test: `GlobalHistoryViewModelTest` — existing non-recurring removal test must still pass |
| All task types: status change from Home screen updates history correctly | Home uses `HomeViewModel.updateTaskStatus` → `repository.updateTaskStatus`; this path is NOT touched | Unit test: existing `GlobalHistoryViewModelTest` home-screen tests must still pass |
| Analytics period filters (daily/monthly/yearly/custom) | `getHeatMapData(startMs, endMs)` signature unchanged; merge is additive | Unit test: `AnalyticsViewModelTest` — period change still updates map |
| Graph rendering (heatmap) | `Map<Long, Int>` structure unchanged; only values increase | Unit test: result type/structure asserted |
| Forest data (recurring habits only) | `getForestData` unchanged — intentionally recurring-only | Existing tests pass unchanged |
| Streak and achievement triggers | `updateTaskStatus` code path unchanged | Existing streak/achievement tests pass |
| Home screen `getCompletedTaskCount` (dummy data gate) | This reactive Flow queries `tasks WHERE status=COMPLETED` — unchanged | Existing home screen tests pass |

### Data Integrity — Gate 2

- No schema changes → DI-002 satisfied (no migration)
- Single source of truth: `task_logs` remains authoritative for recurring completions; `tasks.completionTimestamp` remains authoritative for non-recurring. Fixes unify the VIEW of this data without changing the storage model
- `isCompleted = false` in `task_logs` after the history-screen fix is a correct representation of "this occurrence was un-done by the user" — same semantics as the existing Home-screen path
- No data loss: `updateLog(log.copy(isCompleted = false))` modifies only the `isCompleted` field; all other fields preserved

### Consistency — Gate 3

- All fixes follow UI → ViewModel → Repository → DAO → Storage (CO-001 ✓)
- `saveEditTask` fix moves logic to the data/repository layer interaction, not UI layer
- `getHeatMapData` merge is inside `TaskRepositoryImpl` (data layer) — not in ViewModel or UI

### Security — Gate 4

- No credentials, PII, or machine paths introduced
- All new queries use Room `@Query` with named parameters (SE-003 ✓)
- No new logging of task content (SE-004 ✓)
- No new dependencies; no CVE review needed

### Testing — Gate 5

Testing plan covers all four tiers:

**(a) Unit tests** (`src/test/`) — TDD: failing test written BEFORE each fix:
- `TaskRepositoryImplTest` — new tests for all 6 repository method fixes (heatmap merge, on-time, missed, lifetime, year stats, earliest date)
- `AnalyticsViewModelTest` — expand to verify non-recurring tasks appear in heatmap, on-time, missed; regression tests for existing scenarios
- `GlobalHistoryViewModelTest` — new tests: recurring task COMPLETED→TODO from history removes from list; recurring task COMPLETED→IN_PROGRESS removes; existing non-recurring tests unaffected; Home-screen path tests unaffected

**(b) Instrumented tests** (`src/androidTest/`) — DAO tests with in-memory Room:
- `TaskDaoTest` — test new queries: `getCompletedNonRecurringInRange`, `getEarliestNonRecurringCompletionDate`, modified `getMissedDeadlineCount`
- `TaskCompletionLogDaoTest` — test new queries: `getRecurringOnTimeCount`, `getRecurringMissedCount`, `getTotalCompletedLogCount`
- `HistoryScreenTest` — expand: recurring task status change from history screen removes item

**(c) End-to-end** — Tier 2 connected tests serve as system gate (no separate E2E suite exists yet)

**(d) Full suite run** — After every task: `.\gradlew testDebugUnitTest`; after Phase E: `.\gradlew connectedDebugAndroidTest`

Manual testing MUST NOT substitute for any tier.

## Project Structure

### Documentation (this feature)

```text
specs/006-fix-analytics-stats/
├── plan.md              ← this file
├── research.md          ← Phase 0: bug root causes, findings 1–8
├── data-model.md        ← Phase 1: entity invariants, new DAO query signatures
├── quickstart.md        ← Phase 1: build commands, TDD task sequence
├── contracts/
│   └── internal-contracts.md  ← Phase 1: precise query SQL and fix pseudocode
└── tasks.md             ← Phase 2 output (created by /speckit.tasks)
```

### Source Code (Android project)

```text
app/src/main/java/com/flow/
├── data/
│   ├── local/
│   │   ├── TaskDao.kt                    ← new queries; modify getMissedDeadlineCount
│   │   └── TaskCompletionLogDao.kt       ← 3 new queries
│   └── repository/
│       ├── TaskRepository.kt             ← expose getLogForTaskDate
│       └── TaskRepositoryImpl.kt         ← fix 6 methods + delegate getLogForTaskDate
└── presentation/
    └── history/
        └── GlobalHistoryViewModel.kt     ← fix saveEditTask

app/src/test/java/com/flow/
├── fake/
│   └── FakeTaskRepository.kt             ← add getLogForTaskDate stub
└── presentation/
    ├── analytics/
    │   └── AnalyticsViewModelTest.kt     ← expand with non-recurring scenarios
    └── history/
        └── GlobalHistoryViewModelTest.kt ← new recurring-task-from-history tests

app/src/androidTest/java/com/flow/
├── data/
│   ├── TaskDaoTest.kt                    ← new (or expand existing)
│   └── TaskCompletionLogDaoTest.kt       ← new (or expand existing)
└── HistoryScreenTest.kt                  ← expand with recurring task from history
```

**Structure Decision**: Option 3 (Android single project). All changes are within `app/`. No new modules.

## Complexity Tracking

> No Constitution violations. This table is intentionally empty.

