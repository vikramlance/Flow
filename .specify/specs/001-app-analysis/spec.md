# Feature Specification: Flow — Productivity & Task Management App

**Feature Branch**: `001-app-analysis`
**Created**: 2026-02-20
**Status**: Draft
**Input**: Analysis of existing Android project + 8 roadmap prompts from `.specify/roadmap.md`

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Task Management (Priority: P1)

A user can capture, organise, and progress tasks from creation to
completion. Tasks carry a start date, an optional target date, and a
status that is visually communicated through colour so the user
immediately understands what needs attention.

**Why this priority**: Without reliable task management the app has
no value. Every other feature builds on top of it.

**Independent Test**: Create a task with a target date two days in
the future. Confirm the task appears with the default To Do colour.
Advance status to In Progress (yellow), then to Completed (green).
Confirm each status change is reflected in the task card immediately.
Full value is delivered even if no other feature exists.

**Acceptance Scenarios**:

1. **Given** no tasks exist, **When** the user taps the add-task control and provides a title, **Then** a new task card appears with status "To Do" and start date defaulting to today.
2. **Given** a task with a future target date, **When** the user views the task list, **Then** the card shows remaining time until the target date.
3. **Given** a task whose target date has passed and status is not Completed, **When** the user views the task list, **Then** the card is displayed in the overdue colour (orange).
4. **Given** a task in any status, **When** the user long-presses the card, **Then** an edit panel opens showing the current title, start date, and target date — all of which can be changed and saved.
5. **Given** editing a task, **When** the user changes the target date to a past date without completing the task, **Then** the card colour updates to overdue immediately after saving.
6. **Given** the user wants to remove a task, **When** they trigger the delete action, **Then** the task is permanently removed and no longer appears in any list or history view.
7. **Given** many tasks exist, **When** the user views the home screen, **Then** only the most relevant active tasks are surfaced and the list does not require scrolling through all tasks ever created.

---

### User Story 2 — Recurring Tasks & Individual Streaks (Priority: P2)

A user can designate a task as recurring. For each recurring task
the app tracks consecutive-day completion and surfaces the streak
count, rewarding the user for maintaining a daily habit.

**Why this priority**: Recurring tasks and streaks are the primary
motivational mechanism of the app — explicitly described as the
"addictive" quality in the roadmap.

**Independent Test**: Create one recurring task. Mark it complete
today — streak shows 1. Mark it complete the next day — streak
becomes 2. Demonstrates full value of habit tracking independently
of all other features.

**Acceptance Scenarios**:

1. **Given** an existing task, **When** the user enables the recurring option, **Then** the task becomes recurring and the app begins tracking a daily completion streak for it.
2. **Given** a recurring task, **When** the user marks it complete for today, **Then** today's completion is recorded and the streak counter increments by 1.
3. **Given** a recurring task with a streak of N, **When** the user misses completing it for one full calendar day, **Then** the streak resets to 0 on the following day.
4. **Given** a recurring task, **When** the user navigates to the task's detail or streak view, **Then** they see the current streak count and a full history of which days it was completed.
5. **Given** today is a new calendar day, **When** the user opens the app, **Then** the recurring task's completion state resets to "not yet completed today", ready for a new entry.

---

### User Story 3 — Activity Heat Map (Priority: P3)

A user can view a GitHub-style contribution grid that shows every
calendar day. Each cell is coloured by task activity for that day,
giving an at-a-glance motivational overview of long-term productivity.

**Why this priority**: Visualising cumulative effort is a core
differentiator and was the original inspiration for the app concept.

**Independent Test**: Complete tasks on three different calendar
dates. Open the heat map. Verify those three dates show filled and
correctly coloured cells while all other days remain empty. Verifies
heat-map accuracy independently of any other feature.

**Acceptance Scenarios**:

1. **Given** tasks completed on various past dates, **When** the user opens the heat map, **Then** each calendar day cell reflects actual task completion activity for that exact date.
2. **Given** the heat map is open, **When** the user scrolls horizontally, **Then** weekday labels (Mon–Sun) remain fixed on the left while only date columns scroll.
3. **Given** the heat map is open, **When** the user views the grid, **Then** month labels are visible along the top axis, correctly aligned to their columns.
4. **Given** a recurring task exists, **When** the user accesses that task's dedicated streak heat map, **Then** they see a per-task grid showing only that task's completion history.
5. **Given** a task's start or target date is edited, **When** the user returns to the heat map, **Then** the old date's cell is cleared and the corrected date's cell is lit.
6. **Given** multiple years of data exist, **When** the user scrolls back in the heat map, **Then** historical cells are rendered correctly without data loss.

