# Feature Specification: Comprehensive UI Bug Fixes and Improvements

**Feature Branch**: `001-ui-bug-fixes`  
**Created**: 2026-02-25  
**Status**: Draft  
**Input**: User description: "Fix recurring task time defaults, non-recurring end date defaults, progress percentage calculation, task history deduplication, analytics data accuracy, heat map date range, settings focus timer live update and completion sound, emoji display regression, background color, app icon centering, recurring streak heat map visibility, and TDD non-regression enforcement."

## Clarifications

### Session 2026-02-25

- Q: When a task is re-completed (Completed → In Progress → Completed again), how should the history store manage its record? → A: Upsert by task ID — history store holds at most one row per task; status changes overwrite the existing record in place. Zero duplicate rows are ever possible.
- Q: What does "date" refer to in the history screen — the date on a task card, or a UI element controlling which tasks are shown? → A: The "date box" is a **date filter row at the top of the history screen** showing individual completed dates as selectable filter chips. (1) This filter row must update immediately when any task's completion date changes. (2) If a task's completion date is moved to a past date, that past date must appear as a selectable chip in the filter row, provided it falls within the currently supported date range of the history view.
- Q: Which Android audio stream should the Focus Timer completion sound use? → A: Notification stream — plays at notification volume, fully suppressed by device silent mode and Do Not Disturb.
- Q: Should the Analytics summary count and graph share the same data fetch, or keep separate queries with cross-validation? → A: Consolidate to one query — both summary text and graph draw from the same computed query result, making discrepancy structurally impossible.
- Q: What exact hex value should the softer background color use? → A: #121212 — the canonical Material Design 3 dark surface color.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recurring Task Time Boundaries (Priority: P1)

A user creates or views a recurring task. The task should span the full day — from the first moment of the day (12:01 AM) to the last moment (11:59 PM) on its scheduled date. Currently the task erroneously defaults to midnight start and midnight end (effectively zero duration), making the task invisible in time-based views and causing scheduling conflicts.

**Why this priority**: Directly affects task scheduling correctness. Zero-duration tasks cause broken UX across timeline views, progress calculation, and streak logic.

**Independent Test**: Create a new recurring task and verify that start time defaults to 12:01 AM and end time defaults to 11:59 PM of the same date without any user intervention.

**Acceptance Scenarios**:

1. **Given** a user creates a new recurring task, **When** the task is saved without modifying time, **Then** the start time is set to 12:01 AM and end time is set to 11:59 PM of today's date.
2. **Given** an existing recurring task displayed in a list, **When** the task card is rendered, **Then** the time range shown reflects 12:01 AM to 11:59 PM.
3. **Given** two recurring tasks on the same date, **When** both are viewed in a timeline, **Then** neither overlaps incorrectly due to midnight default times.

---

### User Story 2 - Non-Recurring Task End Date Default (Priority: P1)

A user creates a new non-recurring task. The end date should default to the same day as today (matching the same convention as recurring tasks — 11:59 PM same day), while the start date behavior remains unchanged. The user can freely modify the end date if needed.

**Why this priority**: Consistency in task creation defaults reduces friction and user-error. Mismatched defaults cause tasks to not appear in today's progress calculation.

**Independent Test**: Create a new non-recurring task, do not change the end date, and verify it defaults to today's date at 11:59 PM.

**Acceptance Scenarios**:

1. **Given** a user opens the new non-recurring task form, **When** the form is presented, **Then** the end date field defaults to today's date at 11:59 PM.
2. **Given** the default end date is set, **When** the user taps the end date and changes it, **Then** the new date and time are persisted correctly.
3. **Given** the start date behavior, **When** the form is opened, **Then** the start date behavior is unchanged from the current working implementation.

---

### User Story 3 - Today's Progress Percentage Accuracy (Priority: P1)

