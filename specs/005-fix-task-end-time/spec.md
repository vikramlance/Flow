# Feature Specification: Fix Task End Time Bug (Revised)

**Feature Branch**: 005-fix-task-end-time
**Created**: February 28, 2026
**Revised**: February 28, 2026
**Status**: Revised — previous implementation did not fix the bug (wrong layer)
**Input**: User description: "when I update the task to change the end date and save it, the task shows today's date and time is 12:00 a.m. — it's defaulting to 12:00 a.m. This occurs from both the history screen and home screen, for both recurring and non-recurring tasks. Edit in the history does not show time selection — I think that is the issue because when we only have option to save the date, the time always defaults to 12:00 AM. Creating the same date+time functionality on home screen and in history screen should be the same. Also show start date and time on the task card on the home screen. Also the test cases did not catch this bug — update all documents to avoid this kind of issue in the future."

## Root Cause Analysis (Why Previous Fix Failed)

The first implementation cycle fixed the **UI date-picker confirm lambdas** in `HomeScreen.kt` and `GlobalHistoryScreen.kt`. Those fixes were in the wrong layer. The database layer itself normalised every saved `dueDate` to midnight:

- `TaskRepositoryImpl.updateTask` (line ~151): `dueDate = task.dueDate?.let { normaliseToMidnight(it) }` — **every task update strips the time component to 00:00 before writing to Room**
- `GlobalHistoryViewModel.saveEditTask` (line ~126): same `normaliseToMidnight` call — additionally normalises at the ViewModel layer before the repository even sees it

Because all saves pass through `repository.updateTask()`, no UI-layer fix can ever survive: the repository overwrites the time with midnight unconditionally. The unit tests passed because they tested utility functions (`resolveEditTaskSheetDueDate`, `mergeDateTime`) directly, never invoking the actual save path through the ViewModel → Repository → DAO chain.

**Additionally**, the History screen `TaskEditSheet` has only a date picker and no time picker, so even if the repository were fixed, users would have no way to set or see the time of day when editing from history.

## Clarifications

### Session 2026-02-28 (Iteration 1)
- Q: End Time Behavior on Date-Only Edit → A: Preserve existing specific time (carried over).
- Q: Historical Data Migration → A: No Migration. Existing rows with midnight `dueDate` are not retroactively changed.

### Session 2026-02-28 (Iteration 2)
- History screen edit sheet must have the same date+time selection capability as the Home screen edit dialog.
- Start date and time must be visible on the task card on the Home screen.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Saved Task Retains the Exact Time Set by the User (Priority: P1 — core bug fix)

When a user edits a non-recurring task from the Home screen and changes the end date/time to a specific value (like 11:59 p.m.), the exact date and time must be saved and displayed correctly afterwards, rather than resetting the time to 12:00 a.m.

**Why this priority**: This is the core bug reported, directly affecting the primary task management workflow and data accuracy.

**Independent Test**: Can be fully tested by editing any existing non-recurring task directly on the Home screen, changing its end time, saving it, and verifying the updated time displays correctly.

**Acceptance Scenarios**:

1. **Given** an existing non-recurring task on the Home screen, **When** the user edits the task, sets the target date to today and time to 11:59 p.m., and saves, **Then** the task's target time is displayed returning exactly 11:59 p.m.

---

### User Story 2 - Update End Time for Recurring Task from Home Screen (Priority: P1)

When a user edits any task — recurring or non-recurring — from the **Home screen** and saves it, the exact time they selected must be stored and displayed. This applies to both the target (due) date/time and the start date/time.

**Why this priority**: This is the core reported bug: the Repository layer unconditionally calls `normaliseToMidnight()` on `dueDate` during every `updateTask` call, stripping the time to 00:00 regardless of what the UI sends.

**Independent Test**: Long-press any task on the Home screen → Edit → set Target Date to today and Target Time to 11:59 PM → Save → verify the task card shows "Target: [today] 23:59", not "00:00".

**Acceptance Scenarios**:

1. **Given** a non-recurring task with a 3:30 PM due time, **When** the user opens Edit, changes only the target date (not the time), and saves, **Then** the task card shows the new date with 3:30 PM preserved.
2. **Given** a recurring task, **When** the user edits the target time to 10:30 PM and saves, **Then** the stored and displayed time is 10:30 PM.
3. **Given** any task, **When** the user sets the target date to today and target time to 11:59 PM and saves, **Then** the task shows 11:59 PM — not 12:00 AM.
4. **Given** any task, **When** the user intentionally sets the time to 12:00 AM and saves, **Then** 12:00 AM is correctly stored and displayed (not an unintended default).

---

### User Story 3 - History Screen Edit Has Full Date AND Time Selection (Priority: P1)

When a user opens the edit sheet for a task from the History screen, they must be able to set both the date **and** the time for both start date and target date — the same capability that exists on the Home screen. The saved result must persist the exact time selected, not midnight.

**Why this priority**: The History screen edit sheet currently has date pickers only (no time pickers). Even if the repository bug is fixed, users would have no UI to set or adjust the time component from history. Both screens must be functionally consistent.

**Independent Test**: Navigate to History screen → long-press a task → Edit → change the due date and set a due time of 8:15 PM → Save → verify the task's target date/time shows 8:15 PM on both screens.

**Acceptance Scenarios**:

1. **Given** a task in the History screen edit sheet, **When** the user changes the target date and the target time to 8:15 PM and saves, **Then** the task shows 8:15 PM on the Home and History screens.
2. **Given** a task in the History screen edit sheet, **When** the user changes only the target date (not the time), **Then** the existing time is preserved after save.
3. **Given** a task with no target date, **When** the user picks a target date and a time from the History edit sheet and saves, **Then** the target date and time are both stored correctly.

---

### User Story 4 - Start Date and Time Visible on Task Card (Priority: P2)

On the Home screen task card, both the start date + time and the target date + time should be visible, giving the user a complete at-a-glance view of the task's time window.

**Why this priority**: Users have explicitly requested this and it is additive — no risk of regression to existing functionality.

**Independent Test**: Create a new task with a specific start time (e.g., 9:00 AM today) and a target time (e.g., 5:00 PM today). Verify the task card shows both "Start: [today] 09:00" and "Target: [today] 17:00".

**Acceptance Scenarios**:

1. **Given** a task with a start date and time, **When** the task card is shown on the Home screen, **Then** the start date and time are displayed (e.g., "Start: Mar 1, 09:00").
2. **Given** recurring tasks which set start to creation time, **When** they appear on the Home screen, **Then** the start date/time is shown alongside "Daily Target".

---

### Terminology

| Term used in spec | Meaning |
|---|---|
| **target date / target time** | The UI-visible label for `TaskEntity.dueDate` (epoch millis). Preferred in user-facing descriptions. |
| **dueDate** | Code/field name in `TaskEntity` and all Kotlin call sites. Used in technical sections. |
| **end date / end time** | User's original phrasing from the bug report; appears in the feature title and direct quotes only. Avoid in new spec prose. |
| **start date / start time** | UI label and code name for `TaskEntity.startDate` (epoch millis). |

### Dependencies and Assumptions

- **No schema change**: `TaskEntity.dueDate: Long?` and `TaskEntity.startDate: Long` already store epoch millis — only the save logic must stop normalising the time component.
- **Timezones**: Device local timezone is used for all display and storage (via `Calendar.getInstance()` without explicit timezone). No change to this assumption.
- **New task creation**: `AddTaskDialog` defaults `dueDate` to today at 23:59 — this must remain unchanged.
- **`normaliseToMidnight` retained for filtering/grouping**: The function itself is correct for computing day boundaries; it must only be removed from the **save** path.

### Edge Cases