---

### User Story 4 — Focus Timer (Priority: P4)

A user can start a countdown timer to work within a fixed time
window. The timer supports custom durations, common presets,
pause/resume, and plays an audible notification when time is up.

**Why this priority**: Timer functionality directly supports focused
work sessions and was consistently refined across roadmap prompts 2–8.

**Independent Test**: Set a 1-minute timer. Start it. Pause halfway —
display freezes at the paused value. Resume — continues from the exact
paused time. At zero an audible alert plays and a completion message
is shown without the display disappearing.

**Acceptance Scenarios**:

1. **Given** the timer panel is open, **When** the user enters a custom duration, **Then** the timer starts from that exact duration when started.
2. **Given** the timer panel is open, **When** the user selects a quick-select preset (5, 10, 15, 20, 25, or 30 minutes), **Then** the timer duration is immediately set to that value.
3. **Given** the timer is running, **When** the user taps Pause, **Then** the countdown freezes and the time-at-pause value remains visible on screen.
4. **Given** the timer is paused, **When** the user taps Resume, **Then** the countdown continues from exactly where it was paused.
5. **Given** the countdown reaches zero, **When** the timer expires, **Then** an audible notification plays, a visible "Time is up" message appears, and the display does not disappear.
6. **Given** the timer has expired, **When** the user taps Reset, **Then** the timer returns to the originally set duration, ready to start again.

---

### User Story 5 — Daily Progress Dashboard (Priority: P5)

A user sees at a glance how much of today's work is done. A progress
indicator at the top of the home screen shows the percentage of
today's tasks completed, updating in real time as tasks are actioned.

**Why this priority**: Provides instant daily context and closes the
feedback loop between completing a task and seeing overall progress.

**Independent Test**: Add three tasks with today as the start date.
Observe the indicator shows 0%. Complete one — indicator updates to 33%.
Complete all three — indicator reaches 100%. Fully testable alone.

**Acceptance Scenarios**:

1. **Given** today has tasks in mixed statuses, **When** the user views the home screen, **Then** the progress indicator shows the correct percentage of today's tasks in Completed status.
2. **Given** a task is marked Completed, **When** the home screen is visible, **Then** the progress indicator updates immediately without requiring a manual refresh.
3. **Given** no tasks are active for today, **When** the user views the home screen, **Then** the indicator shows 0% without displaying a confusing or broken state.

---

### User Story 6 — Analytics & History (Priority: P6)

A user can review historical performance through charts and summary
statistics: tasks completed, target-date adherence, streak lengths,
and productivity trends over time.

**Why this priority**: History and analytics convert short-term actions
into long-term motivation and self-awareness.

**Independent Test**: Complete tasks across at least five different
days. Open Analytics. Verify a chart or summary shows task counts per
day, a streak count, and at least one target-date adherence indicator.

**Acceptance Scenarios**:

1. **Given** historical task data, **When** the user opens the Analytics screen, **Then** they see: total tasks completed, tasks completed on time vs overdue, and current plus best-ever streak.
2. **Given** the Analytics screen, **When** the user views the contribution chart, **Then** each cell maps to a real calendar date and colour reflects activity level.
3. **Given** a recurring task with a streak, **When** the user views analytics for that task, **Then** they see the current streak, best-ever streak, and a history grid.

---

### User Story 7 — Onboarding & Discoverability (Priority: P7)

A first-time user is guided through the app's key actions via an
interactive tutorial covering adding tasks, using the timer, and
reading the heat map. Returning users can replay the tutorial from
the settings or help area.

**Why this priority**: An onboarding flow converts installs into
engaged users and prevents abandonment due to hidden features
(explicitly flagged across multiple roadmap prompts).

**Independent Test**: Clear app data. Launch — onboarding appears
automatically. Complete all steps. Relaunch — onboarding skipped.
Navigate to Settings → Help → Tutorial — onboarding is accessible
on demand. Full value verifiable without other feature changes.

