# Feature Specification: App Icon, Task History & Help Access

**Feature Branch**: `001-task-history-help`  
**Created**: 2026-02-21  
**Status**: Ready for Planning  
**Input**: User description: "App icon update with PNG logo, task completion history with persistent color-coded status display, and help icon for onboarding instructions re-access"

---

## Clarifications

### Session 2026-02-21

- Q: How should recurring task history be preserved when the task resets to TODO at midnight? → A: Reuse the existing `TaskCompletionLog` table — each daily completion writes a log entry (same as streak tracking); history queries combine `TaskCompletionLog` for recurring tasks and `TaskEntity.completionTimestamp` for general/dated tasks.
- Q: What triggers the recurring task daily reset to TODO? → A: Lazy reset on app open — when the app is opened, if the last-reset date differs from today, all recurring tasks are reset to TODO and their completions logged before reset.
- Q: How should the top bar handle 5 icons (Timer, Analytics, History, Help, Settings)? → A: All 5 icons remain visible in the top bar as direct icon buttons — no overflow menu.
- Q: How should users select a specific date on the History screen? → A: A horizontal scrollable date strip at the top; dates with data are highlighted (neon green tint/dot), dates with no data have no highlight. Users also toggle between date-grouped view and a flat chronological list (most-recent-completed first).
- Q: If a user cycles a completed task back to TODO, what happens to its history entry? → A: The history entry is removed (deleted). History only reflects tasks that remain in a completed state.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1  Task Status Persistence on Home Screen (Priority: P1)

A user taps a task card to cycle it from TODO  In Progress  Completed. Currently, once a task reaches Completed (neon green), it vanishes from the home screen. The user wants completed tasks to remain visible on the home screen for the current day, so they can see at a glance what they have finished and what remains  without items jumping around or changing position.

**Why this priority**: This is the core usability bug reported by the user. The home screen should act as a single source of truth for today's work. Removing completed tasks causes confusion and a sense of lost work. Fixing this also unlocks the history foundation.

**Independent Test**: Can be fully tested by adding 3 tasks, completing them one by one, and verifying all 3 remain visible (now in neon green) in their original positions on the home screen.

**Acceptance Scenarios**:

1. **Given** a task card in TODO state on the home screen, **When** the user taps it once, **Then** the card turns yellow (In Progress) and stays in the same grid position.
2. **Given** a task card in In Progress state, **When** the user taps it again, **Then** the card turns neon green (Completed) and stays in the same grid position  it does NOT disappear.
3. **Given** a completed task (neon green) on the home screen, **When** the user taps it again, **Then** the card cycles back to TODO state (no color, thick border), remaining in the same position.
4. **Given** tasks in mixed states on the home screen, **When** any task changes status, **Then** no task card changes its position in the grid.
5. **Given** a task whose target date has passed and it is not completed, **When** the home screen is viewed, **Then** that task's card appears with an orange border/color to indicate overdue.

---

### User Story 2  Completed Task History View (Priority: P2)

A user wants to look back on previous days and see which tasks they completed. They need a history screen where they can browse completed tasks grouped by date, with the option to filter either by the date a task was completed or by the task's original target date.

**Why this priority**: Once completed tasks stay on the home screen for that day, users will want a way to view older completed tasks. This creates the full feedback loop: complete today, review yesterday.

**Independent Test**: Can be fully tested by completing tasks on two different days, navigating to the History screen, and verifying tasks appear grouped under their respective dates with correct filtering.

**Acceptance Scenarios**:

1. **Given** the user is on the home screen, **When** they tap the History icon in the top bar, **Then** they are taken to a History screen that lists completed tasks grouped by date (most recent first).
2. **Given** the History screen is open, **When** the user selects a specific date, **Then** only tasks completed on that date (or with that target date, depending on filter mode) are shown.
3. **Given** the History screen is open, **When** the user switches the filter between "Completed On" and "Target Date", **Then** the list re-groups tasks accordingly.
4. **Given** a task was completed, **When** that task is shown in history, **Then** the task title, original target date, and completion time are all visible.
5. **Given** no tasks exist for a selected date, **When** the user selects that date, **Then** a friendly empty-state message is shown.
6. **Given** the user is on the History screen, **When** they tap the back button, **Then** they return to the home screen.

---

### User Story 3  App Icon Update (Priority: P3)

