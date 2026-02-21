# Implementation Plan: Flow — Productivity & Task Management App

**Branch**: `001-app-analysis` | **Date**: 2026-02-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/001-app-analysis/spec.md`

## Summary

Flow is a single-user, local-first Android productivity app (package
`com.flow`) that motivates task completion through colour-coded
task cards (green/yellow/orange), a GitHub-style contribution heat map,
per-task recurring streaks, a focus timer, daily progress tracking, and an
analytics dashboard. The existing codebase (Kotlin 2.0.20, Jetpack Compose,
MVVM + StateFlow, Hilt DI, Room 2.6, DataStore) is the target platform.

This plan formalises the existing architecture, resolves known bugs from
roadmap prompts 1–8, introduces explicit `UiState` objects, fixes the Room
`fallbackToDestructiveMigration` for production readiness, and adds missing
screens (Settings, onboarding replay, per-task streak detail, timer
foreground service).

## Technical Context

**Language/Version**: Kotlin 2.0.20 (K2 compiler); JVM target Java 8
**Primary Dependencies**: Jetpack Compose BOM 2024.09.02, Material 3, Hilt 2.52, Room 2.6.1, DataStore 1.1.1, Lifecycle 2.8.6, Navigation Compose 2.8.x, Hilt Navigation Compose 1.2.0
**Storage**: Room 2.6.1 (SQLite, `flow_db`, schema v4→5) + DataStore Preferences
**Testing**: JUnit 4.13.2 (unit), Espresso 3.6.1 + Compose UI Test (instrumented)
**Target Platform**: Android, minSdk 24 (Android 7.0), compileSdk/targetSdk 34
**Project Type**: Mobile (single Android application module)
**Performance Goals**: 60 fps on mid-range 2020–2021 devices; home screen renders within 1 second of launch; task status update reflected within 0.5 seconds
**Constraints**: Offline-only (no network calls); <100 MB APK; backward compatible to API 24; no breaking schema changes without explicit migration
**Scale/Scope**: Single user; up to ~1 000 tasks; heat map spanning multiple years; 4 primary screens + timer panel

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Additive Logic (Non-Regression)** ✅ PASS
  - All 7 user stories extend existing functionality; no existing screen is
    removed or renamed.
  - Existing flows affected: task CRUD, streak recording, heat-map rendering,
    timer, onboarding flag.
  - Verification plan: manual walkthrough defined in `quickstart.md §4`;
    every PR must re-run the checklist before merge.

- **Data Integrity** ✅ PASS
  - Schema change: v4 → v5. Change is additive (unique index on `task_logs`);
    no data is deleted. `Migration(4,5)` defined in `data-model.md`.
  - `fallbackToDestructiveMigration()` will be removed before production build.
  - New DataStore keys (`has_seen_tutorial`, `default_timer_minutes`) are
    additive; missing keys fall back to defaults, no crash risk.
  - `completionTimestamp` immutability enforced at repository layer (contract
    defined in `contracts/internal-contracts.md`).

- **Consistency** ✅ PASS
  - All screens follow UI → ViewModel → Repository → Storage boundary.
  - Typed `UiState` objects introduced per screen; replaces scattered
    `StateFlow` fields (fixes Constitution Principle IV compliance).
  - `SettingsRepository` interface introduced; `SettingsManager` becomes
    implementation detail of the data layer (fixes Principle V compliance
    — previously `SettingsManager` was injected directly into the ViewModel).

*Post-design re-evaluation*: No violations found after Phase 1 design.
All contracts in `contracts/internal-contracts.md` respect layer boundaries.

## Project Structure

### Documentation (this feature)

```text
specs/001-app-analysis/
├── plan.md                       # This file
├── spec.md                       # Feature specification
├── research.md                   # Phase 0 — 10 research decisions
├── data-model.md                 # Phase 1 — entities, state models, migration
├── quickstart.md                 # Phase 1 — build, install, manual verification
├── contracts/
│   └── internal-contracts.md    # Phase 1 — layer contracts, UiState, routes
└── tasks.md                      # Phase 2 — created by /speckit.tasks
```

### Source Code (Android application)

```text
app/src/main/java/com/flow/
│
├── FlowApp.kt                         # @HiltAndroidApp entry point
├── MainActivity.kt                       # Single activity, NavHost host
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt                # RoomDatabase, version 4→5
│   │   ├── TaskEntity.kt                 # tasks table
│   │   ├── TaskStatus.kt                 # Enum: TODO/IN_PROGRESS/COMPLETED
│   │   ├── TaskDao.kt                    # CRUD + reactive queries
│   │   ├── DailyProgressEntity.kt        # daily_progress table
│   │   ├── DailyProgressDao.kt           # upsert + heat-map queries
│   │   ├── TaskCompletionLog.kt          # task_logs table
│   │   ├── TaskCompletionLogDao.kt       # streak + history queries
│   │   └── SettingsManager.kt            # DataStore wrapper (inject via interface)
│   └── repository/
│       ├── TaskRepository.kt             # Interface (domain contract)
│       ├── TaskRepositoryImpl.kt         # Implementation
│       ├── SettingsRepository.kt         # NEW interface
│       └── SettingsRepositoryImpl.kt     # NEW implementation wrapping SettingsManager
│
├── di/
│   ├── AppModule.kt                      # Room, DAOs, DataStore providers
│   └── RepositoryModule.kt               # Binds interfaces → impls (add SettingsRepository)
│
├── presentation/
│   ├── home/
│   │   ├── HomeScreen.kt                 # Task grid, daily progress bar
│   │   ├── HomeViewModel.kt              # Emits HomeUiState
│   │   └── HomeUiState.kt                # NEW typed state object
│   ├── analytics/
│   │   ├── AnalyticsScreen.kt            # Heat map + stats
│   │   ├── AnalyticsViewModel.kt         # Emits AnalyticsUiState
│   │   └── AnalyticsUiState.kt           # NEW typed state object
│   ├── history/
│   │   └── TaskHistoryScreen.kt          # Per-task completion log view
│   ├── timer/
│   │   ├── TimerPanel.kt                 # NEW composable (bottom sheet / dialog)
│   │   ├── TimerViewModel.kt             # NEW emits TimerUiState
│   │   ├── TimerUiState.kt               # NEW typed state object
│   │   └── TimerForegroundService.kt     # NEW Android Service for bg execution
│   ├── settings/
│   │   ├── SettingsScreen.kt             # NEW (tutorial replay, preferences)
│   │   └── SettingsViewModel.kt          # NEW
│   └── onboarding/
│       └── OnboardingFlow.kt             # NEW (multi-step tutorial composable)
│
├── navigation/
│   ├── Routes.kt                         # NEW typed route constants
│   └── AppNavGraph.kt                    # NEW NavHost wiring
│
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

**Structure Decision**: Single Android application module (Option 3 mobile pattern,
no separate API module). All source lives under `app/src/main/java/com/flow/`.
New directories: `presentation/timer/`, `presentation/settings/`,
`presentation/onboarding/`, `navigation/`.

---

## Complexity Tracking

No constitution violations requiring justification. All architectural
choices are consistent with the existing codebase and the five Core Principles.

---

## Phase Summary

| Phase | Artifacts | Status |
|---|---|---|
| 0 — Research | `research.md` (10 decisions) | ✅ Complete |
| 1 — Design | `data-model.md`, `contracts/internal-contracts.md`, `quickstart.md` | ✅ Complete |
| 2 — Tasks | `tasks.md` | ⏳ Run `/speckit.tasks` |

