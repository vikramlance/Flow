# Feature Specification: Fix Analytics Statistics & History State Sync

**Feature Branch**: `006-fix-analytics-stats`  
**Created**: 2026-02-28  
**Status**: Draft  
**Input**: User description: "on the analytics Screen User need to see the statistics for example in graph section it should show how many total number of tasks completed this year or on specific date or or time or even specific year. Currently it is showing only completed task that are recurring tasks. it is not considering one time tasks that are completed. for example today I completed 4 recurring task and two non recurring tasks,then it is showing total 4 and on time 4, Actually it should add both the recurring and non recurring tasks so total 6 and on time 6. Verify the logic how its calculated and shown. Another issues when I edit any recurring task from history and change status from completed to to do it is shown on the home screen but it is not getting removed from the history of completed task, ideally when it goes to to-do list or in progress then it should show on the home screen but it should also be removed from the history the history should only show task that are in completed state. It's working correctly for non recurring tasks. It is also Working correctly for all types of tasks when we did the status from completed to do from the home screen. it is correctly getting removed from the history. issue is offering when we directly update the status from history itself. This is also the reason why it is showing total task completed as 4 in the first example I gave where I completed 4 recurring task and 2 non recurring tasks,then it is showing total 4 completed tasks in the analytics. Issue also occurs with missed account where it only shows total non recurring task that are missed. for example if three recurring tasks are missed and two non recurring tasks are missed then in the analytics history it is showing only two tasks are missed. Do not break any existing functionality. Test it thoroughly. Add comprehensive test cases. think critically and deeply. follow all instructions from all important files."

## Clarifications

### Session 2026-02-28

- Q: Is each recurring task occurrence stored as its own independent row with its own status field, or is a single rule record stored and instances derived at runtime? → A: Each recurring task occurrence is stored as its own independent row with its own status field. Aggregation queries can therefore treat recurring instances identically to one-time tasks.
- Q: Does the History screen show Completed tasks only, or both Completed and Missed tasks? → A: History shows **Completed tasks only**. Tasks in Missed status are never displayed in the history list.
- Q: Should the reactive removal from history also apply when a Missed recurring task's status is changed from the History screen? → A: Missed tasks are never shown in history so the question does not apply in the general case. However, a task that previously held Missed status but was subsequently transitioned to Completed **is** shown in history (current status = Completed). When such a task's status is changed from Completed to To Do or In Progress directly from the history screen, it follows exactly the same removal behavior as any other Completed task — it is removed from history and added to the Home screen.
- Q: Is the per-occurrence scheduled due time stored as a field on each instance record, or derived at runtime from the parent recurrence rule? → A: Due time is stored per instance record. No runtime derivation from the recurrence rule is required to classify a completion as on-time.
- Q: Should only the three reported metrics (completed, on-time, missed) be fixed, or should all aggregate metrics on the Analytics screen be audited for the same recurrence-type exclusion bug? → A: All aggregate metrics on the Analytics screen must be audited and fixed if affected by the same bug.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Accurate Completed Task Counts in Analytics (Priority: P1)

As a user viewing the Analytics screen, I want the completed task statistics (graphs, totals, on-time counts) to reflect **all** completed tasks — both recurring and one-time — so that the numbers I see accurately represent my productivity.

**Why this priority**: Analytics totals are the central value of the analytics screen. Undercounting completed tasks directly undermines user trust and gives a false picture of productivity. This is the highest-impact defect.

**Independent Test**: Can be fully tested by completing a mix of one-time and recurring tasks on the same day, then opening the Analytics screen and verifying that the displayed totals equal the sum of both task types.

**Acceptance Scenarios**:

1. **Given** a user has completed 4 recurring tasks and 2 one-time tasks today, **When** the user opens the Analytics screen and views today's statistics, **Then** the total completed count shows 6 and the on-time count reflects the combined on-time completions of both task types.

