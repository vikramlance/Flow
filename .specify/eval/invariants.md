# Evaluation Invariants

> Runtime and design invariants the Reviewer AI checks on every review pass.
> These are stricter than contracts — a single violation blocks approval.

---

## Structural Invariants

| ID | Invariant | Severity |
|----|-----------|----------|
| INV-01 | The app has exactly one `@Database` class (`AppDatabase`). | BLOCK |
| INV-02 | Every `@Dao` interface is referenced in `AppDatabase`. | BLOCK |
| INV-03 | Every `@HiltViewModel` has a matching `@Inject constructor`. | BLOCK |
| INV-04 | Navigation graph contains no duplicate route strings. | BLOCK |
| INV-05 | Every Repository interface has exactly one `Impl` class bound in a Hilt module. | BLOCK |

## Behavioural Invariants

| ID | Invariant | Severity |
|----|-----------|----------|
| INV-10 | Completing a task does not remove it from the home screen on the same day. | BLOCK |
| INV-11 | Recurring tasks reset to TODO on app-open when `lastResetDate != today`. | BLOCK |
| INV-12 | Task status cycle is exactly: TODO → In Progress → Completed → TODO. | BLOCK |
| INV-13 | Date picker selection in any timezone produces the correct local calendar date. | BLOCK |
| INV-14 | All emoji render as pictographic symbols, never as replacement characters. | BLOCK |

## Data Invariants

| ID | Invariant | Severity |
|----|-----------|----------|
| INV-20 | `TaskEntity.dueDate` is always `null` or a UTC-midnight-aligned millis value. | BLOCK |
| INV-21 | `TaskCompletionLog.completionDate` is always UTC-midnight-aligned millis. | BLOCK |
| INV-22 | Deleting a task cascades to its completion logs. | BLOCK |
| INV-23 | No two `TaskCompletionLog` rows share the same `(taskId, completionDate)` pair. | BLOCK |

## Process Invariants

| ID | Invariant | Severity |
|----|-----------|----------|
| INV-30 | No code is written before a spec exists in `specs/`. | BLOCK |
| INV-31 | No plan is written before the spec passes `spec-review.md`. | BLOCK |
| INV-32 | No task is started before the plan passes `plan-review.md`. | BLOCK |
| INV-33 | `failure-log.md` is updated within the same session that discovers a failure. | WARN |
| INV-34 | `decisions.md` is updated before the code that implements the decision. | WARN |
| INV-35 | Internal temporary scripts (`.sh`, `.ps1`) are never placed at the repository root; they must be kept in `.specify/scripts/backup/`. | WARN |
