# Research: Flow — Productivity & Task Management App

**Branch**: `001-app-analysis` | **Phase**: 0 | **Date**: 2026-02-20

All findings below resolve unknowns from the Technical Context and inform
Phase 1 design decisions.

---

## 1. Minimum SDK & Backward Compatibility

**Decision**: `minSdk 24` (Android 7.0 Nougat) — retained from existing project.

**Rationale**:
- Android 7.0 covers ~97% of active Android devices as of 2026 (Google
  Play distribution data).
- All chosen libraries (Compose BOM 2024.09.02, Room 2.6, Hilt 2.52,
  DataStore 1.1) support `minSdk 24`.
- Compose requires `minSdk 21`; Room and Hilt have no API requirements
  above 21. No backward-compat library shims needed.
- `compileSdk`/`targetSdk` remain 34 for current Play Store requirements.

**Alternatives considered**: `minSdk 21` — rejected because gains are
marginal (<3% extra device coverage) and would complicate testing matrix.

---

## 2. UI Responsiveness Strategy

**Decision**: Jetpack Compose adaptive layouts with `WindowSizeClass` for
tablet/foldable support; no XML interop required.

**Rationale**:
- The app is single-activity; Compose NavigationSuite handles adaptive nav
  (bottom bar on phones, nav rail on tablets) with one code path.
- Material 3 `WindowSizeClass` is the recommended approach for responsive
  Compose layouts (Google I/O 2024).
- `LazyVerticalGrid` (already used in `HomeScreen.kt`) naturally re-flows
  columns based on available width.

**Alternatives considered**: XML + fragments — rejected; project is
100% Compose and introducing XML would violate Principle III (layered
boundaries) and Fragment lifecycle complexity.

---

## 3. Database Migration Strategy (replacing `fallbackToDestructiveMigration`)

**Decision**: Explicit `Migration` objects in `AppModule` for every
schema version increment, with `fallbackToDestructiveMigration` removed
before any production release.

**Rationale**:
- Current `AppDatabase` is at `version = 4` with `fallbackToDestructiveMigration()`.
  This is acceptable for dev but violates Constitution Principle II (Data
  Integrity) in production.
- Room's `Migration` API provides `ALTER TABLE`, `CREATE TABLE`, etc. with
  full SQLite compatibility back to SDK 24.
- Any schema addition (new column, new table) requires a migration + test.

**Alternatives considered**: `autoMigrations` annotation (Room 2.4+) —
evaluated but requires schema export (`exportSchema = true`) which has a
file-system side-effect; manual migrations preferred for explicit control.

---

## 4. State Modelling per Screen (UiState objects)

**Decision**: Introduce a typed `UiState` sealed class / data class per
screen, replacing multiple individual `StateFlow` properties on `HomeViewModel`.

**Rationale**:
- Current `HomeViewModel` exposes three separate `StateFlow` fields
  (`isFirstLaunch`, `tasks`, `history`); as the app grows this becomes
  difficult to atomically update.
- Constitution Principle IV requires explicit single-state-object per screen
  for core app behaviour.
- Pattern: `data class HomeUiState(val tasks, val progress, val isFirstLaunch)`
  exposed as one `StateFlow<HomeUiState>`. Compose `collectAsStateWithLifecycle`
  collects it efficiently.
- `AnalyticsViewModel` needs the same treatment.

**Alternatives considered**: Keep individual fields — rejected; violates
Principle IV and makes it impossible to model atomic state transitions
(e.g., loading + tasks updating simultaneously).

---

## 5. Timer Implementation (Background / Foreground)

**Decision**: `CountDownTimer` or `CoroutineScope` + `delay` loop tied to
a `TimerViewModel`. A `ForegroundService` with a notification is used when
the app is backgrounded, so the alert fires reliably.

**Rationale**:
- Android restricts background execution on SDK 24+; a process that is not
  in the foreground can be killed, silencing the timer alert.
- A bound `ForegroundService` keeps the process alive and shows a
  persistent notification during an active timer session — standard pattern
  for workout / meditation apps.
