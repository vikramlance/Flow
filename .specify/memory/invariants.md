# Invariants — Non-Negotiable Rules

> These invariants are **never** overridden by a spec, plan, or task.
> The Reviewer AI must reject any artefact that violates even one.

---

## I. Data Integrity

1. **Dates are UTC-midnight millis — with one exception for `dueDate`.** Every `Long` stored as a date in Room MUST pass through `normaliseToMidnight()` before insert/update. No raw `System.currentTimeMillis()` for date fields. **Exception (004-task-ui-scheduling, DEC-004)**: `TaskEntity.dueDate` MAY be stored as an end-of-day value (`normaliseToMidnight(t) + 86_399_999` = 23:59:59.999 PM) when supplied by `AddTaskDialog` via `defaultEndTime()`. This is intentional per US3. **Corollary**: ALL DAO queries filtering by `dueDate` MUST use range checks `[dayStart, dayEnd]`, never exact-midnight equality. The legacy `getTasksDueOn()` exact-equality query in `TaskDao` is retained for backward compatibility but MUST NOT be used by new code or the repository.
2. **Completion log is append-only during a day.** A `TaskCompletionLog` row may be deleted only if the parent task is cycled back to TODO on the same calendar day. Cross-day deletions are forbidden.
3. **No orphan logs.** Deleting a `TaskEntity` must cascade-delete its `TaskCompletionLog` entries.

## II. UI Consistency

4. **Emoji Non-Negotiable (Constitution VII, updated 2026-02-28).** Every emoji in source MUST render as its intended pictographic symbol on API 24+. Emoji MUST be represented as encoding-safe Unicode surrogate-pair escapes (e.g., `\uD83C\uDF31`) — **NOT** raw multi-byte literal characters in source files — to prevent file-encoding regressions. Replacement characters (□, ?, garbled bytes) are prohibited on any supported API level. Reviewer checklist: grep source files for raw multi-byte emoji bytes (e.g., `ðŸ`) rather than valid `\uXXXX` escape sequences.
5. **Position stability.** Changing a task's status MUST NOT reorder or reposition any card in the current grid/list.
6. **Color contract.** TODO = border-only, In Progress = yellow, Completed = neon green, Overdue = orange border. No other colour semantics without spec approval.

## III. Navigation & State

7. **Back-stack sanity.** Every screen reachable via `NavGraph` must have a deterministic back-press destination. No trapping screens.
8. **No data loss on config change.** All user-visible state survives rotation / dark-mode toggle via `ViewModel` + `StateFlow`.

## IV. Testing

9. **Green gate.** No feature branch merges if any pre-existing test turns red. New code must include at least one unit test per public repository/ViewModel method.
10. **No `Thread.sleep` in tests.** Use `advanceUntilIdle()` / `turbine` / Compose test clock.

## V. Performance

11. **Main-thread safety.** All Room queries run on `Dispatchers.IO`. No `runBlocking` on Main.
12. **No N+1 queries.** If a screen shows a list, it must be fetched in a single DAO query (or a `@Transaction`).

## VI. Process

13. **Spec-first.** No code lands without a matching spec user story + acceptance scenarios.
14. **Additive-only changes.** Existing public API signatures (DAO, Repository, ViewModel) may be extended but never removed or renamed without a migration note in `decisions.md`.
15. **Script hygiene.** Internal one-time ad-hoc scripts (`.sh`, `.ps1`) MUST NOT be created at the repo root; they belong in `.specify/scripts/backup/`.