A user views the top progress bar on the home screen. The percentage must accurately reflect all tasks (both recurring and non-recurring) whose end date falls on today. Only tasks with an end date matching today contribute to the "today's progress" count and percentage calculation.

**Why this priority**: The progress bar is the primary motivational UI element. Incorrect calculation directly undermines the user's sense of accomplishment.

**Independent Test**: Add 2 recurring tasks and 2 non-recurring tasks all ending today; complete 2 of them and verify the top bar shows 50%.

**Acceptance Scenarios**:

1. **Given** a mix of recurring and non-recurring tasks all ending today, **When** the home screen is viewed, **Then** all such tasks are counted in the denominator of the progress percentage.
2. **Given** a task with an end date of tomorrow, **When** the home screen is viewed today, **Then** that task does NOT contribute to today's progress percentage.
3. **Given** all today's tasks are completed, **When** the home screen is viewed, **Then** the progress bar shows 100%.
4. **Given** no tasks end today, **When** the home screen is viewed, **Then** the progress bar shows 0% or a neutral empty state without crashing.

---

### User Story 4 - Task History Deduplication (Priority: P2)

A user views the task history screen. Each task appears exactly once in history, showing only its most recent/ultimate state. If a task is moved back to "In Progress" or "To Do", it is removed from history and shown only on the home screen. A completed task with an end date of today appears on both the home screen and in history.

**Why this priority**: History cluttered with duplicate state-change records is confusing and untrustworthy. A single source of truth per task is essential.

**Independent Test**: Move a task from To Do → In Progress → Completed → In Progress; confirm it no longer appears in history and appears correctly on the home screen. Also complete a task, then re-complete it on a different date; confirm the history row is updated in place (upsert) with the new date and the filter row chip for the new date appears immediately.

**Acceptance Scenarios**:

1. **Given** a task that has been moved through multiple statuses, **When** history is viewed, **Then** only one entry for that task exists (upserted by task ID), reflecting the current state.
2. **Given** a completed task with today's end date, **When** history is viewed, **Then** it appears in history AND on the home screen.
3. **Given** a completed task moved back to "In Progress", **When** history is viewed, **Then** it no longer appears in history.
4. **Given** a completed task with a past end date (not today), **When** history is viewed, **Then** it appears in history only (not on home screen).
5. **Given** a completed task, **When** the date it was completed is shown in history, **Then** the date shown is the most recent completion date (the actual date the user last marked it complete), not any prior state-change date.
6. **Given** tasks completed on multiple different dates, **When** the history screen is opened, **Then** the date filter row at the top displays a chip for each distinct completion date within the supported range.
7. **Given** the date filter row is displayed, **When** a task is newly completed or a completion date is changed, **Then** the filter row updates immediately to reflect the new date chip without requiring a screen reload.
8. **Given** a task whose completion date is moved to a past date within the supported history range, **When** the history screen is shown, **Then** that past date appears as a selectable chip in the filter row.

---

### User Story 5 - Analytics Completed Count Accuracy (Priority: P2)

A user views the Analytics screen. The total completed tasks count displayed and the value shown in the bar/graph are consistent and drawn from the same data source. There is no discrepancy between summarized totals and chart values.

**Why this priority**: Data inconsistency destroys trust in the analytics feature.

**Independent Test**: Complete exactly 5 tasks, open analytics, and verify both the summary count and the graph bar reflect exactly 5 completed tasks.

**Acceptance Scenarios**:

1. **Given** 14 tasks completed this year, **When** the analytics screen is viewed, **Then** both the "completed" counter and the graph show 14.
2. **Given** the analytics screen is open, **When** a new task is completed, **Then** both the summary count and the graph reflect the new count consistently.
3. **Given** a time period filter is changed (e.g., this month vs this year), **When** the filter is applied, **Then** both the text summary and graph respond to the same filter.

---

### User Story 6 - Heat Map Default Date Range (Priority: P2)

