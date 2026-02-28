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
