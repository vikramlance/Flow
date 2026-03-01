# Failure Log

> Every bug, rejected review, or broken build gets an entry here.
> The Reviewer AI and Implementation AI both read this before starting work
> to avoid repeating past mistakes.

---

## Template

```
### FAIL-NNN — <Short title>
**Date**: YYYY-MM-DD  
**Spec**: <spec ID>  
**Phase**: spec | plan | code | test | review  
**Symptom**: <What went wrong — observable behaviour>  
**Root cause**: <Why it went wrong — the actual defect>  
**Fix applied**: <What was done to resolve it>  
**Prevention rule**: <Invariant or checklist item added to stop recurrence>  
**Status**: resolved | open | monitoring  
```

---

### FAIL-001 — Emoji rendered as garbled characters
**Date**: 2026-02-27  
**Spec**: 004-task-ui-scheduling US-1  
**Phase**: code  
**Symptom**: Recurring task cards and How-to-Use screen showed `ðŸŒ±` / `â³` instead of 🌱 / ⏳.  
**Root cause**: Source files contained raw multi-byte UTF-8 emoji literal bytes that were mis-read as Latin-1 by the compiler, producing garbled output.  
**Fix applied**: Replace all raw multi-byte literal emoji in source files with `\uXXXX` surrogate-pair escapes (e.g., `\uD83C\uDF31` for 🌱); enforce UTF-8 file encoding in `.editorconfig` (`charset = utf-8`). INV-04 updated 2026-02-28 to mandate escapes going forward.  
**Prevention rule**: Invariant II-4 (Emoji Non-Negotiable, updated 2026-02-28). Reviewer checklist item: "grep source files for raw multi-byte emoji bytes (e.g., `ðŸ`) — valid `\\uXXXX` escapes are correct; raw bytes are a bug."  
**Status**: resolved  

### FAIL-002 — `getTodayProgress()` returned 0 tasks
**Date**: 2026-02-22  
**Spec**: 003-fix-tasks-analytics FR-001  
**Phase**: code  
**Symptom**: Analytics progress ring always showed 0%.  
**Root cause**: `addTask()` stored raw `dueDate` millis (not normalised to midnight); DAO query used exact-midnight equality.  
**Fix applied**: `dueDate = dueDate?.let { normaliseToMidnight(it) }` in `TaskRepositoryImpl.addTask()`.  
**Prevention rule**: Invariant I-1 (dates are UTC-midnight millis).  
**Status**: resolved  

### FAIL-003 — Date picker off by one day in UTC-N timezones
**Date**: 2026-02-22  
**Spec**: 003-fix-tasks-analytics FR-002  
**Phase**: code  
**Symptom**: Selecting "Feb 23" stored "Feb 22" for users in US timezones.  
**Root cause**: `DatePickerState.selectedDateMillis` is UTC midnight; no local-timezone conversion.  
**Fix applied**: New `utcDateToLocalMidnight()` helper, applied at all 4 date-picker confirm handlers.  
**Prevention rule**: Invariant I-1. All date-picker values route through `DateUtils.utcDateToLocalMidnight()`.  
**Status**: resolved  

<!-- Append new failures below this line -->

### FAIL-005 — Iteration 1 fix targeted wrong layer (UI instead of Repository)
**Date**: 2026-03-10  
**Spec**: 005-fix-task-end-time FR-001  
**Phase**: code / test  
**Symptom**: Editing a task's end date saved 12:00 AM (midnight) instead of the user-selected time. Iteration 1 added `mergeDateTime()` helpers at the UI date-picker confirm lambdas in `GlobalHistoryScreen.kt` and wrote 18 Compose utility tests that all passed — yet the bug persisted on device.  
**Root cause**: Two upstream save-path locations both called `normaliseToMidnight(dueDate)` unconditionally, overwriting any time component supplied by the UI: (1) `GlobalHistoryViewModel.saveEditTask` line 126, (2) `TaskRepositoryImpl.updateTask` line 151. The iteration 1 tests exercised utility functions (`mergeDateTime`, `resolveEditTaskSheetDueDate`) directly; none invoked the actual ViewModel or Repository save path — so the stripping was invisible to the test suite.  
**Fix applied**: T004/T005 created RED tests that invoke the real save-path layers and capture the DAO call via MockK slot. T006 removed `dueDate = updated.dueDate?.let { normaliseToMidnight(it) }` from `GlobalHistoryViewModel.saveEditTask`. T007 removed `dueDate = task.dueDate?.let { normaliseToMidnight(it) }` from `TaskRepositoryImpl.updateTask`. T004/T005 turned GREEN. Full instrumented suite: 78 tests, 0 failures.  
**Prevention rule**: Two new constitution rules added: "Repository Layer Rules" (normaliseToMidnight is only for filtering/grouping, never for storage in updateTask) and "Test Coverage Rules" (tests for a bug fix must invoke the actual save path at the root-cause layer, not utility helpers). Documented findings in memory/architecture.md and memory/invariants.md.  
**Status**: resolved  