2. **Given** a user views the analytics graph for a specific date, **When** that date has both recurring and one-time tasks in completed state, **Then** the graph bar and tooltip/count include contributions from both task types.

3. **Given** a user views the analytics summary for a specific month or year, **When** that period contains a mix of completed recurring and one-time tasks, **Then** the aggregate totals include every completed task regardless of recurrence type.

4. **Given** no tasks of either type are completed on a given date, **When** the user browses that date in analytics, **Then** the count shows 0 (no false positives from either task type).

---

### User Story 2 - History List Reflects Live Task State (Priority: P1)

As a user editing a recurring task's status directly from the Task History screen, I want the history list to immediately remove that task entry when I change its status away from "Completed" — matching the existing correct behavior for one-time tasks and for status changes made from the Home screen.

**Why this priority**: Stale completed entries in the history list directly cause incorrect analytics counts (the root cause of the inflated "missed" and "completed" totals for recurring tasks). Fixing history state also restores data integrity.

**Independent Test**: Can be fully tested by finding any completed recurring task in the history list, editing its status to "To Do" or "In Progress" directly from the history screen, then verifying: (a) the task disappears from history, and (b) the task appears on the Home screen.

**Acceptance Scenarios**:

1. **Given** a recurring task appears in the completed history list, **When** the user opens that task from history and changes its status to "To Do", **Then** the task is removed from the history list immediately and appears in the Home screen to-do list.

2. **Given** a recurring task appears in the completed history list, **When** the user opens that task from history and changes its status to "In Progress", **Then** the task is removed from the history list immediately and appears in the Home screen in-progress list.

3. **Given** a completed one-time task in history (already working), **When** the user changes its status from history, **Then** existing correct behavior is preserved — the task is removed from history and routed to the appropriate Home screen section.

4. **Given** a recurring task's status is changed from the history screen back to "Completed", **When** the user views the history list, **Then** the task remains in (or reappears in) the history list.

5. **Given** a recurring task's status is changed from the Home screen (already working), **When** the user views the history list, **Then** the task is correctly removed from history (existing behavior must remain intact).

---

### User Story 3 - Accurate Missed Task Counts in Analytics (Priority: P2)

As a user viewing the Analytics screen, I want the "missed tasks" statistics to include all missed tasks — both recurring and one-time — so that I have a complete and accurate picture of tasks I did not complete.

**Why this priority**: Missed task counts are secondary to completed counts but share the same root cause (task-type filtering). Fixing missed counts restores full analytics accuracy.

**Independent Test**: Can be fully tested by having 3 recurring tasks and 2 one-time tasks in missed state for any period, then opening the Analytics screen and verifying the missed total shows 5.

**Acceptance Scenarios**:

1. **Given** 3 recurring tasks and 2 one-time tasks are in a missed state for a given period, **When** the user views the Analytics screen for that period, **Then** the missed tasks count shows 5.

2. **Given** only recurring tasks are missed on a given day, **When** the user views analytics for that day, **Then** the missed count reflects the recurring tasks (previously showed 0).

3. **Given** only one-time tasks are missed on a given day, **When** the user views analytics for that day, **Then** the missed count continues to reflect those tasks (existing behavior preserved).

---

### Edge Cases

