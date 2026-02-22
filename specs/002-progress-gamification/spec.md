# Feature Specification: Progress Gamification & Analytics Enhancement

**Feature Branch**: `002-progress-gamification`  
**Created**: 2026-02-22  
**Status**: Draft  

## User Scenarios & Testing *(mandatory)*

---

### User Story 1 — Today-Focused Progress Indicator (Priority: P1)

A user opens the app and instantly sees a progress bar or ring at the top of the Home screen. Currently the percentage is confusing because it counts tasks the user was never meant to complete today. The progress indicator should only count tasks whose **target date is today**; anything else (future-dated, undated general tasks) must not dilute or inflate today's score. A clear label — for example "Today's progress" — must replace any ambiguous heading.

**Why this priority**: This is the first thing a user sees every session. A misleading daily number undermines trust and motivation. Fixing it is a prerequisite for every gamification feature built on top of it.

**Independent Test**: Open the app with a mix of tasks: two tasks due today (one completed, one not) and three tasks due next week. The indicator must show 50%, not 20% or any other value. The label must include a reference to "today".

**Acceptance Scenarios**:

1. **Given** two tasks are due today and zero are done, **When** the home screen loads, **Then** the progress indicator shows 0% and displays a "today" label.
2. **Given** two tasks are due today and one is completed, **When** the home screen loads, **Then** the indicator shows 50%.
3. **Given** two tasks are due today and both are completed, **When** the home screen loads, **Then** the indicator shows 100% and the indicator turns green.
4. **Given** no tasks are due today, **When** the home screen loads, **Then** the indicator shows a neutral empty state (e.g., "No tasks for today") rather than 0% or 100%.
5. **Given** a task's target date changes from today to tomorrow, **When** the home screen refreshes, **Then** that task no longer contributes to today's percentage.
6. **Given** the indicator is below 100%, **When** it transitions to 100%, **Then** the colour changes from yellow to green.

---

### User Story 2 — Urgency Colour Coding for Future-Dated Task Cards (Priority: P1)

A user has tasks with both a start date and a target (end) date set in the future. Today the cards all look identical. The user wants a glanceable visual signal: if a task is well ahead of schedule the card appears green; if it is in the warning zone it turns yellow; if the deadline is very close it turns orange. The urgency level is derived from where **today** falls within the [start date → target date] time window expressed as an elapsed percentage:

- 0–30 % elapsed → **green** (comfortable)
- 30–70 % elapsed → **yellow** (moderate urgency)
- 70–100 % elapsed → **orange** (high urgency / near deadline)
- Past target date and not completed → existing **overdue** colour (unchanged)
- Completed tasks → existing completed colour (unchanged)
- Tasks with no start date or no target date → no urgency colouring (unchanged)

**Why this priority**: Together with the fixed daily progress bar (US1) these visual urgency signals form the core daily-use value surface of the home screen and require no extra interaction to benefit from.

**Independent Test**: Create a task with a start date 10 days ago and a target 10 days from now (50 % elapsed). Its card must appear yellow. Create a second with start 9 days ago and target 1 day from now (90 % elapsed). Its card must appear orange.

**Acceptance Scenarios**:

1. **Given** a task whose elapsed percentage is 15 %, **When** the home screen shows the card, **Then** the card uses the green urgency colour.
2. **Given** a task whose elapsed percentage is 50 %, **When** the home screen shows the card, **Then** the card uses the yellow urgency colour.
3. **Given** a task whose elapsed percentage is 85 %, **When** the home screen shows the card, **Then** the card uses the orange urgency colour.
4. **Given** a task with no start date, **When** the home screen shows the card, **Then** no urgency colour is applied (card uses its default appearance).
5. **Given** a task past its target date and not completed, **When** the home screen shows the card, **Then** the existing overdue styling applies, not urgency colouring.
6. **Given** a task is completed, **When** the home screen shows the card, **Then** urgency colouring does not apply regardless of elapsed percentage.

---

### User Story 3 — Heatmap Improvements & Multi-Period Analytics (Priority: P2)

A user opens the Analytics screen to review their task-completion heatmap. Currently the month labels at the top are visually clipped (last character cut off), the default period is unclear, and there is no way to view a rolling 12-month window or lifetime data. The user wants:

- Month label truncation fixed so every label is fully visible at all screen widths.
- A default view of the **current calendar year** (1 Jan – 31 Dec of the current year).
- A **"Last 12 months"** rolling option (today minus 365 days through today, inclusive).
- A year selector to revisit any past calendar year.
- A **"Lifetime"** view showing all data ever recorded, heatmap scaled appropriately.
- Lifetime and current-year summary statistics alongside the heatmap: total tasks completed all-time, current streak, all-time longest streak, unique habits tracked.