A user views the general contribution heat map. By default, the heat map shows from January 1st of the current year up to today's date. Months after the current month are not shown. When the user explicitly selects "Last Year", the full prior year is displayed. When a specific year is selected, that entire year is shown.

**Why this priority**: Showing empty future months wastes screen space and causes visual confusion.

**Independent Test**: Open the heat map in February; verify only January and February columns are visible by default. Switch to "Last Year" and verify all 12 months of the prior year are shown.

**Acceptance Scenarios**:

1. **Given** the current date is in February, **When** the heat map is opened with default settings, **Then** only January and February are displayed.
2. **Given** the user selects "Last Year", **When** the heat map renders, **Then** 12 months of the previous year are shown.
3. **Given** the user selects a specific past year, **When** the heat map renders, **Then** all 12 months of that year are shown.
4. **Given** it is January 1st, **When** the heat map is opened, **Then** only January is shown without layout errors.

---

### User Story 7 - Focus Timer Live Settings Update and Completion Sound (Priority: P2)

A user changes the default Focus Timer duration in Settings. The change is reflected immediately in the active timer screen without requiring an app restart. Additionally, when a focus timer session completes, a pleasant, non-harsh audio chime plays to notify the user.

**Why this priority**: Requiring an app restart to apply settings is a UX defect. The completion sound provides essential feedback for timed focus sessions.

**Independent Test**: Set timer to 10 minutes in Settings, navigate to timer screen immediately (without restarting), and verify it shows 10 minutes.

**Acceptance Scenarios**:

1. **Given** the Focus Timer is set to 25 minutes in Settings, **When** the user navigates to the timer screen without restarting the app, **Then** the timer shows 25 minutes.
2. **Given** the timer duration preference is changed while the timer is idle, **When** the user opens the timer, **Then** the new duration is immediately reflected.
3. **Given** a focus timer session completes (reaches zero), **When** the time runs out, **Then** a pleasant completion sound plays.
4. **Given** the device is on silent/vibrate mode, **When** the timer completes, **Then** the sound behavior respects device audio settings without forced override.

---

### User Story 8 - Emoji Display Correctness (Priority: P1)

All emojis used in the app (e.g., the plant/tree icon for recurring tasks, and all other emoji-decorated UI elements) display correctly as intended emojis, not as replacement characters, boxes, or garbled special characters. This regression was introduced in a recent change and must be fully fixed.

**Why this priority**: Broken characters degrade visual quality and signal a defect to users. Emojis are core visual language in the UI.

**Independent Test**: Open the recurring task section and verify the plant/tree emoji renders correctly as an emoji character, not a special character or box.

**Acceptance Scenarios**:

1. **Given** a recurring task is displayed, **When** the task card is rendered, **Then** the plant/tree emoji appears as the correct Unicode emoji, not a replacement character.
2. **Given** any screen with emoji-decorated labels or icons, **When** those screens are opened, **Then** all emojis render as their intended visual symbols.
3. **Given** a device running API 24+, **When** emoji content is rendered, **Then** no tofu boxes or special characters appear in place of emojis.

---

### User Story 9 - Background Color Refinement (Priority: P3)

A user uses the app in a dark environment. The background color is adjusted from pure black to a softer, eye-friendly dark shade, reducing eye strain without losing the dark theme feel.

**Why this priority**: Visual comfort improvement; does not affect functionality.

**Independent Test**: Open any primary screen and verify the background is visibly a softer dark shade rather than pure black.

**Acceptance Scenarios**:

1. **Given** the app is opened, **When** any main screen is displayed, **Then** the background is a soft dark shade (not pure black).
2. **Given** the updated background color, **When** text and UI elements are rendered on it, **Then** contrast ratios remain accessible (WCAG AA minimum).

---

### User Story 10 - App Icon Centering Fix (Priority: P3)

A user views the app launcher icon. The icon's text label and wave graphics are fully contained within the icon boundary — nothing is cut off. The icon appears centered and balanced at all standard Android launcher icon sizes.

