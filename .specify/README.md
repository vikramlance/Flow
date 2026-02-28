# .specify — AI Review & Memory System

> Drift reduction engine for the Flow Android app.
> Two AI roles, one codebase, zero ambiguity.

---

## Roles

| Role | Purpose | May write code? | May approve? |
|------|---------|-----------------|-------------|
| **Implementation AI** | Writes specs, plans, tasks, code, tests | YES | NO |
| **Staff Engineer Reviewer AI** | Reviews specs, plans, tasks; detects drift, ambiguity, missing edges | NO | YES |

**Rule: The Implementation AI never self-reviews. All artefacts pass through the Reviewer.**

---

## Directory Structure

```
.specify/
├── README.md                  ← You are here
├── memory/
│   ├── architecture.md        ← Living system architecture doc
│   ├── invariants.md          ← Non-negotiable rules (never overridden by a spec)
│   ├── decisions.md           ← Append-only decision log with rationale
│   └── failure-log.md         ← Every bug/rejection with root cause + prevention rule
├── eval/
│   ├── contracts.md           ← Machine-checkable assertions per artefact type
│   ├── invariants.md          ← Structural/behavioural/data invariants to validate
│   └── scenarios.md           ← Edge-case scenarios for stress-testing artefacts
└── review/
    ├── spec-review.md         ← Reviewer prompt: how to review a spec
    ├── plan-review.md         ← Reviewer prompt: how to review a plan
    └── task-review.md         ← Reviewer prompt: how to review a task list
```

---

## Workflow

```
User Request
    │
    ▼
┌─────────────┐     ┌──────────────────┐
│ Impl AI      │────▶│ Spec (specs/NNN/) │
│ writes spec  │     └────────┬─────────┘
└─────────────┘              │
                             ▼
                  ┌─────────────────────┐
                  │ Reviewer AI          │  ◄── reads memory/ + eval/
                  │ runs spec-review.md  │
                  │ VERDICT: pass/fail   │
                  └────────┬────────────┘
                           │ if APPROVED
                           ▼
                  ┌─────────────────────┐
                  │ Impl AI writes plan  │
                  └────────┬────────────┘
                           ▼
                  ┌─────────────────────┐
                  │ Reviewer AI          │
                  │ runs plan-review.md  │
                  └────────┬────────────┘
                           │ if APPROVED
                           ▼
                  ┌─────────────────────┐
                  │ Impl AI writes tasks │
                  └────────┬────────────┘
                           ▼
                  ┌─────────────────────┐
                  │ Reviewer AI          │
                  │ runs task-review.md  │
                  └────────┬────────────┘
                           │ if APPROVED
                           ▼
                  ┌─────────────────────┐
                  │ Impl AI executes     │
                  │ tasks one by one     │
                  └────────┬────────────┘
                           │
                           ▼
                  ┌─────────────────────┐
                  │ On failure:          │
                  │ → failure-log.md     │
                  │ → update invariants  │
                  │ → re-review          │
                  └─────────────────────┘
```

---

## How to Invoke the Reviewer

When you want the Reviewer AI to review an artefact, use this prompt pattern:

```
You are the Staff Engineer Reviewer.
Read .specify/review/<type>-review.md and follow the protocol exactly.
Review: specs/<NNN>/<artefact>.md
Output your verdict in the format specified.
```

Replace `<type>` with `spec`, `plan`, or `task`.

---

## Learning Loop

The system self-improves through three feedback mechanisms:

1. **failure-log.md** — Every bug or rejection is recorded with root cause and a prevention rule. The Reviewer checks this log on every review pass, ensuring the same class of mistake is never approved twice.

2. **decisions.md** — Every architectural or process decision is recorded with rejected alternatives. This prevents re-litigation ("we already decided X because Y") and detects contradictions early.

3. **invariants.md + eval/invariants.md** — When a new failure class is identified, a corresponding invariant is added. This expands the Reviewer's checklist automatically over time.

**Net effect**: Each failure makes the system stricter. Drift is caught earlier with each iteration.