**Why this priority**: Analytics and history are a core retention driver once the immediate daily-use experience (US1, US2) is polished.

**Independent Test**: Complete several tasks spread across different months. Open Analytics. Confirm: month labels are fully visible; default view shows Jan–Dec of the current year; switching to "Last 12 months" spans the correct rolling window; switching to "Lifetime" shows all task data with unclipped labels.

**Acceptance Scenarios**:

1. **Given** the Analytics screen opens, **When** the default period loads, **Then** the heatmap spans 1 January to 31 December of the current year and all month labels are fully visible without clipping.
2. **Given** the user selects "Last 12 months", **When** the heatmap reloads, **Then** it spans from the same calendar date one year prior through today (inclusive).
3. **Given** the user selects a past year (e.g., 2024), **When** the heatmap reloads, **Then** it shows 1 Jan – 31 Dec 2024 with all labels visible.
4. **Given** the user selects "Lifetime", **When** the heatmap reloads, **Then** it spans in full from the earliest recorded completion to today.
5. **Given** the Lifetime view is active, **When** the summary statistics section is visible, **Then** it displays total tasks completed, current streak, all-time longest streak, and unique habits count.
6. **Given** any period is selected, **When** a day cell has more completions, **Then** the cell is rendered darker, and the shading scale is consistent within the selected period.

---

### User Story 4 — Gamification: Streaks, Awards & Achievement Stats (Priority: P2)

A user who has been consistent wants to feel rewarded. The app tracks completion streaks per recurring task and surfaces earned awards in an Achievements section on the Analytics screen. Awards are earned at meaningful milestones and remain permanently visible once earned.

**Streak definition**: A recurring task must be marked complete on a day it is scheduled to occur (per its `recurrenceSchedule`) to extend the streak; completing on an unscheduled day does not count, and missing a scheduled day resets the current streak to zero while the all-time record is preserved.

**Award milestones**:
- 🌱 **10-day streak** — "Budding Habit"
- 🌿 **30-day streak** — "Consistent Grower"
- 🌳 **100-day streak** — "Rooted Habit"
- ⚡ **On-Time Champion** — 10 non-recurring tasks (each with an explicit target date) completed on or before that target date
- 🗓️ **Early Finisher** — 1 task completed strictly before its target date
- 🏆 **Year Finisher** — completions recorded on 365 distinct calendar days within a single calendar year (at least 1 completion per day)

**Lifetime stats**: total ever completed, non-recurring tasks completed on or before target date (count + %), longest streak, unique habits tracked.

**Current-year stats**: tasks completed this calendar year, on-time rate this year (non-recurring tasks only), best streak this year.

**Why this priority**: Gamification features are built on top of the streak infrastructure and the analytics period selector (US3). US3 should ship first; US4 can overlap.

**Independent Test**: Maintain a recurring task for 10 consecutive days, then open the Achievements section — a "Budding Habit" badge must be visible with the task name and earn date. Break the streak, confirm the current-streak counter resets to zero while the badge remains.

**Acceptance Scenarios**:

1. **Given** a recurring task has been completed for 10 consecutive days, **When** Achievements is viewed, **Then** the "Budding Habit" badge is shown with the earning date.
2. **Given** a user has completed 10 non-recurring tasks on or before their explicit target date, **When** Achievements is viewed, **Then** the "On-Time Champion" badge is shown.
3. **Given** a user breaks a streak by missing a day, **When** the streak counter is viewed, **Then** the current streak resets to zero and the all-time longest streak retains its previous value.
4. **Given** the Lifetime stats panel is visible, **When** the user views it, **Then** total completed, on-time rate, longest streak, and unique habits are all displayed.
5. **Given** the current-year stats panel is visible, **When** the user views it, **Then** figures are scoped to 1 Jan – 31 Dec of the current year only.
6. **Given** an award has already been earned, **When** inspected, **Then** the badge is permanently visible regardless of subsequent streak resets.
7. **Given** a task is marked as recurring and the add/edit task dialog is opened, **When** the dialog is visible, **Then** a schedule selector is displayed offering "Every day" (DAILY) or a multi-select of specific weekdays (Mon–Sun); selecting specific days encodes the choice into `recurrenceSchedule`; tasks with no selection default to DAILY.

---

### User Story 5 — Forest Concept for Recurring Habits (Priority: P3)

