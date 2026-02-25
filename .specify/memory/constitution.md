<!--
Sync Impact Report

- Version change: 0.0.0 → 1.0.0 (MAJOR: initial constitution from analysis)
- Version change: 1.0.0 → 1.1.0 (MINOR: added Rename & Deployment Protocol section)
- Version change: 1.1.0 → 1.2.0 (MINOR: added Principle VI Security & Privacy + Security gate #4)
- Version change: 1.2.0 → 1.2.1 (PATCH: added Workflow Conventions section — bulk clarification questions)
- Version change: 1.2.1 → 1.3.0 (MINOR: promoted Testing to Gate 5 (mandatory); added Test Execution
  Protocol with 4-tier pyramid, device detection/wait loop, AVD fallback, and anti-patterns;
  replaced thin bottom "## Testing" stub; updated plan-template.md and tasks-template.md)
- Version change: 1.3.1 → 1.3.2 (PATCH: added Regression Testing Protocol — red-green
  regression tests required for every bug fix; tests must target the root-cause layer)
- Principles established:
  - I. Additive Logic (Non-Regression)
  - II. Data Integrity (Single Source of Truth)
  - III. Layered Architecture Boundaries
  - IV. State-Driven UI
  - V. Explicit Dependency Boundaries
  - VI. Security & Privacy (1.2.0 — NEW)
- Sections established:
  - Core Principles
  - Platform Bindings (Current Implementation)
  - Development Workflow & Quality Gates
    - Gate 4: Security (1.2.0 — NEW)
    - Gate 5: Testing (1.3.0 — NEW, mandatory)
  - Test Execution Protocol (1.3.0 — NEW)
  - Rename & Deployment Protocol (1.1.0)
  - Governance (amendment procedure, versioning, compliance)
- Templates aligned:
  - ✅ .specify/templates/plan-template.md (Gate 5 Testing added to Constitution Check)
  - ✅ .specify/templates/spec-template.md (SE-001–SE-004 security requirements — unchanged)
  - ✅ .specify/templates/tasks-template.md (tests changed from OPTIONAL to MANDATORY)
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

### VI. Security & Privacy

The codebase MUST NOT expose personal, private, or sensitive
information, and MUST NOT introduce security vulnerabilities.

**No secrets or personal data in tracked files**

- Credentials (passwords, API keys, tokens, signing key
  passphrases) MUST NEVER appear in any source-controlled file
  — not in comments, constants, log statements, or test
  fixtures.
- Personal information (local machine paths such as
  `C:\Users\<name>`, device serials, email addresses, real
  names) MUST NOT appear in any tracked file (`.kt`, `.xml`,
  `.pro`, `.md`, `.gradle`, `.kts`, `.toml`, `.properties`).
- All sensitive file patterns MUST be present in `.gitignore`
  before the first commit. At minimum this covers:
  `local.properties`, `.idea/`, `*.jks`, `*.keystore`,
  `keystore.properties`, `google-services.json`, `.env`,
  `signing.properties`, and any `build_log*.txt` output files.
- If a secret is accidentally committed, it MUST be treated as
  compromised immediately: rotate the credential, then purge it
  from history (git-filter-repo or BFG) before pushing.

**No code-introduced vulnerabilities**

- Database queries MUST use parameterized bindings (Room
  `@Query` with named parameters). Raw string-concatenated SQL
  is prohibited.
- Network traffic MUST use HTTPS. Cleartext HTTP is prohibited
  in production builds (`android:usesCleartextTraffic="false"`
  or equivalent).
- Sensitive user data (task content, locations, health data)
  MUST NOT be written to `Logcat` at any log level in
  production (`release`) builds.
- Third-party SDK versions MUST be reviewed for known CVEs
  before adoption. Any dependency with a critical or high CVE
  MUST be updated or replaced before shipping.
- Exported Android components (`Activity`, `Service`,
  `Receiver`, `Provider`) MUST declare explicit
  `android:exported` values and include permission checks where
  access should be restricted.

**Rationale**: A real security incident — leaked credentials,
exposed PII, or an exploitable vulnerability — causes
irreversible harm to users and trust. Security is not a
exclusive Phase-7 concern: guardrails MUST be applied from the
first commit. (Current Implementation)

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
4. **Security gate** — Confirm: (a) no personal information or
   credentials are introduced into tracked files; (b)
   `.gitignore` covers all new sensitive file types produced by
   the change; (c) new code uses parameterized queries, HTTPS,
   and suppresses sensitive log output in production; (d) any
   new third-party dependency has been checked for known CVEs.

5. **Testing gate** — Confirm every layer of the test pyramid is
   covered for the change:
   (a) Unit tests for every new/modified function, class, or
   ViewModel in `src/test/`;
   (b) Instrumented (integration) tests for DAO interactions,
   repository contracts, and any UI component with side effects
   in `src/androidTest/`;
   (c) End-to-end (system) tests for each critical user journey
   on a real or virtual device;
   (d) The full test suite — unit, instrumented, and on-device —
   MUST pass before a change is considered complete.
   Manual testing MUST NOT be substituted for automated tests
   at any tier. If a scenario cannot be automated today, a
   tracking task to automate it MUST be created.

If a gate cannot be satisfied, the plan MUST include a
mitigation, a rollback path, and an explicit approval note
explaining why the exception is acceptable.

## Test Execution Protocol

This section is **normative** — every feature implementation MUST
follow it before declaring the work complete.

### Tier 1 — Unit Tests

```powershell
.\gradlew testDebugUnitTest
```

- Runs on the JVM; no device required.
- MUST pass with zero failures before proceeding to Tier 2.
- Covers: domain logic, ViewModels, StreakCalculator, state
  helpers, and repository fakes.

### Tier 2 — Instrumented / Integration Tests

```powershell
.\gradlew compileDebugAndroidTestKotlin  # compile-only gate first
.\gradlew connectedDebugAndroidTest       # requires device or AVD
```

- Requires a connected Android device or running AVD.
- MUST pass with zero failures before the branch is merged.
- Covers: Room DAO tests, repository integrity, Compose UI
  component tests.

### Tier 3 — System / End-to-End Tests

```powershell
.\gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.flow.E2ETestSuite
```

- Covers: full navigation flows (home → history → analytics),
  task lifecycle (create → complete → streak update),
  DB migration smoke test on the device.
- If a dedicated E2E suite does not yet exist, Tier 2 connected
  tests serve as the system gate until the suite is created.

### Device Detection & Wait Protocol

Before running any Tier 2 or Tier 3 command, execute the
following device check. If no device is found the agent MUST
wait — never skip or substitute with manual steps.

**PowerShell detection script** (run before every
`connectedDebugAndroidTest` invocation):

```powershell
$adb = "C:\Users\vikra\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$maxWaitSeconds = 300  # 5 minutes
$pollInterval   = 10   # seconds
$elapsed        = 0

do {
    $devices = & $adb devices 2>&1 | Where-Object { $_ -match "\bdevice\b" -and $_ -notmatch "^List" }
    if ($devices) {
        Write-Host "Device found: $devices"
        break
    }
    if ($elapsed -ge $maxWaitSeconds) {
        Write-Error "No Android device detected after $maxWaitSeconds s. Connect a physical device or start an AVD and retry."
        exit 1
    }
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] No device found. Waiting $pollInterval s... (${elapsed}s elapsed)"
    Write-Host "  → Connect a physical device via USB, or start an AVD in Android Studio (Tools → Device Manager → ▶)."
    Start-Sleep -Seconds $pollInterval
    $elapsed += $pollInterval
} while ($true)
```

**If waiting is not feasible** (e.g., CI environment without a
connected device), create an Android Virtual Device (AVD) first:

```powershell
# List available system images
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager" --list | Select-String "system-images"
# Create AVD (adjust image as needed)
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager" create avd `
    --name FlowTestAVD `
    --package "system-images;android-34;google_apis;x86_64" `
    --device "pixel_6"
# Start the emulator in the background
Start-Process -NoNewWindow "$env:ANDROID_HOME\emulator\emulator" `
    -ArgumentList "-avd FlowTestAVD -no-audio -no-boot-anim -gpu swiftshader_indirect"
# Wait for boot
& $adb wait-for-device shell getprop sys.boot_completed
```

- AVD creation is a **fallback only** when no physical device
  is available. A physical device is always preferred.
- The agent MUST ask the user to connect a device before
  creating an AVD, and MUST wait up to 5 minutes.

### Anti-Patterns (Prohibited)

| Anti-pattern | Why prohibited |
|---|---|
| Skipping Tier 2/3 because "unit tests cover it" | Instrumented tests catch Room migration bugs, Compose integration regressions, and Hilt wiring errors that JVM tests cannot. |
| Adding a task "manual QA on device" as a substitute for a Tier 3 test | Manual steps are not reproducible, not tracked, and not enforceable in CI. |
| Marking a change complete before all tiers pass | Violates Gate 5 and the Additive Logic gate simultaneously. |
| Running only the changed-module tests to "save time" | All tiers MUST run on the full project; partial runs are informational only. |

## Regression Testing Protocol

Every bug fix MUST include at least one regression test that:

1. **Fails on the unfixed code** (demonstrates the bug is present) and
   **passes after the fix** (demonstrates the fix works). This is the
   red-then-green discipline.
2. **Targets the root-cause layer** — if the bug is in a DAO query, the
   test MUST assert against the DAO or repository, not just the ViewModel.
   ViewModel-level tests that pass through a fake repository do NOT
   substitute for a root-cause test.
3. **Uses a descriptive name** that references the bug. Naming convention:
   `<scenario>_<when>_<expectedBehaviour>`. Example:
   `taskWithDueDateToday_addedAt6pm_isCountedInTodayProgress`.
4. **Lives in the lowest viable test tier**:
   - Pure logic bugs → `src/test/` (JVM unit test)
   - DAO query bugs → `src/androidTest/` (instrumented DAO test)
   - UI interaction bugs → `src/androidTest/` (Compose UI test)
5. **Is added before or with the fix** — a fix without a regression test is
   **incomplete** and MUST NOT be merged.

**After any change that touches business logic**, the author MUST:
- Run Tier 1 (unit) and confirm zero failures.
- Run Tier 2 compile gate, then the full instrumented suite on device.
- If new regression tests were added, explicitly confirm that they were
  red (failing) against the pre-fix code before applying the fix.

**Why existing tests missed a bug** MUST be documented as a one-line
comment in the new regression test, e.g.:
```kotlin
// Regression: existing HomeViewModelTest used a fake repository that
// bypassed normaliseToMidnight; the bug was in the repository write path.
```

**Prohibited**:
- Closing a bug fix PR/commit without a regression test.
- Regression tests that only test the happy path at a higher layer
  (e.g., a ViewModel test that uses a pre-seeded fake).
- Retroactively adding tests to green code without first verifying they
  would have been red on the buggy code.

**Rationale**: Without targeted regression tests, the same bug class
recurs. The existing test suite missed the `dueDate` normalisation bug
because all ViewModel-level tests used a fake repository that returned
pre-computed progress values — bypassing the actual write path. Root-cause
tests at the DAO / repository layer would have caught it immediately.

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

## Repository Hygiene

### Temporary, Output, and Log Files

Build output files, test result captures, and any other
temporary or machine-generated text files MUST NOT be placed
at the repository root or anywhere else in version-controlled
directories.

- All such files MUST be written to the `logs/` directory at
  the repo root. This directory is listed in `.gitignore` and
  is NEVER committed.
- Examples of files that belong in `logs/` (never at root):
  - Build command output captures (`build_output.txt`, etc.)
  - Test result text dumps (`unit_test_out.txt`, etc.)
  - Device / instrumented test logs
  - Any ad-hoc `*.txt` scratch file produced by a shell command
- When a command's output needs to be inspected, redirect it:
  `./gradlew <task> 2>&1 | Tee-Object logs/<descriptive-name>.txt`
- After the output is no longer needed it should be deleted;
  `logs/` is a scratch area, not an archive.
- Do NOT add new `.gitignore` patterns for individual file names;
  the top-level `logs/` rule is the single point of control.

**Rationale**: Scattered output files at the repo root confuse
navigation, pollute `git status`, and add noise to every file
picker in the IDE. Keeping them in one ignored directory keeps
the working tree clean and the intent obvious.

## Workflow Conventions

### Clarification Sessions (`/speckit.clarify`)

- All clarification questions for a session MUST be presented
  **simultaneously in a single message**, not one at a time.
- Every question MUST include a labeled options table (A / B / C …)
  so the user can reply with a letter.
- A recommended option MUST be identified and stated before the
  options table with a one- or two-sentence rationale.
- The user may answer all questions in a single reply by listing
  the question header and chosen option (e.g., "Q1: A, Q2: C").
- Maximum of 5 questions per clarification session (unchanged).

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

**Version**: 1.3.2 | **Ratified**: 2026-02-20 | **Last Amended**: 2026-02-22
