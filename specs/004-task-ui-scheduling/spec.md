# Feature Specification: Task UI Polish — Emoji Fixes, Achievement Explanations, Default Times & Recurring Schedules

**Feature Branch**: `004-task-ui-scheduling`  
**Created**: 2026-02-27  
**Status**: Draft  
**Input**: User description: Fix emoji corruption on recurring task cards and How-to-Use screen; add a dedicated Achievements screen (top-bar nav icon) with badge explanations and a hidden-achievements system; set default task start time to exact current clock time and end time to 11:59 PM (recurring daily refreshes use 12:01 AM start); add flexible recurring schedule options (daily, specific weekdays, etc.) following Google Calendar conventions.

---

## Clarifications

### Session 2026-02-28

- Q: How is the dedicated Achievements screen navigated to? → A: New icon added to the top navigation bar, alongside the existing History, Analytics, Help, and Settings icons.
- Q: For new tasks, what is the default start time? → A: Exact current local clock time at the moment the New Task dialog is opened (e.g., 3:15 PM), for both non-recurring tasks and the first creation of a recurring task. For all subsequent daily recurring-refresh instances, the start time resets to 12:01 AM of that day's date. End time always defaults to 11:59 PM of the same day in all cases.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Fix Emoji Rendering on Task Cards and How-to-Use Screen (Priority: P1)

A user who opens the app sees the recurring task card on the Home screen and the
How-to-Use / Onboarding walkthrough. Both currently display garbled special characters
(e.g., `ðŸŒ±`, `â³`) where emoji symbols should appear — a recurring plant icon and a
status hourglass. In the Global History screen the same emoji symbols render correctly.
After this fix, every emoji in the app renders as its intended visual symbol on all
supported Android versions.

**Why this priority**: Broken characters degrade visual quality, undermine trust, and
directly violate Constitution Principle VII (Emoji Non-Negotiable). This regression is
visible to every user on every launch.

**Independent Test**: Open the app, look at any recurring task card — the plant seedling
emoji must appear as 🌱, not as garbled characters. Open the How-to-Use / Onboarding
screens — every emoji therein renders correctly.

**Acceptance Scenarios**:

1. **Given** a recurring task exists, **When** the Home screen is displayed, **Then** the
   plant/seedling icon renders as the correct pictographic emoji (🌱), not as replacement
   characters or multi-byte garble.
2. **Given** a task is In-Progress, **When** the Home screen is displayed, **Then** the
   hourglass status icon renders as the correct emoji (⏳), not as garbled characters.
3. **Given** the user opens the How-to-Use / Onboarding flow, **When** any step with emoji
   content is shown, **Then** all emoji symbols (e.g., 🌱, ⏳, ✅, 🔥, 🚀, 📊) render as
   their intended pictographic symbols.
4. **Given** emoji are displayed anywhere in the app, **When** the screen is rendered,
   **Then** no replacement character (□, ?, garbled bytes) appears in place of an emoji.

---

### User Story 2 — Dedicated Achievements Screen with Explanations and Hidden Surprise Achievements (Priority: P2)

A user who earns the "Early Bird" badge does not understand what it means or how it was
earned. Previously, achievements were displayed inside the Analytics screen. After this
story, achievements move to their own dedicated **Achievements screen**, accessed via a
new icon in the top navigation bar (alongside History, Analytics, Help, and Settings).