**Acceptance Scenarios**:

1. **Given** the app is launched for the very first time, **When** the home screen loads, **Then** an onboarding tutorial starts automatically and walks through: adding a task, setting a status, using the timer, and reading the heat map.
2. **Given** the tutorial has been completed, **When** the user relaunches the app, **Then** the tutorial does not appear again.
3. **Given** the user is on the settings or help screen, **When** they tap the tutorial/help entry, **Then** the full onboarding sequence can be replayed.
4. **Given** the app is launched for the first time and onboarding completes, **When** the user reaches the home screen, **Then** demo tasks (including at least one recurring task) are pre-populated so the user immediately sees a realistic app state.

---

### Edge Cases

- What happens when a task's start date is set to a future date — should it appear now or only from that date? *(Assumption: visible immediately but excluded from today's progress until the start date arrives.)*
- How does a recurring task behave if the device clock is changed — does the streak break? *(Assumption: streak uses local device clock; no server reconciliation.)*
- What happens if a user has hundreds of tasks — can the home screen list become unmanageable? *(Requirement: home screen MUST cap displayed tasks at a reasonable limit; older or completed tasks are moved to a history/archive view.)*
- What happens when the timer app is backgrounded mid-countdown? *(Assumption: timer continues; audible alert fires even when app is not foregrounded.)*
- What happens to a recurring task's streak data if the task is deleted? *(Assumption: deletion permanently removes all associated completion log entries.)*

---

## Requirements *(mandatory)*

### Functional Requirements

**Task Management**

- **FR-001**: Users MUST be able to create a task with at minimum: a title, a start date (defaults to today), and an optional target date.
- **FR-002**: Users MUST be able to edit any field of an existing task (title, description, start date, target date, recurring flag) at any time after creation.
- **FR-003**: Users MUST be able to set a task's status to: To Do, In Progress, or Completed.
- **FR-004**: The system MUST visually distinguish status using colour: green = Completed, yellow = In Progress, orange = Overdue (target date in the past and status is not Completed).
- **FR-005**: The home screen MUST display a bounded, scannable set of active tasks and MUST NOT require infinite scrolling to reach current tasks.
- **FR-006**: Users MUST be able to permanently delete any task. A confirmation dialog MUST be presented before the deletion is committed, giving the user the opportunity to cancel.

**Recurring Tasks & Streaks**

- **FR-007**: Users MUST be able to mark any task as recurring at creation or via editing.
- **FR-008**: For each recurring task the system MUST track a daily completion streak (number of consecutive days it was marked complete without a miss).
- **FR-009**: A recurring task's daily completion state MUST reset each calendar day so the user can mark it done again the next day.
- **FR-010**: The system MUST persist the full completion history for every recurring task (which specific dates it was marked complete) for the lifetime of the task.

**Heat Map**

- **FR-011**: The system MUST provide a calendar-aligned contribution grid where each cell represents exactly one calendar day.
- **FR-012**: Heat-map cells MUST be coloured by the day's activity: no activity = empty/grey; completions present = green (shade proportional to count); days with overdue tasks = orange tint.
- **FR-013**: The heat map MUST show weekday labels (Mon–Sun) fixed on the left edge while date columns scroll horizontally.
- **FR-014**: The heat map MUST cover all calendar dates for which any task data exists, including past years.
- **FR-015**: When a task's date is edited, the heat map MUST update to reflect the corrected date without an app restart.
- **FR-016**: Each recurring task MUST have its own dedicated streak heat map accessible from the task's detail or streak view.

**Focus Timer**

- **FR-017**: Users MUST be able to set any custom timer duration.
- **FR-018**: The timer MUST offer quick-select presets: 5, 10, 15, 20, 25, and 30 minutes.
- **FR-019**: Users MUST be able to pause and resume the timer; the timer display MUST remain visible while paused, showing the time at which it was paused.
- **FR-020**: When the timer reaches zero, the app MUST play an audible notification and display a visible "Time is up" message; the timer display MUST NOT disappear automatically.
- **FR-021**: Users MUST be able to reset the timer to its original duration at any time.

**Daily Progress**

- **FR-022**: The home screen MUST display a daily completion percentage: Completed tasks today ÷ total tasks active today × 100.
- **FR-023**: The daily completion percentage MUST update in real time when any task status changes.

