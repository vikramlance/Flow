<!--
Sync Impact Report

- Version change: 0.0.0 → 1.0.0 (MAJOR: initial constitution from analysis)
- Version change: 1.0.0 → 1.1.0 (MINOR: added Rename & Deployment Protocol section)
- Principles established:
  - I. Additive Logic (Non-Regression)
  - II. Data Integrity (Single Source of Truth)
  - III. Layered Architecture Boundaries
  - IV. State-Driven UI
  - V. Explicit Dependency Boundaries
- Sections established:
  - Core Principles
  - Platform Bindings (Current Implementation)
  - Development Workflow & Quality Gates
  - Rename & Deployment Protocol (1.1.0)
  - Governance (amendment procedure, versioning, compliance)
- Templates aligned:
  - ✅ .specify/templates/plan-template.md (Constitution Check gates)
  - ✅ .specify/templates/spec-template.md (AL/DI/CO requirements)
  - ✅ .specify/templates/tasks-template.md (Constitution Gates)
- Deferred TODOs: None
-->

# Flow Constitution

## Core Principles

### I. Additive Logic (Non-Regression)

New features MUST be additive — they MUST NOT break existing
features, user flows, or stored data.

- Every change that could alter behavior MUST include a
  non-regression verification plan (automated tests when present;
  documented manual verification steps otherwise).
- Behavior changes MUST be intentional, documented, and scoped.
  Silent semantics changes are prohibited.
- Backward compatibility is the default. Breaking changes require
  a migration path and versioned rationale before merging.
- Removing or renaming a public screen, navigation route, or
  user-facing action requires deprecation notice in the prior
  release.

**Rationale**: A to-do / productivity app earns trust through
reliability. Users must never lose access to a working flow
because of an unrelated change.

### II. Data Integrity (Single Source of Truth)

Data correctness is more important than new functionality.

- There MUST be exactly one source of truth for each piece of
  persisted domain data (local-first by default).
- Writes MUST preserve invariants: valid status transitions,
  non-negative counters, consistent timestamps, referential
  integrity between entities.
- Schema or storage changes MUST be accompanied by safe,
  tested migrations. Silent data loss is a critical defect.
- Operations that may be retried (e.g., network sync, background
  workers) MUST be idempotent or explicitly guarded against
  duplication.
- Deletion MUST be intentional and confirmable; soft-delete is
  preferred where undo is expected.

**Rationale**: Tasks, streaks, and progress history are the user's
core value. Losing or corrupting that data destroys the product.

### III. Layered Architecture Boundaries

The app MUST maintain consistent layer boundaries so that each
feature remains understandable, testable, and replaceable.

- **Presentation** (screens / views) MUST NOT reach into storage
  or network directly; it communicates only with a ViewModel (or
  equivalent presenter).
- **ViewModel / Presenter** depends on abstractions (repository
  interfaces, use-cases) and exposes state as immutable,
  observable streams. It MUST NOT hold platform UI references.
- **Data layer** owns all persistence details (DAOs, entities,
  API clients, storage adapters). These details MUST NOT leak
  into upper layers.
- Data flows **down** (state); events flow **up** (user actions).
  Two-way binding is prohibited.

**Rationale**: Clean boundaries let individual layers change
(e.g., swap Room for CoreData, Compose for SwiftUI) without
cascading rewrites.

### IV. State-Driven UI

UI MUST be a pure function of state, not a sequence of
imperative commands.

- Given the same input state, a screen MUST render identically.
- Core UI state MUST be modelled explicitly in a single state
  object per screen (avoid scattered local mutable flags for
  app-critical behavior).
- Every screen MUST handle at least these states: loading,
  content, empty, and error — represented in the state model
  and rendered consistently.
- Animations and transient visual state (e.g., ripple, scroll
  position) are exempt from the single-state-object rule but
  MUST NOT drive business logic.

**Rationale**: Deterministic rendering eliminates an entire class
of "works on my device" bugs and simplifies automated UI testing.

### V. Explicit Dependency Boundaries

Dependencies MUST be declared, injected, and replaceable.

