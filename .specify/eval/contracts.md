# Evaluation Contracts

> Machine-checkable promises that every implementation must satisfy.
> The Reviewer AI evaluates each contract as PASS / FAIL / NOT-APPLICABLE before approving.

---

## How to Use

Each contract has:
- **ID**: stable reference (`CON-NNN`).
- **Scope**: which artefact type it applies to (spec, plan, task, code, test).
- **Assertion**: a boolean statement that must be true.
- **Verification method**: how to check it (grep, test run, manual inspection).

---

## Spec Contracts

| ID | Assertion | Verification |
|----|-----------|-------------|
| CON-S01 | Every user story has ≥3 acceptance scenarios in Given/When/Then format. | Grep for `**Given**` count per `### User Story`. |
| CON-S02 | Every user story has a `**Why this priority**` rationale. | Grep for `Why this priority` per story. |
| CON-S03 | Every user story has an `**Independent Test**` description. | Grep for `Independent Test` per story. |
| CON-S04 | Spec contains a `## Non-Goals` section listing ≥1 explicit exclusion. | Section heading check. |
| CON-S05 | No acceptance scenario references an implementation detail (class name, method name, file path). | Manual review — scenarios describe user-observable behaviour only. |

## Plan Contracts

| ID | Assertion | Verification |
|----|-----------|-------------|
| CON-P01 | Plan references the spec file and every FR-/US- it addresses. | Grep for spec path + FR/US IDs. |
| CON-P02 | Every code change lists the exact file path to modify. | Grep for file paths under each fix. |
| CON-P03 | Plan has a `## Constitution Check` confirming additive-only logic. | Section heading check. |
| CON-P04 | No plan step introduces a new dependency not listed in `decisions.md`. | Cross-reference plan imports vs. `decisions.md`. |
| CON-P05 | Plan includes a rollback strategy or states changes are atomic. | Grep for `rollback` or `atomic`. |

## Task Contracts

| ID | Assertion | Verification |
|----|-----------|-------------|
| CON-T01 | Every task maps to exactly one plan step. | 1:1 mapping check. |
| CON-T02 | Every task has explicit acceptance criteria (not just "implement X"). | Grep for `Acceptance` or `Verify` per task. |
| CON-T03 | No task mutates >3 source files. If it does, split it. | File-count check per task. |
| CON-T04 | Test tasks exist for every code task. | Pairing check. |

## Code Contracts

| ID | Assertion | Verification |
|----|-----------|-------------|
| CON-C01 | All date `Long` values pass through `normaliseToMidnight()` before Room insert/update. | Grep repository impls for raw `System.currentTimeMillis()` in date fields. |
| CON-C02 | No `runBlocking` on `Dispatchers.Main`. | Grep for `runBlocking`. |
| CON-C03 | Every new public method has a KDoc comment. | Grep for `fun ` without preceding `/**`. |
| CON-C04 | No emoji is stored as a surrogate-pair escape (`\\uD8xx\\uDCxx`). | Regex grep across source. |
| CON-C05 | Every DAO query used by a list screen fetches in a single query (no N+1). | Manual DAO review. |

## Test Contracts

| ID | Assertion | Verification |
|----|-----------|-------------|
| CON-X01 | Every new public ViewModel/Repository method has ≥1 unit test. | Coverage check. |
| CON-X02 | No test uses `Thread.sleep`. | Grep for `Thread.sleep`. |
| CON-X03 | Instrumented tests use `createInMemoryDatabaseBuilder`. | Grep instrumented test files. |
| CON-X04 | All pre-existing tests pass after the change. | CI green gate. |