### FAIL-006 — INV-20 / CON-C01 deviation: `updateTask()` no longer normalises dueDate
**Date**: 2026-03-10  
**Spec**: 005-fix-task-end-time FR-001  
**Phase**: code  
**Symptom**: After T007, `TaskRepositoryImpl.updateTask()` passes `dueDate` verbatim to the DAO. This violates INV-20 ("UTC-midnight-aligned millis") and the literal text of CON-C01 ("all date Longs pass through normaliseToMidnight()").  
**Root cause**: The fix is intentional — FR-001 requires exact user-selected dueDate/time to be preserved. `normaliseToMidnight()` in the Repository save path was the bug. The invariant text was written before this requirement existed.  
**Impact**: No runtime failure. `getTodayProgress()` uses `getTasksDueInRange(dayStart, dayEnd)` (DEC-005) which covers any time-of-day dueDate value. Tasks edited after this fix will have a dueDate with a non-midnight time component; queries are unaffected.  
**Fix applied**: INV-20 relaxed in memory/invariants.md to extend the exception to cover values stored by `updateTask()`. DEC-007 added to decisions.md. CON-C01 exception noted — the only remaining caller of `normaliseToMidnight()` for dueDate is `addTask()` (and it is not called there either since DEC-004).  
**Prevention rule**: ALL DAO queries filtering by `dueDate` MUST use range checks `[dayStart, dayEnd]`. Corollary already in memory/invariants.md I.1 — no change needed. New findability note: "normaliseToMidnight() MUST NOT be called on dueDate in any write path."  
**Status**: resolved

### FAIL-004 — INV-20 deviation: `addTask()` stores end-of-day dueDate (23:59 PM)
**Date**: 2026-03-01  
**Spec**: 004-task-ui-scheduling US3  
**Phase**: code  
**Symptom**: `addTask()` (T019) no longer calls `normaliseToMidnight()` on dueDate. `AddTaskDialog` (T020) passes `defaultEndTime()` = 23:59:59.999 PM today as dueDate. This violates the literal text of INV-20 ("UTC-midnight-aligned millis") and CON-C01 ("all date Longs pass through normaliseToMidnight()").  
**Root cause**: US3 requires the default end time to be 11:59 PM. T019 intentionally shifted normalisation responsibility from the Repository to the Dialog layer per DEC-004. The Dialog now supplies an end-of-day value rather than midnight.  
**Impact**: No runtime failure — `getTodayProgress()` was already updated in T018/US3 to use `getTasksDueInRange(today, todayEnd)` with `todayEnd = midnight + 86_399_999` (inclusive end-of-day). Tasks with dueDate=23:59 PM are correctly counted.  
**Latent inconsistency**: `updateTask()` still calls `normaliseToMidnight()` on dueDate. Tasks created via dialog arrive at 23:59 PM; on first edit they are promoted to midnight of the same day. Both values fall within `[dayStart, dayEnd]` so query behaviour is unaffected, but the stored value changes on update.  
**Fix applied**: INV-20 relaxed in memory/invariants.md (I.1 corollary added). No runtime change needed. DEC-004 and DEC-005 added to decisions.md. `updateTask()` inconsistency noted as known.  
**Prevention rule**: All DAO queries referencing `dueDate` MUST use range checks `[dayStart, dayEnd]`, never exact-midnight equality. New corollary added to memory/invariants.md I.1.  
**Status**: resolved  