**Why this priority**: The launcher icon is the first impression of the app. Cut-off artwork is unprofessional.

**Independent Test**: Install the app on a device and view the launcher icon; verify text and waves are not clipped on any edge.

**Acceptance Scenarios**:

1. **Given** the app is installed, **When** the launcher icon is viewed, **Then** all text and wave elements are fully visible and centered within the icon boundary.
2. **Given** multiple Android launcher icon sizes, **When** the icon is rendered, **Then** no clipping or overflow occurs at any density.

---

### User Story 11 - Recurring Task Streak Heat Map Visual (Priority: P2)

A user views the streak heat map within a recurring task. The filled heat map cells use a visibly darker neon green color (higher contrast). The heat map shows current year only, from January to the current month — no empty future months are displayed. The visual style matches the general contribution heat map.

**Why this priority**: Poor color visibility and empty future months reduce the motivational impact of streak tracking.

**Independent Test**: Complete a recurring task streak entry, open the task's streak heat map, and verify: (a) the cell color is a clearly visible darker neon green, and (b) no months beyond the current month are shown.

**Acceptance Scenarios**:

1. **Given** a recurring task with completed streak entries, **When** the streak heat map is viewed, **Then** the colored cells use a darker, more visible neon green shade.
2. **Given** the current month is February, **When** the streak heat map renders, **Then** only January and February are displayed.
3. **Given** the streak heat map, **When** it renders, **Then** its visual presentation style (cell size, layout, spacing) matches the general contribution heat map.

---

### Edge Cases

- What happens when a recurring task is created on December 31st — end time 11:59 PM must not roll over to January 1st of next year.
- What happens when a non-recurring task's default end date is set but the user is near a timezone midnight boundary — end time must use local device time.
- What happens when a task is completed and immediately moved back to "To Do" multiple times — history must remain clean with zero duplicate entries.
- What happens if there are no tasks ending today — progress bar shows 0% or empty state without crashing or division-by-zero.
- What happens when analytics are viewed and a task status changes concurrently — displayed counts must be consistent with stored data.
- What happens if the device's audio is completely muted when the focus timer ends — graceful silent completion, no crash.
- What happens when emoji characters are used on Android API levels at the minimum supported version — no garbled characters.
- What happens on the heat map when the current day is January 1st — only one partial month visible without layout errors.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When a new recurring task is created, the system MUST default the start time to 12:01 AM and end time to 11:59 PM of the task's scheduled date.
- **FR-002**: When a new non-recurring task is created, the system MUST default the end date to today's date at 11:59 PM; the start date behavior MUST remain unchanged from current implementation.
- **FR-003**: The top progress bar MUST include both recurring and non-recurring tasks with an end date matching today in the percentage calculation.
- **FR-004**: Only tasks whose end date equals today MUST be included in the today's progress denominator and numerator.
- **FR-005**: The task history store MUST maintain exactly one record per task, keyed by task ID, using an upsert strategy; no two rows for the same task may coexist.
- **FR-005a**: The history record for a task is overwritten in place on re-completion; the stored completion date reflects the most recent time the user marked the task complete.
- **FR-006**: A task that has been moved back from "Completed" to "In Progress" or "To Do" MUST have its history record deleted; it MUST appear on the home screen only.
- **FR-007**: A completed task with today's end date MUST appear in both the history screen and the home screen.
- **FR-008**: A completed task with a past end date MUST appear in history only, not on the home screen.
- **FR-008a**: The history screen MUST display a date filter row at the top, showing one selectable chip per distinct completion date for all tasks in the supported history date range.
- **FR-008b**: The date filter row MUST update immediately (reactively) whenever a task's completion date is added, changed, or removed — no manual screen refresh required.
- **FR-008c**: If a task's completion date is set to a past date that falls within the supported history range, that past date MUST appear as a selectable chip in the filter row.
- **FR-009**: The Analytics screen MUST compute both the summary completed count text and the chart/graph data from the same single query result; the two values MUST be structurally impossible to diverge (not two separate queries cross-validated, but one shared data fetch used for both renderings).
- **FR-010**: The general contribution heat map MUST default to showing January of the current year through the current month only; future months MUST NOT be rendered by default.
- **FR-011**: When "Last Year" is selected in the heat map, the full 12 months of the prior year MUST be displayed.
- **FR-012**: When a specific year is selected in the heat map, all 12 months of that year MUST be displayed.
- **FR-013**: A change to the Focus Timer default duration in Settings MUST be reflected immediately in the timer screen without requiring an app restart.
- **FR-014**: When a Focus Timer session reaches zero, the system MUST play a pleasant, non-harsh audio completion sound using the **notification audio stream**.
- **FR-015**: Because the notification stream is used, the sound MUST be fully suppressed when the device is in silent mode or Do Not Disturb; no forced audio override is permitted.
- **FR-016**: All emoji characters used throughout the app MUST render as their intended visual emoji symbols on all supported devices.
- **FR-017**: The emoji correctness principle MUST be codified as a non-negotiable rule in the project's constitution file, requiring any code change that processes emoji content to include a test asserting correct rendering.
- **FR-018**: The app's primary background color MUST be changed from pure black (#000000) to **#121212** (Material Design 3 dark surface baseline), which maintains WCAG AA contrast ratios for all overlaid text.
- **FR-019**: The launcher app icon MUST display all text and wave graphic elements fully contained within the icon boundary, centered, at all standard Android icon densities.
- **FR-020**: The recurring task streak heat map MUST use a darker, more visible neon green color for completed streak cells.
- **FR-021**: The recurring task streak heat map MUST show only the current year from January through the current month; future empty months MUST NOT be rendered.
- **FR-022**: The recurring task streak heat map visual style MUST match the general contribution heat map in terms of cell layout and spacing.
- **FR-023**: All changes MUST be covered by unit tests, integration tests, system tests, and device/instrumentation tests; no existing passing test MUST be broken.

