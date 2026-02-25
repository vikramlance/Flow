# Feature Specification: Task Display Fixes & Analytics Redesign

**Feature Branch**: `003-fix-tasks-analytics`  
**Created**: 2026-02-22  
**Status**: Draft  
**Input**: User description: "Fix task display bugs: top bar percentage shows no tasks for today, target date off by one day on creation, future tasks not shown on home screen, history task editing broken for recurring and generic tasks, analytics tab redesign with separate sections and forest visualization showing trees per day"

---

## Clarifications

### Session 2026-02-22

- Q: For recurring tasks in History, should long-press edit the task entity, the log entry, or both? → A: Both — show two actions: "Edit this day" (completion log for that specific day) and "Edit task" (task entity: title, start date, target date, schedule).
- Q: How should future-dated tasks be presented on the Home screen? → A: Separate clearly labelled "Upcoming" section below today's tasks, with a section header.
- Q: What is the maximum number of tree icons rendered per Forest cell? → A: 4 trees (🌲🌲🌲🌲). Cap at 4; zero completions shows an empty/greyed cell.
- Q: Should the existing completion-log-date-only dialog be kept or replaced by the new full-task edit? → A: Keep it as the "Edit this day" action. The new full task edit is additive alongside it.
- Q: Analytics sections layout — always expanded, collapsible, or other? → A: Tab/pager navigation: a sleek section-switcher row at the top (tab chips or pill buttons); only ONE section fills the screen at a time; no other section content is visible while viewing the current section.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Today Progress Bar Shows Correct Count (Priority: P1)

A user has tasks with a target date set to today. The top app bar progress
percentage currently shows "0 / 0" (no tasks for today) even though tasks
clearly have today as their target date. The progress bar must count those
tasks and show the correct ratio.

**Why this priority**: Data correctness — the home-screen percentage is the
primary motivator for daily completion. Showing zero destroys trust.

**Independent Test**: Create two tasks with dueDate = today, complete one,
observe the top bar shows 50 %.

**Root cause identified**: `getTodayProgress()` queries `WHERE dueDate = todayMidnight`
(exact epoch match). `addTask()` normalises `startDate` to local midnight but
stores `dueDate` as-is. If the user picks 6 pm today, `dueDate` = 6 pm ≠ midnight.

**Acceptance Scenarios**:

1. **Given** a task was created with target date = today at any time of day,
   **When** the home screen loads,
   **Then** the progress bar counts that task in the denominator.
2. **Given** one of two today-dated tasks is completed,
   **When** the user views the home screen,
   **Then** the top bar reads 50 %.
3. **Given** no tasks have today as target date,
   **When** the home screen loads,
   **Then** the progress bar shows 0 / 0 without crashing.

---

### User Story 2 — Target Date Saved as the Correct Calendar Day (Priority: P1)

When a user picks February 22 in the date picker and saves the task, the
stored target date must display as February 22, not February 21.

**Why this priority**: Off-by-one corrupts every date-sensitive behaviour:
matching, sorting, display, and progress counting.

**Independent Test**: Pick today in the DatePickerDialog, save, open the
task — the shown date must match the day actually selected.

**Root cause identified**: Android's `DatePickerState.selectedDateMillis`
returns the UTC midnight of the selected day. When a user in UTC+05:30 picks
Feb 22, the picker returns the Feb 21 18:30 UTC value. `normaliseToMidnight()`
with a local `Calendar` then produces Feb 21 midnight local — one day earlier.

**Acceptance Scenarios**:

1. **Given** the user selects Feb 22 from the date picker,
   **When** the task is saved,
   **Then** the task card shows target date = Feb 22 (regardless of UTC offset).
2. **Given** a task saved with target date = Feb 22,
   **When** viewed on Feb 22,
   **Then** the progress bar includes it in today's denominator.

---

### User Story 3 — Future-Dated Tasks Appear on Home Screen (Priority: P1)