On the Achievements screen:
- Every visible achievement card shows a short one-sentence description explaining
  precisely what action triggered it (e.g., "Earned by completing a task before its
  target date").
- A dedicated "How Achievements Work" expandable section explains all unlockable
  (visible) achievements and their exact criteria at a glance.
- Some achievements are intentionally hidden ("surprise" tier) — the system shows a
  locked-mystery-badge placeholder ("??? — Keep going!") without revealing the criteria,
  rewarding discovery.
- The achievements section does not log noise for minor milestones; only meaningful
  thresholds generate a badge card.
- Achievements are **no longer displayed inside the Analytics screen**.

**Why this priority**: Users who understand what they are working towards are more
motivated. Unexplained badges feel like bugs, not rewards. A dedicated screen gives
achievements first-class status and keeps the Analytics screen focused on data.

**Independent Test**: Tap the Achievements icon in the top navigation bar. The dedicated
Achievements screen opens. Each earned badge must display its emoji, name, and a
one-sentence explanation. The expandable "How Achievements Work" section must list all
non-hidden achievements with their unlock criteria. Navigate to Analytics and confirm no
achievement cards or achievement section are present there.

**Acceptance Scenarios**:

1. **Given** the user is on any screen, **When** they tap the Achievements icon in the
   top navigation bar, **Then** the dedicated Achievements screen opens.
2. **Given** a user has earned "Early Bird", **When** the Achievements screen is viewed,
   **Then** the badge shows its emoji, name ("Early Bird"), and the explanation "Complete
   any task before its target date."
3. **Given** a user views the Achievements screen, **When** they tap "How Achievements
   Work", **Then** an expandable section shows all non-hidden badge names and their exact
   unlock criteria.
4. **Given** a hidden achievement has not yet been earned, **When** the Achievements
   screen is viewed, **Then** a locked placeholder "??? — Keep going!" is shown in place
   of the badge name and criteria (criteria MUST NOT be revealed).
5. **Given** a hidden achievement is earned, **When** it unlocks, **Then** the placeholder
   is replaced by the actual badge with its emoji, name, and description — without
   requiring navigation away from the Achievements screen.
6. **Given** a user has no earned achievements, **When** the Achievements screen is
   viewed, **Then** visible achievement placeholders are shown (not an empty screen).
7. **Given** the user is in the Analytics screen, **When** they view it, **Then** no
   achievement cards or achievement section are present — Analytics shows only metrics
   and graphs.

---

### User Story 3 — Sensible Default Start and End Times for New Tasks (Priority: P2)

A user who opens the "New Task" dialog wants the start time to reflect exactly when they
are creating the task (e.g., 3:15 PM today), and the end time to default to the end of
the same day (11:59 PM) — covering the rest of the day without any extra taps.

Recurring tasks behave differently on subsequent daily resets: since a recurring instance
represents "this whole day", its start time resets to 12:01 AM (not the original creation
time) and its end time stays at 11:59 PM of that day.

The user can still change either value manually at any time.

**Why this priority**: Defaulting start to exact current time is honest and immediately
useful — the task starts "now". Defaulting end to 11:59 PM means the task covers the
rest of the current day. For recurring tasks, 12:01 AM on refresh ensures the task spans
the full scheduled day from its beginning.

**Independent Test**: Open "New Task" dialog (non-recurring) at 3:15 PM without changing
anything. The start field must read "3:15 PM" (today's date) and the target field must
read "11:59 PM" (today's date). Create a recurring version of the same task; on the next
daily refresh, confirm the start time has reset to "12:01 AM" of that day and the end
time remains "11:59 PM".

**Acceptance Scenarios**:

1. **Given** the user opens the New Task dialog (non-recurring) at 3:15 PM, **When** no
   date or time is changed, **Then** the start field shows today at the exact current time
   (3:15 PM) and the target field shows today at 11:59 PM.
2. **Given** the user opens the New Task dialog (recurring) at 3:15 PM, **When** no date
   or time is changed on first creation, **Then** the start field shows today at the exact
   current time (3:15 PM) and the target field shows today at 11:59 PM.
3. **Given** the user changes only the start date to a different day, **When** the dialog
   is saved, **Then** the start time component remains the exact clock time captured when
   the dialog was opened (not reset, not re-sampled), applied to the chosen calendar date,
   and the target defaults to 11:59 PM on that same day.
4. **Given** the user clears the target date, **When** the dialog is saved, **Then** no
   target date is stored (remains optional — clearing must still work).
5. **Given** the user has created a recurring task (with any creation-time start), **When**
   the task card refreshes to the next scheduled day, **Then** the refreshed occurrence
   uses 12:01 AM as the start time and 11:59 PM as the end time for that day.

---

### User Story 4 — Flexible Recurring Schedule Options (Priority: P3)

A user who creates a recurring task today has only one option: "Track Streak (Recurring)"
which defaults to daily recurrence. The user wants to schedule recurring tasks on
specific days of the week — for example, every Monday, or every weekday, or every
Tuesday and Thursday — similar to repeating events in Google Calendar.  

After this story, when "Track Streak (Recurring)" is checked, the user sees a compact
day-of-week selector showing Mon–Sun chips. The user can choose:
- **Every day** (default, all chips selected)  
- **Weekdays only** (Mon–Fri quick-select)  
- **Weekends only** (Sat–Sun quick-select)  
- **Custom** — any combination of individual days  

The selected schedule is persisted on the task. On days not in the schedule, the task
is skipped (not reset to TODO) so the streak is not broken unfairly.

**Why this priority**: Daily-only recurrence is the MVP; day-of-week scheduling follows
as the next most-requested calendar pattern. Monthly patterns (e.g., "3rd Tuesday") are
deferred as they require a different data model and are significantly more complex.

**Independent Test**: Create a recurring task with "Mon, Wed, Fri" schedule. On a Tuesday
the task should NOT appear in the Today list / should not reset. On a Wednesday it MUST
appear as TODO.

**Acceptance Scenarios**:

1. **Given** the user checks "Track Streak (Recurring)", **When** the schedule chips are
   shown, **Then** all 7 days are selected by default (daily behaviour unchanged for
   existing tasks).
2. **Given** the user selects "Mon, Wed, Fri" and saves, **When** the app refreshes on
   a Wednesday, **Then** the task is reset to TODO and appears in today's list.
3. **Given** the task has a "Mon, Wed, Fri" schedule, **When** the app refreshes on a
   Tuesday, **Then** the task is NOT reset to TODO and does NOT appear in the Today list;
   the task card is not shown on non-scheduled days (it is skipped entirely, not displayed
   as Completed or In-Progress). The streak counter remains unchanged.
4. **Given** the user taps "Weekdays" quick-select, **When** the chips update, **Then**
   Mon–Fri are all selected and Sat–Sun are deselected.
5. **Given** an existing recurring task created before this feature (no schedule stored),
   **When** it is displayed or refreshed, **Then** it behaves identically to "Every day"
   — no regression to existing daily streaks.
6. **Given** the user tries to save a recurring task with zero days selected, **When** the
   "Add Task" button is pressed, **Then** validation prevents saving and shows an error
   "Select at least one day".
7. **Given** a recurring Mon/Wed/Fri task was marked Completed on Monday, **When** the
   app is opened on Tuesday (a non-scheduled day), **Then** the task does NOT appear
   anywhere in the Today list, and the streak counter value remains exactly what it was
   at end-of-day Monday — a skipped day neither increments nor resets the streak.
8. **Given** an existing recurring task has 5 prior completion log entries and a current
   streak of 5, **When** the user opens EditTaskDialog, changes the schedule from
   "Every day" to "Mon, Wed, Fri", and saves, **Then** all 5 prior completion log entries
   are preserved in the database, the current streak remains 5, and no
   `TaskCompletionLog` rows are deleted or modified as a result of the schedule change.

---

### Edge Cases

- **Emoji on older Android (API 21–23)**: emoji may use font fallbacks; the spec does not
  require vector-quality rendering on API < 24, but MUST NOT show replacement characters
  (□ or garbled bytes) on any supported API level.
- **How-to-Use opened on existing install**: the onboarding flow can be re-triggered via
  the Help action; emoji must render correctly whether this is the first or a subsequent
  view.
- **Default times when date is changed**: if the user changes the start date in the New
  Task dialog (e.g., picks tomorrow), the time component MUST stay at the exact clock
  time captured when the dialog opened — not reset to 12:01 AM and not re-captured as
  the new current time.
- **Recurring task first-creation vs. refresh start time**: the first-ever save of a
  recurring task stores the exact creation-time as `startDate`; every subsequent daily
  refresh overwrites `startDate` to 12:01 AM of that day. These two code paths MUST NOT
  be conflated — applying refresh logic on first-save or creation logic on refresh are
  both bugs.
- **Target date cleared after being set**: clearing the target date must work; no crash
  or stale value.
- **Schedule change on existing task (edit)**: editing the recurrence schedule of an
  existing task MUST NOT delete completed history or reset the current streak.
- **Schedule with all 7 days deselected and saved** (attempted): blocked by validation;
  a recurring task must have at least one scheduled day.
- **Hidden achievement unlocked while screen is open**: the locked placeholder updates
  to the earned badge on the same screen visit without requiring navigation away.
- **Achievement explanation text overflow**: descriptions must be concise enough to fit
  within the card without truncation on a 360 dp wide screen.

---

## Requirements *(mandatory)*

### Functional Requirements

**US1 — Emoji Fixes**:

- **FR-001**: All emoji characters in `HomeScreen` task cards MUST be stored as Unicode
  escape sequences (`\uXXXX`) and render as their intended pictographic symbols on devices
  running Android API 21 and above.
- **FR-002**: All emoji characters in the Onboarding / How-to-Use flow MUST render as
  their intended pictographic symbols; garbled multi-byte sequences are prohibited.
- **FR-003**: Any source file modified as part of this feature that contains emoji MUST
  use a file-encoding-safe representation for emoji characters to prevent future
  file-encoding regressions; raw multi-byte literal emoji in source files are prohibited.

**US2 — Achievement Explanations**:

- **FR-004**: Each achievement card displayed in the dedicated Achievements screen MUST
  show: emoji, name, a one-sentence description of how it is earned, and the date earned.
- **FR-005**: An expandable "How Achievements Work" section MUST be present in the
  Achievements screen, listing every non-hidden achievement type with its exact unlock
  criterion.
- **FR-006**: A defined subset of achievement types MUST be marked as hidden (surprise);
  hidden achievements MUST display only a locked placeholder card ("??? — Keep going!")
  until earned; their criteria MUST NOT be revealed before earning.
- **FR-007**: When a hidden achievement is earned, it MUST transition from the locked
  placeholder to the full badge card (emoji, name, description, date) without requiring
  app restart or navigation away from the Achievements screen.
- **FR-008**: When a user has zero earned achievements, the Achievements screen MUST still
  show unearned (visible) achievement placeholders — the screen MUST NOT be empty or
  display an error state.
- **FR-008a**: The Achievements screen MUST be accessible via a dedicated icon in the top
  navigation bar. The Analytics screen MUST NOT contain any achievement cards or
  achievement sections after this change.

**US3 — Default Task Times**:

- **FR-009**: When the New Task dialog opens, the start date/time MUST default to the
  exact current local time at the moment the dialog is opened (e.g., 3:15 PM). This
  applies to both non-recurring tasks and the first creation of a recurring task.
- **FR-010**: When the New Task dialog opens, the target date/time MUST default to the
  current calendar day at 11:59 PM (23:59 local time), regardless of task type.
- **FR-011**: When editing an existing task that has no previously saved start time, the
  edit dialog MUST default the start time to the exact current local time (not epoch zero,
  not 12:01 AM). When an existing task has no previously saved end time, the edit dialog
  MUST default the end time to 11:59 PM of today.
- **FR-012**: The daily recurring-task refresh (existing `refreshRecurringTasks()`) MUST
  set the refreshed task's `startDate` to today at 12:01 AM (00:01 local time) and (when
  `dueDate` is used) `dueDate` to today at 11:59 PM (23:59 local time). This intentionally
  differs from first-creation defaults: refresh represents "the task starts at the
  beginning of this scheduled day", not "the task starts right now".

**US4 — Recurring Schedule Options**:

- **FR-013**: When "Track Streak (Recurring)" is enabled in the New Task dialog, a
  day-of-week selector MUST appear showing seven day chips (Mon–Sun), all selected by
  default.
- **FR-014**: The selector MUST include two quick-select shortcuts: "Weekdays" (Mon–Fri)
  and "Weekends" (Sat–Sun).
- **FR-015**: The selected schedule (bitmask of days) MUST be persisted on the task using
  the existing schedule field already present in the task data model. No database schema
  migration is required.
- **FR-016**: On daily refresh, a recurring task MUST only reset to TODO if today's day-of-week
  is included in its stored schedule. Tasks NOT scheduled for today MUST keep their
  current state unchanged.
- **FR-017**: An attempt to save a recurring task with zero days selected MUST be blocked
  with a visible validation message; the dialog MUST NOT dismiss.
- **FR-018**: Existing recurring tasks without a stored schedule (null `scheduleMask`) MUST
  continue to behave as "Every day" — no change to their refresh or streak calculation.

### Additive Logic, Data Integrity, Consistency, Security *(mandatory)*

- **AL-001 (Additive)**: Impacted flows — Home screen task card rendering; Onboarding
  flow; dedicated Achievements screen (new); Analytics screen (achievement section
  removed); top navigation bar (new Achievements icon); New Task / Edit Task dialogs;
  daily recurring task refresh. Each must be verified by automated tests after changes.
- **AL-002 (Verification)**: Non-regression verified by: existing unit tests for
  `achievementEmoji()` passing; new navigation test for Achievements screen top-bar icon;
  new unit tests for default-time helpers (current-time path and 12:01 AM refresh path
  separately); new unit tests for `refreshRecurringTasks()` schedule filtering; existing
  Tier 2 task-card and analytics UI tests passing.
- **DI-001 (Integrity)**: `scheduleMask` invariant — must be `null` OR an integer
  1–127 (at least one of 7 bits set). Saving `0` (zero days selected) is invalid and
  must be rejected at the UI layer before any write.
- **DI-002 (Migration)**: No schema migration required. `scheduleMask` column already
  exists in `tasks` table (added in feature 002). The `addTask` and `updateTask`
  repository methods already accept `scheduleMask`. No DB version bump needed.
- **CO-001 (Consistency)**: Default-time logic MUST live in a pure helper function
  (testable without Android context), not inline in the Compose dialog. Schedule
  validation logic MUST live at the ViewModel layer, not in the UI layer.
- **SE-001 (No Secrets)**: No credentials or API keys introduced.
- **SE-002 (No PII)**: No personal information in tracked files.
- **SE-003 (Safe Queries)**: No raw SQL changes; all queries use existing parameterized
  Room annotations.
- **SE-004 (No Sensitive Logging)**: No task content written to Logcat in release builds.

### Key Entities

- **TaskEntity** (existing): `scheduleMask: Int?` column used by US4. `null` = daily
  (all days). `1–127` = bitmask where bit 0 = Monday … bit 6 = Sunday.
- **AchievementEntity** (existing): unchanged schema. US2 adds description strings as
  UI-layer constants only — no new columns.
- **ScheduleOption** (new, UI-only value type): represents the day-of-week selection in
  the task creation dialog; converts to/from an `Int?` bitmask before persistence.
- **AchievementVisibility** (new, UI-only enum): `VISIBLE` (shown before earning) vs
  `HIDDEN` (locked placeholder until earned). Lives in the presentation layer only.
- **AchievementsScreen** (new Composable): dedicated top-level screen displaying all
  badge cards and the "How Achievements Work" expandable section. Registered in
  `NavGraph` with its own route. Replaces the achievements section previously embedded
  in the Analytics screen.

---

## Assumptions

- Monthly recurrence patterns (e.g., "every 3rd Tuesday") are explicitly out of scope
  for this feature. A separate `scheduleType` field and additional data model work would
  be required; deferred.
- The existing `scheduleMask` bitmask convention (bit 0 = Monday … bit 6 = Sunday)
  established in feature 002 is retained unchanged.
- "How-to-Use" and "Onboarding" refer to the same `OnboardingFlow` composable that is
  accessible both at first launch and from the Help action.
- The hidden achievement tier initially includes at least one type as a surprise. The
  exact types designated as hidden are recorded in the implementation plan and tested;
  the spec does not hard-code them to allow implementation flexibility.
- "Minimum code changes" principle (Constitution IX) applies: the schedule day-selector
  is a compact component reusing the existing chip/checkbox pattern; no third-party
  calendar library is introduced.
- The top navigation bar currently holds 5 icons (Timer, Analytics, History, Help,
  Settings — per feature 001). Adding the Achievements icon brings the count to 6;
  whether to replace the Analytics icon or relabel an existing slot is an
  implementation decision deferred to the plan phase.
- For recurring tasks, start-time defaults are dual-mode by design: first creation stores
  the exact clock time; every daily refresh thereafter overwrites `startDate` to
  12:01 AM. These are two distinct code paths in `TaskRepositoryImpl` / refresh logic.
- **US3 timezone scope**: Default-time acceptance scenarios (US3) are scoped to
  the device's local timezone. The UTC-N cross-midnight timezone conversion is handled
  by the existing `utcDateToLocalMidnight()` helper (established in feature 003,
  FAIL-003 fix) and is not re-tested in US3 acceptance scenarios. Any date picker
  confirm handler that sets `dueDate` or `startDate` MUST route through
  `utcDateToLocalMidnight()` per DEC-002 before calling `endTimeForDate()` or
  `mergeDateTime()` — this invariant is assumed satisfied without an additional
  timezone-specific acceptance scenario in this spec.

---

## Non-Goals

The following are **explicitly out of scope** for this feature and must not be
implemented:

1. **Monthly / custom-interval recurrence** (e.g., "every 3rd Tuesday", "every 2 weeks",
   "first weekday of the month") — requires a new `scheduleType` field and a separate
   data model change; deferred to a future feature.
2. **Per-task push notifications or reminders** — no notification scheduling for task
   start/due times is introduced by this feature.
3. **Cloud sync or cross-device achievement progress** — achievements remain local-only;
   no backend integration.
4. **Achievement leaderboards, social sharing, or external export** of badge data.
5. **Redesign of the Analytics screen** beyond removing the achievement section — the
   Analytics screen layout, charts, and stats are unchanged.
6. **Batch or template task scheduling** — a recurring pattern applies per individual
   task only.
7. **Emoji rendering improvement on API < 24** — the spec does not require
   vector-quality rendering on API levels below 24, only that no replacement character
   (`□`, `?`, garbled bytes) appears.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every emoji visible on the Home screen recurring task cards and the
  Onboarding / How-to-Use screens renders as its intended pictographic symbol — zero
  garbled-character occurrences on all supported Android versions.
- **SC-002**: Every earned achievement badge in the dedicated Achievements screen displays
  a non-empty, human-readable description sentence for all achievement types. The
  Analytics screen contains no achievement cards.
- **SC-003**: 100% of new tasks created via the New Task dialog have a default start time
  equal to the exact current local clock time at dialog open and a default end time of
  11:59 PM when the user makes no manual time selection. Recurring task daily refreshes
  use 12:01 AM as the start time (not the original creation time).
- **SC-004**: A recurring task scheduled for specific days (e.g., Mon/Wed/Fri) does NOT
  appear in Today's task list on unscheduled days, correctly preserving the streak.
- **SC-005**: All existing app functionality continues to work correctly after all
  changes — zero regressions in any user flow.
