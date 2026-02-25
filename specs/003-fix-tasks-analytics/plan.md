# Implementation Plan: Fix Tasks & Analytics (003)

**Branch**: `003-fix-tasks-analytics` | **Date**: 2026-02-22 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-fix-tasks-analytics/spec.md`

## Summary

Fix four critical bugs and deliver one analytics redesign in the Flow Android app:

1. **FR-001** — `TaskRepositoryImpl.addTask()` stores raw `dueDate` (not normalised to midnight),
   so `getTodayProgress()` returns 0 tasks because the exact-midnight DAO query finds no match.
   Fix: `dueDate = dueDate?.let { normaliseToMidnight(it) }`.

2. **FR-002** — Android `DatePickerState.selectedDateMillis` returns UTC midnight. In UTC-N
   timezones this maps to the *previous* local calendar day. Fix: new `utcDateToLocalMidnight()`
   helper in `DateUtils.kt`; applied at the 4 date-picker confirm handlers in `HomeScreen.kt`.

3. **FR-003** — `getHomeScreenTasks` SQL has no clause for `dueDate >= tomorrowStart AND
   status != COMPLETED`, so upcoming tasks never appear. Fix: add clause in `TaskDao.kt`;
   split Home screen task list into a "Today" section + "Upcoming" section.

4. **FR-005/FR-006** — History long-press is gated on `logId != null`, so non-recurring tasks
   silently do nothing; recurring tasks open only the log-date editor. Fix: two-branch handler —
   recurring shows an action sheet ("Edit this day" / "Edit task"), non-recurring opens a new
   `TaskEditSheet` directly.

5. **FR-007/008/009** — Analytics is a single long-scroll with coloured `Box` heat cells.
   Redesign: `HorizontalPager` with 4 pill tabs (Graph, Lifetime, This Year, Forest); Forest
   cells replaced with 🌲 emoji (capped at 4); best-streak text added above forest grid.

## Technical Context

**Language/Version**: Kotlin 2.0 (K2 compiler)  
**Primary Dependencies**: Jetpack Compose + Material 3 (BOM 2024.09.02), Hilt 2.52, Navigation Compose 2.8.x  
**Storage**: Room 2.6.1 — AppDatabase v7 (no schema migration needed for this feature)  
**Testing**: JUnit4 + Coroutines Test (unit), Compose UI Test + Room in-memory (instrumented)  
**Target Platform**: Android API 16 (Samsung SM-S936U, serial R5CY305LTGB)  
**Project Type**: Android single-module (`:app`)  
**Performance Goals**: No new async paths; all fixes are synchronous normalisation calls or SQL clause additions  
**Constraints**: No new dependencies; `HorizontalPager` already available via Compose Foundation BOM 2024.09.02; no DB migration  
**Scale/Scope**: 5 focused changes across 8 source files; 1 new utility file; new regression tests

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Additive Logic (Non-Regression)**: Existing flows affected: Home task list display, task
  creation/editing date pickers, History long-press, Analytics screen scroll. All pre-fix
  behaviour is preserved or corrected (no removals). Existing passing tests — `TodayProgressTest`,
  `TaskDaoTest`, `HistoryEditDialogTest`, `GlobalHistoryViewModelTest` — must remain green post-fix.
  Verified by running `.\gradlew :app:test` and `.\gradlew :app:connectedDebugAndroidTest`.

- **Data Integrity**: No schema change / no migration. The `dueDate` normalisation fix changes
  *new writes only*; existing rows with raw timestamps remain (pre-fix data is an acknowledged
  known-data issue). `normaliseToMidnight()` is idempotent: applying it to an already-midnight
  value is safe. DAO queries use parameterised Room `@Query` — no raw SQL injection risk.

- **Consistency**: All changes follow `UI → ViewModel → Repository → DAO` boundaries.
  `utcDateToLocalMidnight()` lives in `util/` (UI-layer concern). `normaliseToMidnight()` stays
  in `TaskRepositoryImpl` (repository-layer concern). History editing follows existing
  `editingLog` / `updateLogEntry` pattern extended with analogous `editingTask` / `saveEditTask`.

- **Security**: No credentials, tokens, or PII added. No new tracked files require `.gitignore`
  entries. All DB access via Room parameterised queries. No new network calls. No sensitive
  logging added.

- **Testing**:
  - Unit: `DateUtilsTest` (UTC-to-local midnight, both UTC± zones), extend `TodayProgressTest`
    with a write+read round-trip test, extend `GlobalHistoryViewModelTest` with
    `openEditTask`/`saveEditTask` happy-path and validation tests.
  - Instrumented: extend `TaskDaoTest` with future-task query coverage; extend
    `RepositoryIntegrityTest` with dueDate normalisation write verification.
  - Each critical path (task creation, progress counting, history editing, future task display)
    has at least one automated test at the appropriate tier.
  - No tier replaced by a manual-testing task.

## Project Structure

### Documentation (this feature)

```text
specs/003-fix-tasks-analytics/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output — UTC bug analysis, query fix, history editing, analytics pager
├── data-model.md        # Phase 1 output — entity invariants, new UiState fields, DateUtils contract
├── quickstart.md        # Phase 1 output — implementation order, key files map, DoD
├── contracts/
│   └── schema-changes.md  # DAO queries, function signatures, Composable contracts
└── tasks.md             # Phase 2 output (/speckit.tasks command — NOT created by /speckit.plan)
```

### Source Code

```text
app/src/main/java/com/flow/
├── util/
│   └── DateUtils.kt                          ← NEW (FR-002)
├── data/
│   ├── local/
│   │   └── TaskDao.kt                        ← SQL clause fix (FR-003)
│   └── repository/
│       └── TaskRepositoryImpl.kt             ← dueDate normalise on write (FR-001)
└── presentation/
    ├── home/
    │   ├── HomeScreen.kt                     ← 4 picker fixes (FR-002) + Upcoming section (FR-003)
    │   ├── HomeViewModel.kt                  ← split today/upcoming (FR-003)
    │   └── HomeUiState.kt                    ← upcomingTasks field (FR-003)
    ├── history/
    │   ├── GlobalHistoryScreen.kt            ← two-branch long-press + TaskEditSheet (FR-005/006)
    │   ├── GlobalHistoryViewModel.kt         ← openEditTask, saveEditTask, openActionSheet (FR-005)
    │   └── GlobalHistoryUiState.kt           ← editingTask, showActionSheet, actionSheetTarget (FR-005)
    └── analytics/
        └── AnalyticsScreen.kt               ← HorizontalPager + pill tabs + tree icons (FR-007/008/009)

