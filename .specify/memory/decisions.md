# Decision Log

> Append-only. Each entry records **what** was decided, **why**, and **what was rejected**.
> The Reviewer AI references this log to prevent re-litigation and detect contradictions.

---

## Template

```
### DEC-NNN — <Title>
**Date**: YYYY-MM-DD  
**Context**: <Why the decision was needed>  
**Decision**: <What was chosen>  
**Rejected alternatives**: <What was not chosen and why>  
**Affected invariants**: <Which invariants.md rules apply>  
**Spec ref**: <spec ID or "architecture">  
```

---

### DEC-001 — Single-module architecture
**Date**: 2026-02-21  
**Context**: Project is a personal productivity app; multi-module adds build complexity with no current benefit.  
**Decision**: Keep single `:app` module.  
**Rejected alternatives**: Feature-module split — overhead exceeds value at current scale.  
**Affected invariants**: Architecture hard-boundary #2.  
**Spec ref**: architecture  

### DEC-002 — UTC-midnight date normalisation
**Date**: 2026-02-22  
**Context**: `DatePickerState.selectedDateMillis` returns UTC midnight; in UTC-N zones this maps to the previous local day (FR-002 bug).  
**Decision**: All date pickers pass through `utcDateToLocalMidnight()` then `normaliseToMidnight()` before storage.  
**Rejected alternatives**: Store local-tz millis — breaks queries when user travels across zones.  
**Affected invariants**: Invariant I-1.  
**Spec ref**: 003-fix-tasks-analytics FR-002  

### DEC-003 — No new Gradle dependencies without approval
**Date**: 2026-02-22  
**Context**: Dependency creep increases APK size and review surface.  
**Decision**: Every new dependency requires an explicit entry here before it appears in `build.gradle.kts`.  
**Rejected alternatives**: Open dependency policy — too risky for a single-dev app.  
**Affected invariants**: Architecture hard-boundary #1.  
**Spec ref**: architecture  

<!-- Append new decisions below this line -->

### DEC-007 — Remove `normaliseToMidnight()` from `updateTask()` and `saveEditTask()`
**Date**: 2026-03-10  
**Context**: FR-001 (005-fix-task-end-time) requires that editing a task's due date preserves the user-selected time exactly. Both `TaskRepositoryImpl.updateTask()` and `GlobalHistoryViewModel.saveEditTask()` unconditionally called `normaliseToMidnight(dueDate)`, silently stripping hours/minutes on every update.  
**Decision**: Remove both `normaliseToMidnight` calls from the write paths. `dueDate` is passed verbatim from the ViewModel through the Repository to the DAO. `normaliseToMidnight()` is now restricted to filtering/grouping operations only (e.g., history grouping, range-query boundary computation) — never for storage.  
**Rejected alternatives**: (a) Keep normalisation and represent time-of-day separately as a `dueTime: Long?` field — beyond spec scope; schema migration required. (b) Apply normalisation conditionally if no time was explicitly set — introduces ambiguity about what "no time set" means in millis.  
**Affected invariants**: INV-20 relaxed (see FAIL-006). CON-C01 exception noted. All `dueDate`-filtering queries must continue to use `[dayStart, dayEnd]` range checks per DEC-005.  
**Spec ref**: 005-fix-task-end-time FR-001, FR-007, FR-008 / T006, T007  

### DEC-008 — MockK DAO slot capture for Repository-layer RED test (T005)
**Date**: 2026-03-10  
**Context**: T005 needed to verify that `TaskRepositoryImpl.updateTask()` passes `dueDate` verbatim to the DAO without stripping time components. Two options considered: (a) MockK spy on `TaskDao` with slot capture; (b) in-memory Room DB with the full DAO.  
**Decision**: Use MockK `slot<TaskEntity>()` to capture the argument passed to `taskDao.updateTask()`. Keeps T005 a pure JVM unit test (fast, no emulator needed). The slot captures the exact entity passed to the DAO layer, making the assertion `entity.dueDate == expectedDueDateMillis` exact and reliable.  
**Rejected alternatives**: In-memory Room DB — heavier setup, moves test to instrumented tier; adds emulator dependency for what is a pure logic assertion. Turbine / flow testing — not needed; `updateTask` is a suspend write, not a Flow.  
**Affected invariants**: CON-X03 N/A (not a Room query test; no DB needed). CON-X01 satisfied.  
**Spec ref**: 005-fix-task-end-time / T005  