A user who completes recurring tasks every day wants a deeply visual, emotionally rewarding representation of their consistency. Each completed recurring-task day plants a virtual tree. The Forest section within the Analytics screen shows a growing forest:

- A **tree count** prominently displayed ("Your forest: 247 trees").
- A heatmap-style yearly grid where each cell reflects the number of recurring completions that day, with tree-density shading replacing the plain squares and small tree icons for days with completions.
- Tapping a day cell shows a summary of which recurring tasks were completed that day.
- The same period-selector controls as the Analytics heatmap (current year, last 12 months, a specific past year, lifetime).

**Why this priority**: The Forest is a delight and retention layer built on top of the streak and recurring infrastructure from US4. It can be omitted from an initial release without reducing core utility.

**Independent Test**: Complete three different recurring tasks on the same day. The forest tree count must increase by 3. The heatmap cell for that day must reflect 3 completions. Switching between periods must refilter the cell data and tree count correctly.

**Acceptance Scenarios**:

1. **Given** a user completes one recurring task, **When** the Forest view is opened, **Then** the tree count increments by 1.
2. **Given** three recurring tasks are completed on the same day, **When** the Forest heatmap is viewed, **Then** the cell for that day shows density proportional to 3 completions.
3. **Given** a period is changed from "Current Year" to "Last 12 months", **When** the Forest view reloads, **Then** the tree count and cell data update to reflect only that period.
4. **Given** the "Lifetime" period is selected, **When** the Forest view loads, **Then** the tree count shows the all-time total of recurring-task completions.
5. **Given** a day cell is tapped, **When** the summary appears, **Then** it lists the titles of each recurring task completed that day.

---

### User Story 6 — Task History Editing (Priority: P2)

A user reviewing earlier History entries notices an incorrect completion date. They want to tap the entry and correct the start date, target date, or completion date. After saving:

- If status is changed to TODO or IN_PROGRESS, the task card re-appears on the Home screen according to the standard visibility rules.
- The History screen updates immediately.
- Streak counters and heatmap cells for affected dates are recalculated.

**Why this priority**: Incorrect history dates corrupt streaks and analytics (US3, US4). Editing should be available before gamification stats are heavily used.

**Independent Test**: In History, tap a completed task and change its completion date to yesterday. Save. Confirm the History entry shows yesterday's date. Re-open and change status to TODO. Confirm the card re-appears on the Home screen.

**Acceptance Scenarios**:

1. **Given** a completed task is in History, **When** the user taps it and opens the edit dialog, **Then** start date, target date, and completion date fields are all editable.
2. **Given** the user changes a task's completion date, **When** saved, **Then** the History entry shows the updated date and the heatmap cell for the corrected day reflects the change.
3. **Given** the user changes status from COMPLETED to TODO, **When** saved, **Then** the task card appears on the Home screen (if today or overdue or undated) and disappears from the completed section of History.
4. **Given** the user sets a task's target date to next week and status to TODO, **When** saved, **Then** the task does not appear on today's Home screen but arrives when its target date is reached.
5. **Given** the user sets a completion date earlier than the start date, **When** save is triggered, **Then** a validation error is shown and the save is blocked.
6. **Given** a recurring task with a streak has its completion date edited, **When** saved, **Then** the streak counter is recalculated from the full corrected log.

---

### Edge Cases

- What happens when today has zero tasks with a target date of today? The progress indicator shows a neutral empty state, not 0%.
- What if a task's start date equals its target date? Elapsed percentage is 100%: apply orange urgency colour.
- What if a user attempts to set a completion date in the future? Block with validation: "Completion date cannot be in the future."
- What if a recurring task is logged as completed more than once on the same day (data anomaly)? Count as one tree and one streak day; deduplicate in the query.
- What if the heatmap "Last 12 months" period straddles two calendar years? Display months in chronological order regardless of year boundary.
- What if all lifetime data fits within fewer than 12 months? The "Lifetime" and "Last 12 months" views show identical data; no error or empty state.
- What happens to in-progress award counters when a streak is broken exactly at the milestone boundary (e.g., broken on day 10)? The award requires reaching the milestone before any reset; a streak broken on day 10 (not yet awarded) does not grant the badge.
- What if today is not a scheduled day for a recurring task? The task is not reset to TODO, does not appear on Home, and is not counted toward today's progress or streak.
- What if a recurring task has `recurrenceSchedule = null` (legacy data from before this feature)? It defaults to DAILY behaviour to preserve backward compatibility with all existing recurring tasks.

---

