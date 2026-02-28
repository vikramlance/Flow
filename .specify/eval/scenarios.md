# Evaluation Scenarios

> Concrete test scenarios the Reviewer AI uses to stress-test specs, plans, and code.
> Each scenario probes a known failure mode from the project's history or common Android pitfalls.

---

## How to Use

When reviewing an artefact, the Reviewer AI selects the relevant scenario category
and asks: "Does this artefact handle this scenario? If not, is it in scope?"

---

## S1 — Timezone & Date Scenarios

| # | Scenario | Expected Outcome |
|---|----------|-----------------|
| S1.1 | User in UTC-8 picks "Feb 23" in date picker at 11 PM local. | Stored date = Feb 23 00:00 UTC-adjusted local midnight. |
| S1.2 | User in UTC+5:30 (India) picks "Feb 23" at 1 AM local. | Same — Feb 23 local midnight stored. |
| S1.3 | User flies from NYC to Tokyo; opens app next morning. | Recurring reset triggers based on device's new timezone; today's tasks are correct. |
| S1.4 | Device clock is manually set to a past date. | App does not crash. Recurring reset handles past dates gracefully (no double-reset). |

## S2 — Task Lifecycle Edge Cases

| # | Scenario | Expected Outcome |
|---|----------|-----------------|
| S2.1 | User taps a completed task back to TODO, then closes app. | Completion log entry for today is deleted. Task shows as TODO on next open. |
| S2.2 | User completes a recurring task, then the midnight reset fires. | Completion log row preserved; task status resets to TODO; streak counter increments. |
| S2.3 | User creates a task with no due date, completes it. | Task appears in history under "General" (no date group). |
| S2.4 | User deletes a task that has 30 completion log entries. | All 30 log rows cascade-deleted. No orphan data. |
| S2.5 | Two tasks have the same title. | Both display independently — no dedup, no collision. |

## S3 — UI Rendering Scenarios

| # | Scenario | Expected Outcome |
|---|----------|-----------------|
| S3.1 | Device uses a custom font that lacks emoji glyphs. | App uses system emoji font fallback; emoji still renders correctly. |
| S3.2 | Screen rotates while a bottom sheet is open. | Sheet state survives rotation; no crash, no data loss. |
| S3.3 | User enables "Display size: Largest" in Android settings. | All cards, buttons, and text remain accessible — no clipping, no overlap. |
| S3.4 | Dark mode toggled while app is in foreground. | Theme switches immediately; no flicker, no state loss. |

## S4 — Data & Persistence Scenarios

| # | Scenario | Expected Outcome |
|---|----------|-----------------|
| S4.1 | App killed mid-transaction (e.g., during task save). | Room transaction rolls back; no partial writes. |
| S4.2 | Database upgraded from v6 to v7 with existing data. | Migration runs; no data loss; all old tasks queryable. |
| S4.3 | Empty database — fresh install, zero tasks. | All screens render gracefully with empty states. No crashes on empty lists. |
| S4.4 | Database has 1000 tasks with 10,000 log entries. | Home screen loads in <500ms. Analytics doesn't ANR. |

## S5 — Spec Drift Scenarios (Meta)

| # | Scenario | Expected Outcome |
|---|----------|-----------------|
| S5.1 | Plan adds a feature not mentioned in the spec. | Reviewer rejects plan — "not in spec, add to spec first or move to Non-Goals." |
| S5.2 | Task changes a method signature that other tasks depend on. | Reviewer flags ordering dependency — "task X must complete before task Y." |
| S5.3 | Code implements a different UX flow than the acceptance scenarios describe. | Reviewer rejects code — "scenario says X, code does Y." |
| S5.4 | Test passes but doesn't actually assert the acceptance criteria. | Reviewer rejects test — "assertion doesn't match Given/When/Then #N." |