- User sets time to exactly 12:00 AM intentionally — must store 00:00, not be treated as a bug.
- Task with `dueDate = null` — save must leave it null; time picker must only appear when a date has been selected.
- Timezone offset causes UTC midnight to represent a different local day — `utcDateToLocalMidnight` already handles this and must continue to be used in date-picker confirms.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `TaskRepositoryImpl.updateTask` MUST NOT normalise `dueDate` to midnight. The caller is responsible for sending the correct H:M value; the repository must persist exactly what it receives.
- **FR-002**: `GlobalHistoryViewModel.saveEditTask` MUST NOT normalise `dueDate` to midnight before passing to the repository.
- **FR-003**: The History screen `TaskEditSheet` MUST include time pickers (start time, target time) matching the capability of `EditTaskDialog` on the Home screen.
- **FR-004**: The History screen `TaskEditSheet` date and time display labels MUST show both date and time (e.g., "Start: Mar 1, 09:00"), not date-only.
- **FR-005**: The Home screen task card MUST display start date and time when `startDate` is set.
- **FR-006**: System MUST continue to correctly establish the target time as 11:59 PM by default when a **new** task is created via `AddTaskDialog`.
- **FR-007**: When a user changes only the date (not the time) in any edit dialog, the existing time component MUST be preserved.
- **FR-008**: The fix MUST apply equally to recurring and non-recurring tasks.

### Additive Logic, Data Integrity, Consistency, Security *(mandatory)*

- **AL-001 (Additive)**: `AddTaskDialog` (new task creation) MUST NOT be modified — it correctly defaults to 23:59.
- **AL-002 (Test Coverage)**: New tests MUST exercise the **full save path** (UI state → onSave callback → ViewModel → Repository → DAO). Tests that only call utility functions in isolation are insufficient to catch this class of bug. At minimum one test per save path (Home + History) must assert the value stored in the Repository/DAO equals the value the UI sent.
- **DI-001 (Integrity)**: `TaskEntity.dueDate` stored in Room MUST equal the epoch millis value sent by the UI after pressing Save — no intermediate layer may transform the time component.
- **DI-002 (No Migration)**: Existing rows with midnight `dueDate` (00:00) are NOT retroactively modified. They may have been deliberately set to midnight.
- **CO-001 (Consistency)**: UI → ViewModel → Repository → DAO. The Repository is a thin persistence layer — it should not apply business-logic time transformations on incoming values. `normaliseToMidnight` belongs only in date-grouping/filtering queries.
- **SE-001**: No passwords, API keys, tokens, or credentials in any tracked file.
- **SE-002**: No usernames, local machine paths, device serials, or email addresses in tracked files.
- **SE-003**: All database queries use parameterized bindings; raw concatenated SQL prohibited.
- **SE-004**: No sensitive user data in production logs.

### Key Entities

- **TaskEntity**: `dueDate: Long?` (epoch millis, nullable) and `startDate: Long` (epoch millis). Both fields must be stored with full H:M:S precision. The Repository must not apply `normaliseToMidnight` when persisting these values during an update.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of task edits from the Home screen correctly store the exact H:M the user selected — verified by a test that saves through the ViewModel and asserts the DAO received the correct value.
- **SC-002**: 100% of task edits from the History screen correctly store the exact H:M the user selected — same end-to-end test requirement.
- **SC-003**: 0 regressions in new-task creation: 100% of newly created tasks default their target time to 23:59.
- **SC-004**: History screen edit sheet allows the user to set both date and time for start date and target date.
- **SC-005**: Task cards on the Home screen show start date and time.
- **SC-006**: All previously passing tests (78 instrumented, all unit) continue to pass.

## Process Improvement Notes

The following gaps caused this bug to be missed in the first implementation cycle and MUST be addressed in planning and testing:

1. **Test scope**: Tests MUST verify the full save path (UI → ViewModel → Repository → DAO), not just utility functions. A utility function test that passes does not prove the bug is fixed if the repository overwrites the value.
2. **Layer contracts**: The Repository layer MUST NOT apply business-logic time transformations to incoming field values. `normaliseToMidnight` is appropriate only for computing day-boundary keys for filtering/grouping queries.
3. **Code review checklist**: Any call to `normaliseToMidnight(it)` (or similar time-stripping functions) on a field that is to be **stored** (not compared or grouped) must be flagged as a bug.