## Clarifications

### Session 2026-02-22

- Q: Should recurring tasks count toward Today's progress? → A: Yes — via `dueDate` set to the scheduled occurrence date on refresh; recurring tasks use `dueDate = today` for DAILY schedule and `dueDate = scheduled weekday` for WEEKLY patterns; FR-001 is unchanged (dueDate = today is the single rule).
- Q: Should the Forest be a separate screen or within Analytics? → A: Tab or scrollable section within the Analytics screen.
- Q: How is "scheduled day" defined for a recurring task's streak? → A: Based on the task's `recurrenceSchedule`; only completions on scheduled occurrence days advance the streak; completing on an unscheduled day has no effect; missing a scheduled day resets current streak to zero.
- Q: Does "Year Finisher" mean 365 distinct days or 365 total completions? → A: 365 distinct calendar days within one calendar year each having ≥ 1 completion.
- Q: Does "On-Time Champion" count recurring tasks? → A: No — only non-recurring tasks with an explicit `dueDate` completed on or before that date.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The Home screen progress indicator MUST count only tasks whose target date is today when calculating the daily completion percentage; recurring tasks automatically receive a target date equal to their scheduled occurrence date on each daily refresh, so they are included by this rule on any day they are scheduled.
- **FR-002**: The progress indicator MUST display a label that explicitly references "today" (e.g., "Today's progress").
- **FR-003**: When no tasks have a target date of today, the progress indicator MUST show a neutral empty state instead of 0%.
- **FR-004**: The progress indicator colour MUST be yellow below 100% and switch to green at exactly 100%.
- **FR-005**: Task cards with both a start date and a target date MUST display an urgency colour: green (0–30 % elapsed), yellow (30–70 %), orange (70–100 %).
- **FR-006**: Urgency colouring MUST NOT apply to completed tasks, tasks already overdue (existing overdue style takes precedence), or tasks missing either date boundary.
- **FR-007**: The Analytics heatmap MUST default to the current calendar year (1 Jan – 31 Dec).
- **FR-008**: The Analytics screen MUST offer four period options: Current Year, Last 12 Months (rolling from today), any specific past year, and Lifetime.
- **FR-009**: All month labels on the heatmap MUST be fully visible and unclipped at all screen widths across all period options.
- **FR-010**: The app MUST track a per-recurring-task current streak and all-time longest streak based on the task's `recurrenceSchedule`; only completions on scheduled occurrence days advance the streak, missing a scheduled day resets the current streak to zero, and completing on an unscheduled day has no streak effect.
- **FR-011**: The app MUST award milestone badges when streak or completion thresholds are reached; awarded badges MUST persist permanently and be displayed in an Achievements section.
- **FR-012**: Lifetime and current-year statistics (total completed, on-time rate, longest streak, unique habits) MUST be displayed in the Analytics screen.
- **FR-013**: The Forest section MUST display a tree count equal to the total recurring-task completions for the selected period.
- **FR-014**: The Forest heatmap MUST use the same four-option period selector (FR-008) and update tree count and cell shading when the period changes.
- **FR-015**: The History screen MUST allow users to edit the start date, target date, and completion date of any task entry.
- **FR-016**: Saving a history edit that sets status to TODO or IN_PROGRESS MUST cause the task card to re-appear on the Home screen according to existing filter rules.
- **FR-017**: Saving any history date change MUST trigger recalculation of streak counters, award eligibility, and heatmap cells for affected dates.
- **FR-018**: The history edit dialog MUST reject saves where the completion date is in the future or is earlier than the start date.
- **FR-019**: The task creation and edit dialog MUST allow a user to specify a recurring task's schedule as DAILY or as specific days of the week (e.g., Mon/Wed/Fri); the selected schedule is stored in the `recurrenceSchedule` field; tasks with `recurrenceSchedule = null` default to DAILY for backward compatibility.

### Additive Logic, Data Integrity, Consistency, Security *(mandatory)*