The app currently uses a broken placeholder PNG icon. The user has provided a custom PNG logo (`flow_logo.png`) featuring a dark background, neon green wave-line motif, and "Flow" text. This icon should replace all app launcher icons, correctly displaying across all Android screen densities and adaptive icon shapes (circle, rounded square, etc.).

**Why this priority**: Brand identity matters for user confidence, but it is a visual-only change that does not affect any functional user flow.

**Independent Test**: Can be fully tested by installing the updated APK on a device and verifying the launcher icon displays the neon green wave logo at all icon sizes (including rounded, circle crop, and full-bleed adaptive shapes).

**Acceptance Scenarios**:

1. **Given** the app is installed, **When** the user views their device home screen, **Then** the app icon shows the neon green wave + "Flow" text on a dark background.
2. **Given** different Android launchers that apply circular or rounded-square crops, **When** the icon is displayed, **Then** no key visual element is clipped; the wave and "Flow" text remain visible.
3. **Given** the icon is viewed at various sizes (notification, app drawer, home screen), **Then** the logo remains crisp and legible at all sizes.

---

### User Story 4  Help Icon for Onboarding Re-Access (Priority: P4)

After completing onboarding for the first time, there is no way to re-read the instructions. The user wants a persistent Help icon in the top bar that re-launches the onboarding instructions overlay at any time. Additionally, the current onboarding overlay blocks all interaction  the user cannot dismiss it by tapping outside the card. This should be fixed so tapping outside the instructions card closes the overlay.

**Why this priority**: Discoverability and usability  users who forget how the app works have no recourse. Non-critical for first-time users who just went through onboarding.

**Independent Test**: Can be fully tested by completing onboarding, tapping the Help icon in the top bar to re-open the overlay, and confirming it dismisses both via "Let's Go!" and by tapping outside the card.

**Acceptance Scenarios**:

1. **Given** the user is on the home screen, **When** they look at the top bar, **Then** a Help icon (question mark) is visible alongside the existing Timer, Stats, and Settings icons.
2. **Given** the user taps the Help icon, **When** the overlay appears, **Then** the same 4-step onboarding flow is shown from step 1.
3. **Given** the onboarding overlay is open (first launch or via Help icon), **When** the user taps anywhere outside the instruction card, **Then** the overlay is dismissed immediately.
4. **Given** the overlay is dismissed partway through by tapping outside, **When** the user taps the Help icon again, **Then** the overlay restarts from step 1.
5. **Given** the user is on first launch and has not completed onboarding, **When** they tap outside the card, **Then** the overlay is still dismissed (no forced completion).

---

### Edge Cases

- What happens when a task has no target date (`dueDate == null`)  should it appear on the home screen every day until completed, or only once completed?
- What if the user has hundreds of completed tasks  does the home screen become unwieldy? Home screen shows only today's tasks; older completed tasks are only visible in the History screen.
- What happens with the PNG at different resolutions? The provided `flow_logo.png` must be scaled to all required mipmap density sizes; the PNG raster approach ensures full fidelity (including the "Flow" text) at every density without any format conversion.
- What happens when the History screen is opened and there are zero completed tasks? Show a friendly empty-state prompt ("No completed tasks yet. Keep going!").
- What happens if the user opens history and filters by a date with no data? Show an empty-state message for that specific date.

---

## Requirements *(mandatory)*

### Functional Requirements

**Home Screen Task Display**

- **FR-001**: The home screen MUST display all tasks whose `dueDate` falls on the current calendar day, regardless of their completion status (TODO, In Progress, Completed all visible).
- **FR-002**: Tasks are divided into two undated categories:
  - **Recurring tasks** (marked as recurring): MUST appear on the home screen every day. They MUST auto-reset to TODO status when the app is opened on a new calendar day (lazy reset — on each app open the app compares the stored last-reset date to today; if different, the reset runs). Before the reset, the previous day's completion MUST be recorded as a new entry in `TaskCompletionLog`. Each day's completion is stored as a separate log entry, enabling per-day history browsing.
  - **General tasks** (not recurring, no `dueDate`): MUST remain visible on the home screen until they are completed. Once completed, the task MUST be removed from the home screen (not visible the next day) and appear only in History under the date it was completed.