- All dependencies are provided via constructor injection (or
  the platform's idiomatic equivalent). Classes MUST NOT locate
  or create their own dependencies.
- Layer boundaries (repository, data source, manager) MUST be
  defined as interfaces/protocols. Callers depend on the
  abstraction, never the concrete implementation.
- The full dependency graph is assembled in one composition root
  (DI modules / app entry point), not scattered across feature
  code.
- Third-party libraries MUST be wrapped behind an internal
  interface when used at a layer boundary, so they can be
  replaced without touching feature code.

**Rationale**: Explicit injection makes every dependency visible,
testable with fakes/mocks, and swappable across platforms.

## Platform Bindings (Current Implementation)

The Core Principles above are **platform-agnostic**. Below are the
current Android bindings; an iOS implementation would substitute
platform equivalents while respecting the same principles.

| Concern | Android (current) | iOS (equivalent) |
|---|---|---|
| Language | Kotlin 2.0 | Swift 5.9+ |
| UI | Jetpack Compose + Material 3 | SwiftUI |
| Architecture | MVVM + StateFlow | MVVM + Combine / @Observable |
| DI | Hilt (Dagger) | Swift DI container / Swinject |
| Database | Room 2.6 (SQLite) | SwiftData / CoreData / GRDB |
| Preferences | DataStore Preferences | UserDefaults / @AppStorage |
| Async | Kotlin Coroutines + Flow | Swift Concurrency + AsyncSequence |
| Build | AGP 8.5, KSP, Version Catalog | Xcode / SPM |

## Development Workflow & Quality Gates

Every feature or change MUST pass these three gates during
specification, planning, and code review:

1. **Additive Logic gate** — List every existing user flow
   affected by the change. Define how each will be verified
   (automated test, manual walkthrough, or screenshot
   comparison).
2. **Data Integrity gate** — Document data-model changes (if
   any), define migrations, and list invariants that must hold
   before and after the change.
3. **Consistency gate** — Confirm the change respects layer
   boundaries (UI → VM → repository → storage) and uses
   consistent state modelling (single state object per screen).

If a gate cannot be satisfied, the plan MUST include a
mitigation, a rollback path, and an explicit approval note
explaining why the exception is acceptable.

### Rename & Deployment Protocol

When `applicationId`, `namespace`, or package structure changes,
the following steps are **mandatory** before attempting to run or
distribute a build — skipping any step will produce an
`Activity class does not exist` launch failure or silent data
corruption on the device.

**On Any applicationId / Package Rename**

1. Update both `namespace` and `applicationId` in
   `app/build.gradle.kts` to the new value (they MUST match).
2. Update the `package` attribute in `AndroidManifest.xml` if it
   is set explicitly (AGP derives it from `namespace` in modern
   builds — omit it entirely if not needed).
3. Rename all source directories to match the new package path
   (`com/new/pkg/...`) and update every `package` and `import`
   statement in Kotlin/Java source files.
4. **Uninstall the OLD application from every device and
   emulator** before running the new build:
   ```
   adb uninstall <old.application.id>
   ```
   The Android system identifies apps by `applicationId`;
   leaving the old APK installed causes the IDE to resolve the
   wrong package and fail to find activities from the renamed
   code.
5. In Android Studio:
   - Run **File → Sync Project with Gradle Files**.
   - If the error persists, run **File → Invalidate Caches…
     → Invalidate and Restart**.
6. Verify the run configuration (`.idea/workspace.xml` →
   `RunManager`) does not contain a hardcoded old package name.
   If it does, delete the run configuration and let Android
   Studio recreate it from the Gradle model.
7. Confirm the build succeeds and only the new package is
   installed before marking the rename complete:
   ```
   adb shell pm list packages | findstr <new.application.id>
   ```

**Root Cause for Future Reference**

The error `Activity class {old.id/new.pkg.Activity} does not
exist` always means the IDE or `adb am start` is targeting an
installation whose `applicationId` no longer matches the
activity's class package — typically caused by a stale device
installation or a cached run configuration that predates the
rename.

## Governance

This constitution is the highest-level rule set for the Flow
project. If any other document conflicts with it, the other
document MUST be corrected.

**Amendment procedure**:
- Amendments MUST be submitted as explicit changes that update
  this file and explain motivation and impact.
- Each amendment MUST include any required updates to
  `.specify/templates/*` that reference the constitution.

**Versioning policy** (SemVer):
- **MAJOR**: Principle removal or backward-incompatible
  governance change.
- **MINOR**: New principle or section added, or materially
  expanded guidance (new required gate).
- **PATCH**: Clarification, wording fix, or non-semantic
  refinement with no new obligations.

**Compliance**:
- Implementation plans MUST include a "Constitution Check"
  section addressing all three gates.
- Code reviews MUST verify Additive Logic, Data Integrity,
  and Consistency gates before approval.

## Testing
**build**: build must be successful and validated
**Unit tests**: add unit tests for maximum coverage.
**UI tests**: add tests for integration and end-to-end flows on critical paths.
Tests MUST correctness of functional requirements.

**Version**: 1.1.0 | **Ratified**: 2026-02-20 | **Last Amended**: 2026-02-20