- **AL-001 (Additive)**: Existing flows affected: Home screen task display and progress ring, History listing, Analytics heatmap and streak display, recurring task status transitions. All must pass non-regression tests after changes.
- **AL-002 (Verification)**: Non-regression suite required for: task card state transitions (TODO → IN_PROGRESS → COMPLETED) unchanged; existing streak increment for unedited tasks unchanged; navigation between Home, History, Analytics unaffected.
- **DI-001 (Integrity)**: Streak counters must stay consistent with the completion log at all times; history edits must trigger full streak recalculation, not a partial update. Total-completed counts must match `isCompleted = true` entries in the log.
- **DI-002 (Migration)**: New persistence for Achievement, TaskStreak, and the `recurrenceSchedule` column on the tasks table requires a schema migration with a version increment; all existing task and log rows must be preserved without modification; existing recurring tasks where `recurrenceSchedule` is null MUST default to DAILY behaviour for backward compatibility.
- **CO-001 (Consistency)**: All new screens and sections follow UI → ViewModel → Repository → DAO; no direct database access from composables or UI state holders.
- **SE-001 (No Secrets)**: No passwords, API keys, tokens, or credentials in any tracked file.
- **SE-002 (No PII)**: No usernames, local machine paths, device serials, or email addresses in tracked files.
- **SE-003 (Safe Queries)**: All database queries MUST use parameterized bindings; no raw string-concatenated query expressions.
- **SE-004 (No Sensitive Logging)**: Task titles and dates MUST NOT be written to Logcat in production builds.

### Key Entities

- **Task**: Existing entity — urgency state is derived (computed from `startDate`/`dueDate`, not stored); `dueDate` is the filter key for daily progress. A new `recurrenceSchedule` field (nullable; values: DAILY or WEEKLY with a day-of-week set, e.g., MON/WED/FRI) is required; on each refresh, recurring tasks receive `startDate` and `dueDate` equal to the current scheduled occurrence date, and tasks whose schedule excludes today are skipped and do not appear on Home.
- **TaskCompletionLog**: Existing entity — completion `date` and `timestamp` fields must be mutable to support history editing; changes trigger streak recalculation.
- **TaskStreak**: New entity — stores `taskId`, `currentStreak` (days), `longestStreak` (days), `longestStreakStartDate`; updated after every completion event or log edit.
- **Achievement**: New entity — stores `type` (award type enum), `taskId` (nullable), `earnedAt` (timestamp), `periodLabel` (e.g., "2026"); one row per earned award instance.
- **AnalyticsPeriod**: UI-layer value type — represents the selected view window (CURRENT_YEAR, LAST_12_MONTHS, SPECIFIC_YEAR, LIFETIME); not persisted.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can identify how many of today's targeted tasks are done at a glance within 2 seconds of opening the Home screen, with no ambiguity about off-day tasks inflating or deflating the figure.
- **SC-002**: Every future-dated task card with both a start and target date displays a distinct urgency colour (green / yellow / orange) that can be verified by creating tasks at known elapsed-percentage positions and confirming the correct colour is rendered.
- **SC-003**: The heatmap month labels are fully readable with no characters clipped at all supported screen sizes and across all four period options.
- **SC-004**: Users can view their completion history across four distinct time windows — current year, last 12 months, a specific past year, and lifetime — with date boundaries that are accurate to within one calendar day.
- **SC-005**: A user who completes a recurring task for 10 consecutive days sees the "Budding Habit" badge in the Achievements section within the same session that the 10th completion is recorded.
- **SC-006**: The Forest tree count increases by exactly 1 per recurring-task completion event, and the heatmap cell for that day reflects the updated density within the same screen render.
- **SC-007**: A user can correct a task's completion date in History and observe the updated date in the History list, the corrected heatmap cell, and the recalculated streak counter — all within the same save operation and without restarting the app.
- **SC-008**: A task whose history-edited status is changed back to TODO re-appears on the Home screen on the very next screen render without requiring an app restart or manual refresh.

---

## Assumptions

- "Target date" and "due date" refer to the same field on the Task entity. "Start date" refers to the existing `startDate` field.
- A task with only a target date (no start date) is not eligible for urgency colouring; existing overdue logic applies when the target date is passed.
- Streak counting uses calendar days in the device's local time zone, not UTC.
- Award earning is additive: earning a 30-day streak badge does not remove the previously earned 10-day badge.
- History editing is permitted for all tasks; editing a recurring task's completion date recalculates its streak from the full corrected log, not just the single edited entry.
- The Forest is a tab or scrollable section within the Analytics screen, sharing the period-selector state with the heatmap for the current screen lifecycle (re-entering the Analytics screen after navigating away resets the period selector to its default; state is not persisted across navigation).
- Lifetime statistics are bounded by the earliest completion log entry and today, not by any calendar year.
- Recurring tasks carry a `recurrenceSchedule` specifying which days they are active (DAILY or specific days of the week). The `refreshRecurringTasks()` logic is extended to: (a) only reset tasks whose schedule includes today, and (b) set both `startDate` and `dueDate` to the current scheduled occurrence date (midnight) on reset.
