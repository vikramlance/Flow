<!--
Sync Impact Report

- Version change: 1.0.0 → 1.1.0 (MINOR: refocus principles + expand governance gates)
- Modified principles:
	- I. MVVM Architecture → I. Additive Logic (Non-Regression)
	- II. Unidirectional Data Flow → II. Data Integrity (Single Source of Truth)
	- III. Reactive State Management → III. Consistent Architecture Boundaries
	- IV. Declarative, State-Driven UI → IV. Consistent State-Driven UI
	- V. Dependency Injection via Constructor → V. Consistent Dependency Boundaries
	- VI. Interface-Driven Repository Pattern → folded into III/V
	- VII. Local-First Persistence → folded into II
- Added sections:
	- Platform Bindings (Current Implementation)
	- Development Workflow & Quality Gates
	- Amendment + versioning procedure in Governance
- Templates requiring updates:
	- ✅ .specify/templates/plan-template.md
	- ✅ .specify/templates/spec-template.md
	- ✅ .specify/templates/tasks-template.md
	- ⚠ N/A .specify/templates/commands/*.md (directory not present)
- Deferred TODOs: None
-->

# Flow Constitution

## Core Principles

### I. Additive Logic (Non-Regression)
New features MUST be additive: they must not break existing features, user flows, or stored data.

Non-negotiables:
- Any change that could alter behavior MUST include an explicit non-regression verification plan
	(automated tests when present; otherwise documented manual verification steps).
- Behavior changes MUST be intentional, documented, and scoped; avoid "silent" semantics changes.
- Backward compatibility is the default; breaking changes require migration steps and a versioned rationale.

### II. Data Integrity (Single Source of Truth)
Data correctness is more important than new functionality.

Non-negotiables:
- There MUST be a single source of truth for persisted domain data (local-first by default).
- Writes MUST preserve invariants (e.g., valid status transitions, non-negative counters, consistent timestamps).
- Schema changes MUST be accompanied by safe migrations and validation steps; no silent data loss.
- Operations that may be retried MUST be idempotent (or explicitly guarded) to prevent duplication.

### III. Consistent Architecture Boundaries
The app MUST preserve consistent boundaries so features remain understandable and testable.

Non-negotiables:
- Presentation never reaches into storage/network directly; it talks to a ViewModel.
- ViewModels depend on abstractions (repositories/use-cases) and expose state as immutable streams.
- The data layer owns persistence details (DAOs, entities, storage adapters) and is not leaked upward.
- Data flows down (state), events flow up (user actions). No two-way binding.

### IV. Consistent State-Driven UI
UI is a function of state, not a sequence of imperative commands.

Non-negotiables:
- UI rendering MUST be deterministic given the same input state.
- UI state models MUST be explicit (avoid scattered local mutable state for core app behavior).
- Loading/empty/error states MUST be represented in state and handled consistently.

### V. Consistent Dependency Boundaries (DI + Abstractions)
Dependencies MUST be explicit and replaceable.

Non-negotiables:
- Dependencies are injected (constructor injection preferred); classes do not "reach out" to locate them.
- Use interfaces at layer boundaries (e.g., repository interfaces) so implementations can change without
	rewriting callers.
- Composition happens in one place (the app's composition root / DI modules), not scattered across features.

## Platform Bindings (Current Implementation)

the Core Principles remain valid even if the app is reimplemented on iOS.


## Development Workflow & Quality Gates

Every feature/change MUST pass these checks during spec, plan, and review:

- **Additive Logic**: list existing flows affected; define non-regression verification (tests or manual).
- **Data Integrity**: document data model changes; define migrations; validate invariants.
- **Consistency**: confirm boundaries (UI → VM → repository → storage) and consistent state modelling.

If a gate cannot be met, the plan MUST include a mitigation, rollback path, and an explicit approval note.

## Governance

This constitution is the highest-level project rule set. If a document conflicts with it, the document is
wrong and must be updated.

Amendments:
- Amendments MUST be explicit PRs that update this file and explain motivation and impact.
- Each amendment MUST include any required updates to `.specify/templates/*` that reference the constitution.

Versioning policy:
- Uses SemVer: MAJOR.MINOR.PATCH.
- MAJOR: principle removals or backward-incompatible governance/process changes.
- MINOR: new principle/section added, or materially expanded guidance (new required gates).
- PATCH: clarifications and wording changes without new obligations.

Compliance:
- Plans MUST include a constitution check section.
- Reviews MUST verify Additive Logic, Data Integrity, and Consistency gates.

**Version**: 1.1.0 | **Ratified**: 2026-02-20 | **Last Amended**: 2026-02-20