When a user creates a task with a target date later than today (e.g. next
week), the task disappears from the home screen immediately after creation.
Future-dated tasks must appear in a dedicated **"Upcoming" section** below
today's active tasks, clearly separated with a section header.

**Why this priority**: Task items literally vanish — users lose work and trust.

**Independent Test**: Create a task with dueDate = tomorrow. Navigate away
and back to home — the task must appear in the "Upcoming" section.

**Root cause identified**: `getHomeScreenTasks` query only includes dated tasks
with `dueDate >= todayStart AND dueDate < tomorrowStart`. Future tasks
(`dueDate >= tomorrowStart`) are not covered by any clause.

**Acceptance Scenarios**:

1. **Given** a task with dueDate = 7 days from now and status = TODO,
   **When** the home screen loads,
   **Then** that task appears in an "Upcoming" section below today's tasks.
2. **Given** a future-dated task is completed,
   **When** the home screen loads,
   **Then** that task is no longer shown in either section.
3. **Given** five tasks with various future dates,
   **When** the home screen renders,
   **Then** all five appear in the "Upcoming" section, separated from today's items by a visible header.
4. **Given** only tasks with dueDate = today,
   **When** computing progress percentage,
   **Then** future-dated tasks in the "Upcoming" section are NOT counted in the denominator.

---

### User Story 4 — Full Task Editing from History (Priority: P2)

When a user long-presses any task row in the History screen a bottom sheet
appears with contextual actions. For **recurring** tasks, two actions are
shown: "Edit this day" (modify the completion log entry for that specific
occurrence) and "Edit task" (modify the entity: title, dates, schedule). For
**non-recurring** tasks, long-press opens the full entity edit sheet directly
(title, start date, target date, status).

**Why this priority**: Users must be able to correct mistakes and reactivate
tasks from history.

**Independent Test**:
- Recurring: long-press → choose "Edit task" → change status to "In Progress"
  → task reappears on home screen.
- Non-recurring: long-press → edit sheet opens directly → change status to
  "To Do" → task moves to home screen.

**Acceptance Scenarios**:

1. **Given** the user long-presses a **recurring** task row in History,
   **When** the gesture completes,
   **Then** a bottom sheet appears offering two actions: "Edit this day" and
   "Edit task".
2. **Given** the user chooses "Edit this day" for a recurring task,
   **When** the action sheet opens,
   **Then** the existing completion-log date picker is shown (unchanged
   from current behaviour).
3. **Given** the user chooses "Edit task" for a recurring task,
   **When** the edit sheet opens,
   **Then** the following entity fields are editable: title, start date, target date.
   (Recurrence pattern / schedule-days editing is out of scope for this feature.)
4. **Given** the user long-presses a **non-recurring** task row in History,
   **When** the gesture completes,
   **Then** a full edit sheet opens directly with editable title, start date,
   target date, and status (TODO / IN_PROGRESS / COMPLETED).
5. **Given** the user changes status to "To Do" or "In Progress" and saves,
   **When** the sheet dismisses,
   **Then** the task disappears from History and appears on the Home screen.
6. **Given** the user taps Cancel on any edit sheet,
   **When** the sheet dismisses,
   **Then** no data changes are persisted.

---

### User Story 5 — Analytics Tab with Section-Switcher Navigation and Tree Forest (Priority: P3)

The analytics tab must be redesigned as a **tab/pager layout**: a sleek
section-switcher row (pill chips or underlined tabs) at the top of the screen
lets the user jump between four sections — Contribution Graph, Lifetime Stats,
This Year's Stats, Forest. Only one section fills the screen at a time; no
other section content is visible while the current section is active. The
Forest section replaces coloured squares with small tree icons (🌲, up to 4
per cell) representing completed recurring tasks per day.

**Why this priority**: Visual polish; the correctness bugs are higher priority.