**Analytics**

- **FR-024**: The analytics screen MUST display: total all-time tasks completed, tasks completed on time, tasks that missed their target date, and current plus best-ever streak.
- **FR-025**: Analytics data MUST include at least one visual chart or graph beyond numeric summaries.

**Onboarding**

- **FR-026**: On first launch the app MUST automatically present an onboarding tutorial covering: adding a task, changing its status, using the timer, and reading the heat map.
- **FR-027**: On completion of onboarding the app MUST pre-populate demo tasks (including at least one recurring task) so the user immediately experiences a realistic app state.
- **FR-028**: Users MUST be able to replay the onboarding tutorial at any time from the Settings screen. *(A dedicated help or FAQ area is out of scope for this iteration.)*

---

### Additive Logic, Data Integrity, Consistency *(mandatory)*

- **AL-001 (Additive)**: All new features MUST be additive; existing task CRUD flows, recurring-task streak tracking, heat-map rendering, and timer behaviour MUST continue to work correctly after every change.
- **AL-002 (Verification)**: Before any release the following flows MUST be verified: create task → change status → confirm card colour; mark recurring task complete → confirm streak increments; view heat map → confirm cell dates match actual calendar dates; run timer to zero → confirm audible alert fires and display persists.
- **DI-001 (Integrity)**: Completion timestamps MUST be immutable once recorded; they MUST NOT be silently updated when other task fields are edited.
- **DI-002 (Migration)**: Any change to the data-storage schema MUST include a tested migration that preserves all existing task records, completion logs, and streak history without data loss.
- **CO-001 (Consistency)**: All screens MUST follow the defined data-flow boundary: UI observes state from ViewModel → ViewModel delegates to repository → repository owns storage. No screen accesses storage directly.

---

### Key Entities

- **Task**: A unit of work. Attributes: unique ID, title, description, status (To Do / In Progress / Completed), start date, target date (optional), recurring flag, creation timestamp, completion timestamp (set when status first becomes Completed). "Overdue" is a derived display state: target date is in the past AND status is not Completed.

- **TaskCompletionLog**: A record pairing a recurring task identifier with a specific calendar date on which it was marked Completed. Enables per-task streak calculations and per-task heat-map rendering. One recurring task may have many logs.

- **DailyProgress**: An aggregate summary for one calendar day: count of tasks completed that day, count of total tasks active that day. Powers the main contribution heat map and the daily progress indicator.

- **AppSettings**: A lightweight key-value store for user preferences and app-state flags (e.g., whether onboarding has been completed, user's preferred timer default duration).

---

## Assumptions

1. The app is single-user and fully local. No user accounts, no cloud sync, no network calls for core features.
2. "Overdue" is a derived display state (target date past + status ≠ Completed) — not a stored status value.
3. Tasks with a future start date are visible on the home screen but are excluded from today's progress percentage until their start date arrives.
4. Deleting a recurring task permanently deletes all associated `TaskCompletionLog` entries.
5. The timer's audible notification uses the device's default notification/alarm channel; the user's device must not be silenced for the alert to be audible.
6. Streak calculation uses the device's local clock; no server-time reconciliation is performed.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can add their first task within 30 seconds of launching the app for the first time.
- **SC-002**: The home screen renders all visible tasks and the daily progress indicator within 1 second of app launch on a mid-range (3-year-old) device.
- **SC-003**: After marking a task complete, the task card colour and daily progress percentage update within 0.5 seconds — no manual refresh needed.
- **SC-004**: A user who completes a recurring task for 7 consecutive days sees a streak count of 7 and exactly 7 filled cells on that task's heat map, each aligned to the correct calendar date.
- **SC-005**: 100% of heat-map cells for days with recorded completions display the correct colour; zero cells show activity on days with no recorded completion.
- **SC-006**: A focus timer counts down within ±1 second of wall-clock time over a 30-minute session; the audible alert fires within 2 seconds of reaching zero.
- **SC-007**: A first-time user who completes the onboarding tutorial can — without external help — add a task, set it to In Progress, and navigate to the heat map screen.
- **SC-008**: Editing a task's target date is reflected on the heat map within one navigation back to the heat map — no app restart required.