### Additive Logic, Data Integrity, Consistency, Security *(mandatory)*

- **AL-001 (Additive)**: New behavior MUST NOT break existing user flows; impacted flows: task creation (recurring & non-recurring), home screen progress bar, history screen, analytics screen, general heat map, settings, focus timer, recurring task detail/streak, launcher icon.
- **AL-002 (Verification)**: Non-regression verification MUST be ensured via automated test suites (unit, integration, device/instrumentation) that run on every change; all existing tests must pass.
- **DI-001 (Integrity)**: Task time boundaries (start 12:01 AM, end 11:59 PM) must be stored consistently; existing tasks with midnight-to-midnight times must not be silently altered without a documented, safe migration path.
- **DI-002 (Migration)**: If any existing task records require time field corrections, a safe, reversible migration strategy with validation steps must be defined before applying changes.
- **CO-001 (Consistency)**: All changes must follow the established architecture: UI → ViewModel → Repository → Storage.
- **SE-001 (No Secrets)**: No passwords, API keys, tokens, or credentials in any tracked file.
- **SE-002 (No PII)**: No usernames, local machine paths, device serials, or email addresses in tracked files.
- **SE-003 (Safe Queries)**: All database queries MUST use parameterized bindings; raw concatenated SQL is prohibited.
- **SE-004 (No Sensitive Logging)**: Sensitive user data MUST NOT be written to logs in production builds.
- **NC-001 (Emoji Non-Negotiable)**: Unicode emoji characters MUST always render as their intended visual symbols. Any code change that processes, serializes, or transmits emoji string content MUST include a specific automated test asserting correct emoji rendering. This is a non-negotiable principle to be recorded in the project constitution file.

### Key Entities *(include if feature involves data)*