**Independent Test**: Open analytics → section switcher shows 4 labelled tabs
→ tapping "Forest" fills the screen with only the Forest grid → no other
section content is visible → cells show 🌲 icons not squares.

**Acceptance Scenarios**:

1. **Given** the analytics screen opens,
   **When** it renders,
   **Then** a section-switcher row is visible at the top with four labelled
   options: "Graph", "Lifetime", "This Year", "Forest".
2. **Given** the user taps a section tab,
   **When** the transition completes,
   **Then** only that section's content fills the remainder of the screen;
   no other section content is visible.
3. **Given** recurring tasks completed across multiple days,
   **When** the Forest section is active,
   **Then** days with 1–4 completions show 1–4 🌲 icons respectively;
   days with >4 completions show 4 🌲 icons (capped); empty days show a
   greyed/empty cell.
4. **Given** a day cell with completions in the Forest,
   **When** the user taps it,
   **Then** the bottom sheet lists the task names completed that day
   (unchanged from current behaviour).

---

### Edge Cases

- Normalising an already-midnight timestamp must be idempotent (midnight → midnight).
- A task with `dueDate` = today in UTC but yesterday in UTC−X must use device-local calendar — always show the day the user intended.
- The History edit sheet opened for a since-deleted task must not crash; show an error or close gracefully.
- Zero recurring completions: the Forest section must render an empty grid, not crash.
- A future task whose `dueDate` passes without completion becomes overdue — the existing overdue clause covers it; the future-task clause must not duplicate it.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `addTask()` MUST normalise `dueDate` to local midnight using `normaliseToMidnight()` — the same helper already applied to `startDate`. This makes `getTodayProgress()` (which queries `dueDate = todayMidnight`) produce correct counts.
- **FR-002**: The UI layer MUST convert the UTC midnight value from `DatePickerState.selectedDateMillis` to local midnight before passing it to `addTask()`/`updateTask()`. Use `utcDateToLocalMidnight()` (new utility in `util/DateUtils.kt`) on the picker value — **not** `normaliseToMidnight()`, which would produce the wrong local day in UTC-N timezones.
- **FR-003**: `getHomeScreenTasks()` MUST include incomplete tasks with `dueDate >= tomorrowStart`. The Home screen MUST display two visually separated groups: today's tasks (existing behaviour) and an **"Upcoming" section** (new, below today's list, with a section header) containing future-dated incomplete tasks.
- **FR-004**: `getTodayProgress()` MUST continue to count only tasks whose `dueDate` (after FR-001 normalisation) equals today's midnight. Tasks in the Upcoming section MUST NOT be counted in the denominator.
- **FR-005**: The History screen MUST differentiate long-press behaviour by task type:
  - **Recurring tasks**: long-press opens an action bottom sheet with two options: "Edit this day" (opens the existing completion-log date picker — behaviour unchanged) and "Edit task" (opens the full entity edit sheet: title, start date, target date, schedule days).
  - **Non-recurring tasks**: long-press opens the full entity edit sheet directly (title, start date, target date, status).
- **FR-006**: When a task's status is changed via the entity edit sheet to TODO or IN_PROGRESS, it MUST disappear from History and appear on the Home screen using the existing `updateTaskStatus()` code path.
- **FR-007**: The Analytics screen MUST be redesigned as a **tab/pager layout**. A section-switcher row at the top presents four labelled tabs: "Graph", "Lifetime", "This Year", "Forest". Only one section fills the screen content area at a time; switching tabs replaces the content entirely.
- **FR-008**: The Forest section MUST render 🌲 emoji icons per cell (1–4 icons proportional to completion count, capped at 4). Zero completions MUST show an empty/greyed cell. Coloured squares MUST be removed.
- **FR-009**: The section-switcher row in Analytics MUST use a sleek, minimal design (pill-shaped `FilterChip` row or equivalent) consistent with the app's existing dark/neon-green theme. The active tab MUST be visually distinct (e.g. neon-green selected state).