- What happens when a recurring task instance has no associated date entry (orphaned record)? The system must handle gracefully without crashing and exclude such entries from counts.
- What happens when a recurring task's status is updated from both the History screen and the Home screen in close succession? The last write wins; resulting state must be consistent between history list and analytics counts.
- What happens when a task is changed from "Completed" back to "Completed" (no-op edit) from the history screen? The task must remain in history and not be duplicated.
- What happens when the analytics period spans a schema migration boundary where older records exist? Counts must remain accurate with no double-counting.
- What happens when there are zero tasks of either type for a given analytics period? Totals must display 0 without errors.
- How does the system handle a recurring task series where only some instances are completed? Each instance is counted individually; completing one instance does not affect analytics for other instances of the same series.
- What happens when a task that was previously in Missed status is later marked Completed and the user then changes its status back to To Do or In Progress from the History screen? The task must be removed from history and added to the Home screen to-do or in-progress list — the prior Missed state is irrelevant; only the current status at the moment of the edit governs history membership.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The analytics completed task count MUST aggregate all tasks in "Completed" state regardless of whether they are recurring or one-time tasks.
- **FR-002**: The analytics on-time count MUST aggregate all tasks completed on time regardless of recurrence type.
- **FR-003**: The analytics missed task count MUST aggregate all tasks in "Missed" state regardless of recurrence type.
- **FR-004**: All analytics time-range views (daily, monthly, yearly, custom date) MUST apply FR-001 through FR-003 consistently.
- **FR-005**: When a user changes the status of a recurring task from "Completed" to any non-completed state (To Do, In Progress) directly from the History screen, the system MUST remove that task instance from the history list immediately without requiring a manual refresh.
- **FR-006**: When a recurring task is removed from history due to a status change (FR-005), the system MUST add it to the appropriate section on the Home screen (To Do or In Progress) immediately.
- **FR-007**: The history list MUST only display task instances whose **current** status is "Completed". Tasks in Missed, To Do, or In Progress status MUST never appear in the history list. A task instance that previously held Missed status but has since been transitioned to Completed is shown in history because its current status is Completed; if the user then changes that task's status from Completed to any non-completed state (To Do, In Progress) directly from the history screen, it MUST be removed from history and routed to the Home screen using the same mechanism as every other Completed task status change.
- **FR-008**: The existing correct behavior for one-time tasks (changing status from history removes them from history and adds them to Home screen) MUST be fully preserved. *(Mandated by AL-001; acceptance: T034 passes unchanged.)*
- **FR-009**: The existing correct behavior for all task types when status is changed from the Home screen MUST be fully preserved. *(Mandated by AL-001; acceptance: T035 passes unchanged.)*
- **FR-010**: The six known aggregation bugs (heatmap, total completed, on-time count, lifetime stats, current-year stats, missed count) in the data layer MUST be corrected to include both recurring and one-time task records.
- **FR-011**: In addition to the six known bugs corrected by FR-001–FR-003 and FR-010, ALL remaining aggregate metrics displayed on the Analytics screen — including but not limited to completion rate percentages, streaks, averages, and category or tag breakdowns — MUST be audited for the same recurrence-type exclusion root cause. Any metric found to be affected MUST be corrected within this feature branch.

### Additive Logic, Data Integrity, Consistency, Security *(mandatory)*

- **AL-001 (Additive)**: Changes MUST NOT break existing working flows: one-time task history removal on status change, status changes from Home screen for all task types, and any analytics filters already functioning correctly.
- **AL-002 (Verification)**: Non-regression verification MUST cover: (a) one-time task status change from history still removes from history; (b) all task type status changes from Home screen still update history correctly; (c) analytics period filters (daily/monthly/yearly) continue to function; (d) existing graph rendering is unaffected by the data layer fix.
- **DI-001 (Integrity)**: The task status stored in the data layer MUST be the single source of truth for both the history list display and the analytics counts; there MUST be no separate cached or derived status that can diverge from the stored value.
- **DI-002 (Migration)**: If the root cause requires a schema or query change, the change MUST be backward-compatible with existing stored task records; no data loss is permitted.
- **CO-001 (Consistency)**: All fixes MUST follow the established data flow boundary: UI layer → ViewModel → Repository → data/storage layer. No status mutation logic may live exclusively in the UI layer.
- **SE-001 (No Secrets)**: No passwords, API keys, tokens, or credentials in any tracked file — including code examples in markdown.
- **SE-002 (No PII)**: No usernames, local machine paths, device serials, or email addresses in any tracked file — including fenced code blocks in markdown files.
- **SE-003 (Safe Queries)**: All database queries MUST use parameterized bindings; raw concatenated query strings are prohibited.
- **SE-004 (No Sensitive Logging)**: Sensitive user data MUST NOT be written to logs in production builds.