- **FR-003**: Task cards MUST NOT change their grid position when the user changes a task's status (TODO  In Progress  Completed  TODO).
- **FR-004**: A Completed task card MUST display with a neon green filled background and remain visible on the home screen; it must not disappear upon completion.
- **FR-004b**: If a completed task is tapped again to cycle back to TODO, its history entry MUST be removed: for general/dated tasks the `completionTimestamp` is cleared; for recurring tasks the `TaskCompletionLog` entry for that day is deleted. History only reflects tasks that remain in a completed state.
- **FR-005**: An In Progress task card MUST display with a yellow filled background.
- **FR-006**: A TODO task card MUST display with no fill color but with a clearly visible thick card border (2dp) in Soft Lavender/Dusty Purple so it is distinct from the background.
- **FR-007**: An overdue task (not completed, `dueDate` has passed) MUST display with an orange 2dp border.

**Task History Screen**

- **FR-008**: The app MUST provide a History screen showing all tasks that have ever been completed. It MUST support two presentation modes the user can toggle between:
  - **Date-grouped view**: tasks grouped under date headers, most recent date first.
  - **Chronological list view**: a flat list of all completed tasks ordered by most-recently-completed first, with the completion date/time shown inline on each row.
- **FR-009**: The History screen MUST support two grouping modes: "Completed On" (group by the date the task was marked complete) and "Target Date" (group by the task's original `dueDate`).
- **FR-010**: The History screen MUST be accessible via a dedicated new icon (e.g., clock or history icon) added to the home screen top bar. The existing per-task streak screen (accessible via the recurring streak count on a task card) MUST remain unchanged and separate.
- **FR-011**: Each history entry MUST display the task title, the original target date (if set), and the completion date/time.
- **FR-012**: The History screen date selector MUST be a horizontal scrollable date strip displayed at the top of the screen. Dates that contain completed task entries MUST be visually highlighted with a distinct color (e.g., neon green accent dot or tinted background). Dates with no completed task data MUST appear without any highlight, making it immediately clear which days have history to review.
- **FR-012b**: Selecting a highlighted date on the strip filters both the date-grouped view and the chronological list to show only tasks from that day. Tapping the same selected date again clears the date filter.
- **FR-012c**: A permanent **Show All** control (e.g., a chip or button labelled "Show All" always visible at the leading end of the date strip) MUST display all completed tasks as a single flat chronological list, regardless of date. This is distinct from and independent of date selection — tapping "Show All" always clears any active date filter and shows the entire history in chronological (most-recent-first) order.
- **FR-013**: The History screen MUST show a friendly empty-state message when no history data exists for the current filter selection (e.g., "No completed tasks on this day yet.").

**App Icon**

- **FR-014**: The app launcher icon MUST be updated to display the design from `flow_logo.png` (located at `app/src/main/res/mipmap-hdpi/flow_logo.png`): dark background (`#0A0A0A`), neon green (`#39FF14`) flow/wave lines, and the "Flow" text label. The PNG MUST be scaled and placed at all required mipmap density resolutions. For API 26+ the adaptive icon MUST use a solid `#0A0A0A` background drawable and a `<bitmap>` foreground drawable referencing the PNG, so the full design (including the "Flow" text) is preserved without any format conversion.
- **FR-015**: The icon MUST be implemented as an Android Adaptive Icon (foreground + background layers) to ensure correct rendering across all launcher shapes.
- **FR-016**: The icon MUST be provided at all required density resolutions to render crisply on all Android devices.

**Help / Onboarding Re-Access**

- **FR-017**: A Help icon (question mark or info icon) MUST be permanently visible in the home screen top bar as the 5th icon button, alongside the existing Timer, Analytics, History, and Settings icons. All 5 icons MUST be directly tappable without any overflow menu.
- **FR-018**: Tapping the Help icon MUST open the existing 4-step onboarding overlay from step 1.
- **FR-019**: The onboarding overlay MUST be dismissible by tapping anywhere outside the instruction card.
- **FR-020**: Outside-tap dismissal MUST work whether the overlay was triggered by first launch or by the Help icon.

---

### Additive Logic, Data Integrity, Consistency *(mandatory)*

- **AL-001 (Additive)**: New behavior MUST NOT break existing user flows; impacted flows: task status cycling, recurring streak tracking (`TaskCompletionLog`), daily progress percentage, timer panel, analytics heatmap, per-task streak history screen (`task_streak/{taskId}`).
- **AL-002 (Verification)**: Non-regression check: (a) recurring tasks still accumulate streaks when completed; (b) daily progress % reflects today's tasks vs. completed correctly under the new visibility logic; (c) the existing per-task streak screen continues to function; (d) Settings screen navigation is unaffected.
- **DI-001 (Integrity)**: History uses two complementary sources of truth:
  - **General/dated tasks**: `TaskEntity.completionTimestamp` is the record of completion. It MUST be set when Completed and MUST be cleared (set to null) when cycled back to TODO.
  - **Recurring tasks**: `TaskCompletionLog` records each daily completion (one entry per task per day). An entry MUST be written when the recurring task is completed. If the task is cycled back to TODO on the same day before the next app-open reset, that day's `TaskCompletionLog` entry MUST be deleted.
  - In both cases, reverting from COMPLETED to TODO removes the associated history entry, ensuring history only reflects tasks that remain completed.
- **DI-002 (Migration)**: No new Room schema columns are required (all needed fields exist). If a new DAO query or index is added for history performance, it MUST be applied via a safe incremental Room migration. No `fallbackToDestructiveMigration` is permitted per the project constitution.
- **CO-001 (Consistency)**: All new UI composables MUST interact only with ViewModels; ViewModels MUST call Repositories; Repositories MUST call DAOs. No direct DAO imports in the Presentation layer.

---

### Key Entities *(include if feature involves data)*

- **TaskEntity**: Existing entity; key fields: `id`, `title`, `dueDate` (target date, optional), `status` (TODO / IN_PROGRESS / COMPLETED), `completionTimestamp` (date/time when task was completed, optional). No new data fields needed.
- **TaskCompletionLog**: Existing entity for recurring streak tracking; doubles as the history source for recurring tasks. One log entry is created per task per day upon completion. If cycled back to TODO that same day, the entry is deleted. Both the streak counter and the History screen read from this table for recurring tasks.
- **Completed Task History View**: A logical read-only union of two sources: `TaskCompletionLog` entries (recurring task completions) and `TaskEntity` records where `completionTimestamp` is set (general/dated task completions). Results support date-grouped presentation (most recent date first) and flat chronological list presentation (most recently completed item first), with optional date-strip filtering.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can complete all of today's tasks and verify all completed cards remain visible on the home screen without any card disappearing during that session.
- **SC-002**: A user can navigate to the History screen and find a completed task from any previous day within 2 taps from the home screen.
- **SC-003**: Switching the history filter mode and selecting a specific date takes under 3 seconds with up to 500 historical tasks.
- **SC-004**: The app launcher icon matches the `flow_logo.png` design (dark `#0A0A0A` background, neon green `#39FF14` wave lines, "Flow" text) on all tested device launchers and icon shapes.
- **SC-005**: After completing onboarding, a user can re-open the instructions within 1 tap from the home screen at any time.
- **SC-006**: Tapping outside an open onboarding card dismisses the overlay 100% of the time with no forced step progression.
- **SC-007**: Task cards do not reorder during a user session involving status changes across 10 or more tasks.

---

## Assumptions

- **A-001**: Undated tasks behave differently based on type:
  - **Recurring tasks**: Appear on the home screen every day. They reset to TODO when the app is opened on a new calendar day (lazy reset on app open). Each prior day's completion is saved to `TaskCompletionLog` before the reset. This models a daily habit loop.
  - **General tasks** (non-recurring, no `dueDate`): Appear on the home screen until completed. Once marked complete they leave the home screen and are stored in History under their completion date. They do NOT reappear.
- **A-002**: The home screen "today's tasks" view shows: (a) all tasks with `dueDate` equal to today's date (any status), (b) all recurring tasks without a `dueDate` (reset daily), and (c) all non-recurring general tasks that have not yet been completed.
- **A-003**: The app icon will be derived from the provided `flow_logo.png` (at `app/src/main/res/mipmap-hdpi/flow_logo.png`) and produced in the Android Adaptive Icon format (PNG bitmap foreground layer + solid dark background layer). The PNG already contains the complete design including wave lines and "Flow" text — no format conversion is required. The foreground layer is implemented as a `<bitmap>` drawable XML referencing the PNG; density-specific copies are placed in each `mipmap-*` folder at the correct dp-scaled pixel dimensions.
- **A-004**: The existing per-task streak History screen (`task_streak/{taskId}`, accessed via the  icon) remains unchanged. The new History screen is a separate global completed-tasks view.
- **A-005**: Daily progress percentage continues to be calculated based on tasks due today (matching the home screen filter logic), not all tasks ever created.