- The `TimerViewModel` holds timer state; the `ForegroundService` is the
  execution host when backgrounded.
- Audible alert uses `MediaPlayer` or `RingtoneManager` — both available
  SDK 24+.

**Alternatives considered**: `WorkManager` — rejected; WorkManager is for
deferrable background work, not precise countdown timers. `AlarmManager`
with exact alarm — rejected; requires `SCHEDULE_EXACT_ALARM` permission
(restricted API on SDK 31+) and would need a new manifest permission.

---

## 6. Heat Map Calendar Rendering Performance

**Decision**: `LazyHorizontalGrid` or custom `Canvas` drawing for the
contribution grid. Weekday labels in a fixed `Column` outside the lazy
container.

**Rationale**:
- The heat map can span 365+ cells per year. `LazyHorizontalGrid` only
  composes visible cells, keeping memory constant.
- Weekday labels (Mon–Sun, 7 items) are placed in a sibling `Column` with
  the same row height, outside the scroll container — this was the root
  cause of the label-scroll bug reported in roadmap prompt 8.
- Cell tap shows date tooltip (roadmap prompt 8 UX request).

**Alternatives considered**: `RecyclerView` in Compose via `AndroidView` —
rejected; unnecessary complexity when `LazyHorizontalGrid` solves the
problem natively.

---

## 7. Onboarding Storage

**Decision**: Boolean flag in DataStore Preferences (`is_first_launch`),
already implemented in `SettingsManager`. Extend with additional flags if
multi-step onboarding progress needs persistence.

**Rationale**:
- `SettingsManager.isFirstLaunch` already exists and is wired to
  `HomeViewModel.isFirstLaunch`. The infrastructure is in place.
- Add `has_seen_tutorial` key (separate from `is_first_launch`) so users
  can trigger re-play from Settings without resetting the first-launch
  dummy-data population.

**Alternatives considered**: SQLite table for onboarding state — rejected;
overkill for a boolean flag. SharedPreferences — rejected; DataStore is
the modern, coroutine-safe replacement and is already a dependency.

---

## 8. Navigation Architecture

**Decision**: Single-activity, Jetpack Compose Navigation (`NavHost`) with
`hilt-navigation-compose` for ViewModel scoping. Routes are typed constants.

**Rationale**:
- `hilt-navigation-compose 1.2.0` is already a declared dependency.
- Nav graph routes: `home`, `analytics`, `history`, `settings`.
- Per-task streak detail is a nested destination: `task_streak/{taskId}`.
- `BottomNavigation` / `NavigationBar` (Material 3) drives primary nav.

**Alternatives considered**: Fragments with Navigation Component — rejected;
project is fully Compose, fragments would violate Principle III.

---

## 9. Performance Budget

**Decision**: Target 60 fps on a Pixel 4a (2020, ~mid-range) and Samsung
Galaxy A32 (2021, budget tier). Profile with `Compose Compiler metrics +
systrace`.

**Rationale**:
- App is read-heavy (reading tasks, rendering grid). Flow emissions from
  Room DAOs are collected with `collectAsStateWithLifecycle` to avoid
  recompositions on background threads.
- Heat-map `Canvas` drawing deferred to `DrawScope` — no allocations per
  frame.
- `remember { }` guards on derived computations in composables.

---

## 10. Dependency Additions Required

| Library | Version | Purpose |
|---|---|---|
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.8.6` | `collectAsStateWithLifecycle` |
| `androidx.navigation:navigation-compose` | `2.8.x` | NavHost, typed routes |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | BOM | Adaptive nav |
| `androidx.window:window` | `1.3.x` | `WindowSizeClass` |

All existing dependencies (Kotlin 2.0.20, Compose BOM 2024.09.02, Room 2.6.1,
Hilt 2.52, DataStore 1.1.1) are retained unchanged.

No new dependencies introduce transitive version conflicts (verified via
BOM resolution: all Compose artefacts resolve to the same BOM version).