### DEC-009 — `TaskEditSheet` visibility changed from `private` to `internal`
**Date**: 2026-03-10  
**Context**: T009 needed to invoke `TaskEditSheet` from `GlobalHistoryScreenTest.kt` (instrumented Compose test in the same module). The composable was declared `private` in `GlobalHistoryScreen.kt`, making it inaccessible from the test file.  
**Decision**: Change `private fun TaskEditSheet(…)` to `internal fun TaskEditSheet(…)`. This is the minimum visibility change needed; `internal` ensures the function is still module-scoped and not part of the public API. The `@OptIn(ExperimentalMaterial3Api::class)` annotation was also required and added.  
**Rejected alternatives**: Move `TaskEditSheet` to a separate file with default (public) visibility — unnecessary API surface expansion. Use a test-only wrapper composable — unnecessary indirection.  
**Affected invariants**: None. No public API change; `internal` stays within `:app` module.  
**Spec ref**: 005-fix-task-end-time / T009, T010–T014

### DEC-004 — `addTask()` no longer normalises dueDate
**Date**: 2026-03-01  
**Context**: US3 requires the AddTaskDialog to display and store an end-of-day default (11:59 PM). Previously addTask() called normaliseToMidnight() forcing all dueDates to midnight, which was correct for equality-based DAO queries but incompatible with the new UX requirement.  
**Decision**: T019 removes normaliseToMidnight() from addTask(). The caller (AddTaskDialog) is now responsible for supplying the correct dueDate value. refreshRecurringTasks() handles the 12:01 AM / 11:59 PM recurring reset independently and is unaffected.  
**Rejected alternatives**: (a) Keep normalisation in addTask() and show 11:59 PM only as a display-layer fiction — would confuse callers and require two-layer time conversion. (b) Add a separate `endTime` field — increases schema complexity beyond spec scope (DEC-003).  
**Affected invariants**: INV-20 relaxed; CON-C01 exception noted.  
**Spec ref**: 004-task-ui-scheduling US3 / T019  

### DEC-005 — `getTodayProgress()` uses range query, not exact-midnight equality
**Date**: 2026-03-01  
**Context**: FAIL-002 fixed the progress ring by normalising dueDate to midnight and using exact equality (`WHERE dueDate = :todayMidnight`). T018/US3 introduced end-of-day dueDates (23:59 PM) that would not match an exact-midnight query, risking a FAIL-002 regression.  
**Decision**: T018 replaced the exact-midnight DAO query with `getTasksDueInRange(todayStart, todayEnd)` where `todayEnd = todayStart + 86_399_999` (23:59:59.999). This covers both midnight-aligned and end-of-day dueDates. The old `getTasksDueOn()` exact-equality query is retained in TaskDao for backward compatibility but is no longer called by the repository.  
**Rejected alternatives**: Keeping exact-midnight query and normalising dueDate in AddTaskDialog — requires the Dialog to re-normalise after supplying an end-of-day value, defeating the US3 UX.  
**Affected invariants**: INV-20 relaxed in memory/invariants.md.  
**Spec ref**: 004-task-ui-scheduling US3 / T018  

### DEC-006 — SchedulePreset bitmask encoding
**Date**: 2026-03-01  
**Context**: US4 ScheduleDaySelector needed a compact encoding for day-of-week presets.  
**Decision**: Reuse the existing DayMask.kt bit scheme: bit 0 = Monday through bit 6 = Sunday. SchedulePreset enum: ALL_DAYS = 127 (0111_1111), WEEKDAYS = 31 (0001_1111, Mon–Fri), WEEKENDS = 96 (0110_0000, Sat–Sun). resolveInitialScheduleMask(null) returns 127 (every day) per FR-018.  
**Rejected alternatives**: ISO weekday numbering (1=Mon .. 7=Sun) — does not fit a bitmask; bit 0 = Sunday (Java Calendar convention) — inconsistent with existing DayMask.kt.  
**Affected invariants**: None new; consistent with existing scheduleMask validation in addTask() (1..127).  
**Spec ref**: 004-task-ui-scheduling US4 / T023-T027  