app/src/test/java/com/flow/
├── util/
│   └── DateUtilsTest.kt                     ← NEW (TR-001)
├── data/local/
│   └── TodayProgressTest.kt                 ← extend (TR-001)
└── presentation/history/
    └── GlobalHistoryViewModelTest.kt        ← extend (TR-003)

app/src/androidTest/java/com/flow/
└── data/local/
    └── TaskDaoTest.kt                       ← extend (TR-002)
```

**Structure Decision**: Android single-module project. All changes confined to `:app`. No new
modules, no new Gradle dependencies.

## Complexity Tracking

No constitution gate violations. All changes are additive or corrective within existing
architecture patterns.

| Item | Why Needed | Simpler Alternative Rejected Because |
|------|------------|-------------------------------------|
| New `DateUtils.kt` file | `utcDateToLocalMidnight()` is a UI-layer concern; must not live in the repository | Inlining at each of 4 picker sites creates 4 copies of the same fix; DRY principle |
| Two-branch history long-press | Recurring and non-recurring tasks have fundamentally different edit targets (log vs entity) | Single handler cannot address both without a branch anyway |
| `HorizontalPager` for Analytics | User explicitly requested tab/pager one-section-at-a-time experience (Q5 answer) | Single `LazyColumn` was the exact UX that was rejected |

---

## Phase 0: Research

> Completed inline. See [research.md](research.md) for full findings.

| Unknown | Resolved | Decision |
|---------|----------|----------|
| UTC vs local midnight fix approach | ✅ | `utcDateToLocalMidnight()` — extract UTC calendar components, build local midnight |
| dueDate normalisation layer | ✅ | Repository (`addTask`, `updateTask`) — matches existing `startDate` treatment |
| Future task SQL clause | ✅ | Add `dueDate >= :tomorrowStart AND status != 'COMPLETED'` clause |
| History long-press branching | ✅ | Two-branch: action sheet for recurring; direct `TaskEditSheet` for non-recurring |
| Analytics pager library | ✅ | `HorizontalPager` from `androidx.compose.foundation` (already in BOM) — no new dep |

---

## Phase 1: Design

> Completed. Artifacts below.

- [research.md](research.md) — all NEEDS CLARIFICATION resolved
- [data-model.md](data-model.md) — entity invariants, new UiState fields, `DateUtils.kt` spec
- [contracts/schema-changes.md](contracts/schema-changes.md) — DAO query diffs, VM signatures, Composable specs
- [quickstart.md](quickstart.md) — implementation order, key files, DoD checklist

### Constitution Check (post-design)

Re-evaluated after Phase 1 — no new violations introduced:
- No schema change → no migration risk
- New `DateUtils.kt` has zero production dependencies → no CVE risk
- `TaskEditSheet` follows identical Compose/Hilt patterns as existing dialogs
- `HorizontalPager` is part of the stable Compose Foundation API (BOM 2024.09.02)