- **Task**: Represents a unit of work with status, start time, end time, scheduled date, and type (recurring/non-recurring). Constraint: start time < end time; end date determines today-eligibility for progress bar and history. *(Terminology note — I3: the storage field `dueDate` in `TaskEntity` is a UTC Long timestamp encoding both date and time. The terms "end date" (used in US2, FR-002) and "end time" (used in US1, FR-001) are synonymous — both refer to this same `dueDate` field.)*
- **TaskHistory**: A single-entry-per-task log of completed tasks, keyed by task ID (upsert strategy). Contains: task reference, scheduled end date, and actual completion timestamp (the last time the user marked the task complete). Must never hold more than one row per task. Row is deleted when task is moved back to non-completed status.
- **TaskProgress**: Derived view over all tasks whose end date equals today, used for top bar percentage calculation. Includes both recurring and non-recurring tasks.
- **StreakEntry**: A date-keyed record within a recurring task representing a completed streak day, rendered in the streak heat map.
- **FocusTimerPreference**: User-set default duration for the focus timer. Must be observed reactively by the timer screen so changes apply immediately without restart.
- **HeatMapEntry**: A date-keyed record of task completions used by the general contribution heat map.

---

## Assumptions

1. "Today's date" is determined by the device's local date at the time the home screen or calculation is rendered.
2. Existing tasks already stored with midnight-to-midnight times will not be auto-migrated unless a separate migration story is approved; the fix applies to newly created tasks only.
3. The completion sound is a short chime (under 3 seconds) bundled with the app, not streamed from the internet. It plays via the Android notification audio stream and is subject to device silent mode and Do Not Disturb suppression.
4. The background color is exactly **#121212** (Material Design 3 dark surface). No further design review is required for this value.
5. WCAG AA contrast ratio (4.5:1 for normal text, 3:1 for large text) is the minimum accessibility standard for the background color change.
6. Emoji rendering targets the app's existing minimum supported Android API level.
7. The app icon fix targets the standard adaptive icon format used by the app.
8. The focus timer settings change must be reactive using the existing architecture's state observation pattern (StateFlow/LiveData or equivalent).

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of newly created recurring tasks default to 12:01 AM start and 11:59 PM end without user intervention, verified by automated test.
- **SC-002**: 100% of newly created non-recurring tasks default end date to today at 11:59 PM, verified by automated test.
- **SC-003**: The top progress bar percentage is mathematically consistent with a manual count of completed tasks ending today, with zero discrepancy across all test scenarios.
- **SC-004**: The history store contains exactly one row per task ID in all automated test scenarios (upsert verified); zero duplicate rows observed. The date filter row shows the correct distinct set of completion date chips immediately after any completion event.
- **SC-005**: The Analytics completed count and chart value for the same time period match exactly across all tested scenarios (zero discrepancy).
- **SC-006**: The heat map never renders future months beyond the current month in the default view, confirmed by automated rendering test on any given date.
- **SC-007**: A settings change to Focus Timer duration is reflected in the timer screen within the same app session (without restart), measured as immediate on navigation.
- **SC-008**: The timer completion sound plays within 1 second of the timer reaching zero, confirmed on device instrumentation test.
- **SC-009**: Zero emoji display defects (replacement characters, boxes, garbled text) found across all app screens in automated and manual review.
- **SC-010**: The app launcher icon is fully contained and centered with no visual clipping at any standard Android icon density, confirmed by visual inspection and adaptive icon preview tool.
- **SC-010a**: All primary screen backgrounds measure as #121212 in a color picker tool after the change, with zero screens retaining the pure black (#000000) value.
- **SC-011**: The recurring streak heat map colored cells are visually distinguishable and clearly visible in both bright and dim ambient conditions, confirmed by contrast ratio check.
- **SC-012**: All existing automated tests continue to pass after all changes are applied (zero regression failures).
- **SC-013**: New tests added for each bug fix result in a measurable net increase in test coverage for affected modules.