### Key Entities *(include if feature involves data)*

- **Task Instance**: Represents a single occurrence of either a one-time task or a single scheduled instance of a recurring task series. Each occurrence is stored as an independent row with its own status field (To Do, In Progress, Completed, Missed) and its own scheduled due-time field. Status is the single source of truth for history list membership and analytics aggregation. Due time is stored per instance — no runtime derivation from a parent recurrence rule is required.
- **Recurring “Missed” representation**: Recurring tasks have no `MISSED` status enum value in the `tasks` table. Their missed state is inferred at query time from `task_logs` rows where `isCompleted = 0` and `date < todayMidnight`. FR-003 references this inferred state when it requires counting “all tasks in Missed state.”
- **Recurring on-time boundary**: Recurring tasks are scheduled with `dueDate = midnight + 86 340 000 ms` (23 h 59 m) by `refreshRecurringTasks`. The on-time classification for recurring completions therefore uses `timestamp ≤ date + 86 340 000` in `task_logs`, which equals the stored per-instance due time. This is consistent with the clarification that due time is stored per instance.
- **Recurrence Type**: A property classifying a task as one-time or recurring. Analytics and history filtering logic MUST treat both types identically when computing counts and determining history list membership.
- **History Entry**: A filtered view of task instances limited to those whose **current** status is exactly "Completed". Tasks in Missed, To Do, or In Progress state are excluded. The view must be derived live from the stored status — not a separate persisted list — so that any status change (from any screen) is immediately reflected. A task that previously transitioned through Missed before reaching Completed is treated identically to any other Completed task for all history display and removal purposes.
- **Analytics Aggregate**: A calculated count (completed, completed on-time, missed) over task instances for a given time period. Must query across all recurrence types without implicit exclusion.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When a user completes N recurring tasks and M one-time tasks in any given period, the analytics completed count for that period equals exactly N + M with zero discrepancy.
- **SC-002**: When a user completes N recurring tasks and M one-time tasks on time in a given period, the analytics on-time count equals exactly the combined on-time completions across both task types.
- **SC-003**: When P recurring tasks and Q one-time tasks are missed in a given period, the analytics missed count equals exactly P + Q with zero discrepancy.
- **SC-004**: After changing a recurring task's status from "Completed" to "To Do" or "In Progress" directly from the History screen, the task disappears from the history list within the same user action — no manual refresh required.
- **SC-005**: After the status change in SC-004, the task appears correctly in the matching section on the Home screen within the same navigation flow.
- **SC-006**: All existing automated tests continue to pass after the fix is applied (zero regressions in the previously-passing test suite).
- **SC-007**: New comprehensive automated tests covering the acceptance scenarios for all three user stories achieve a 100% pass rate on the first stable run.
- **SC-008**: The FR-011 analytics audit is documented with findings; every affected metric is corrected and verified accurate via automated or manual tests before the branch is considered complete.

## Assumptions

- Analytics counts are computed by querying or aggregating task instance records filtered by status and date range; the root cause is a query or aggregation that inadvertently excludes one-time task records (e.g., an implicit join or filter on recurrence type).
- The history screen inconsistency for recurring tasks is caused by a status-update code path in the History screen that persists the new status but does not trigger the same reactive list update that the Home screen code path does.
- "On time" is defined as a task completed before or at its scheduled due time; this definition applies equally to recurring and one-time tasks.
- "Missed" is defined as a task that passed its due time without reaching "Completed" state; this definition applies equally to both task types.
- Each recurring task occurrence is stored as an independent row in the data layer with its own status field and its own scheduled due-time field (confirmed). Aggregation queries can therefore treat recurring instances identically to one-time tasks once the erroneous type filter is corrected. The scheduled due time does not need to be derived at runtime from a parent recurrence rule.
- The fix does not require any new user-visible UI elements; it corrects data accuracy in existing displays only.