### Regression & Testing Requirements

- **TR-001**: For every bug fixed (FR-001 through FR-004), a targeted unit test or instrumented test MUST be written that fails on the unfixed code and passes after the fix (red-then-green).
- **TR-002**: Bug-regression tests MUST assert at the lowest possible layer (DAO or repository), not only at the ViewModel layer.
- **TR-003**: After every change, all four test tiers (unit, compile gate, instrumented, device) MUST pass.

### Additive Logic, Data Integrity, Consistency, Security *(mandatory)*

- **AL-001 (Additive)**: Changes MUST NOT break: recurring task streaks, home screen for undated tasks, global history display, analytics for users with existing data.
- **AL-002 (Verification)**: Non-regression verified by re-running all 63 existing unit tests and 45 instrumented tests after each change.
- **DI-001 (Integrity)**: `dueDate` normalisation is applied on write only. Existing tasks with non-midnight values are corrected on next edit, with no data loss.
- **DI-002 (Migration)**: No schema change required; normalisation is a write-path correction only.
- **CO-001 (Consistency)**: History edits use the same `updateTask()` / `updateTaskStatus()` repository methods as the Home screen — no new write paths.
- **SE-001 (No Secrets)**: No sensitive data involved.
- **SE-002 (No PII)**: No personal information beyond task titles.
- **SE-003 (Safe Queries)**: All new DAO queries MUST use Room parameters (`:param`), not string concatenation.
- **SE-004 (No Sensitive Logging)**: Task content MUST NOT be written to logs in production builds.

### Key Entities

- **TaskEntity**: `dueDate: Long?` — after FR-001, always stored as local midnight when non-null. `status` becomes editable from History screen (non-recurring tasks) and via "Edit task" action (recurring tasks).
- **TodayProgressState**: derived count from tasks whose normalised `dueDate` equals today's midnight. Tasks in the Upcoming section are excluded.
- **HomeScreenTasks query**: expanded to return two logical groups — today tasks (existing clauses) + future-dated incomplete tasks (new clause). The UI layer is responsible for rendering them in separate sections.
- **HistoryItem**: used as-is (no structural change). The entity edit sheet fetches `TaskEntity` on demand via `repository.getTaskById(taskId)` when the user opens the edit surface; if the task has since been deleted the sheet must not crash — show an error snackbar and close instead.
- **TaskCompletionLog**: unchanged schema. "Edit this day" action continues to update the log entry date via the existing dialog.

---

## Success Criteria *(mandatory)*

1. **Progress bar accuracy**: On any timezone, creating a task with today's target date at any hour results in the task appearing in the top-bar count. Verified by automated DAO / repository unit test.
2. **Date display correctness**: A task created with target date = Feb 22 shows "Feb 22" regardless of UTC offset. Verified by unit test covering `normaliseToMidnight` with UTC± offsets.
3. **Future tasks in Upcoming section**: A task with dueDate 7 days out appears in the "Upcoming" section on the home screen after creation, not mixed with today's tasks. Verified by instrumented test.
4. **Two-action History edit for recurring tasks**: Long-pressing a recurring history row shows "Edit this day" and "Edit task" options; each opens the correct sheet. Verified by instrumented test.
5. **Non-recurring task status edit from History**: Long-pressing a non-recurring history row opens the full edit sheet; changing status to "To Do" moves the task to the Home screen. Verified by instrumented test.
6. **Analytics tab/pager navigation**: Tapping a section tab shows only that section's content; no other section content is visible. Verified on device.
7. **Forest tree icons**: Forest cells show 1–4 🌲 icons proportional to completions (capped at 4); empty days show a greyed cell. No coloured squares remain. Verified on device.
8. **No regressions**: All 63 existing unit tests and 45 instrumented tests pass after all changes.
9. **Regression tests added**: At minimum 4 new unit/instrumented tests (one per FR-001 through FR-004) that fail on unfixed code and pass after fixes.
