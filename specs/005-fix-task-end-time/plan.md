# Implementation Plan: Fix Task End Time Bug — Iteration 2

**Branch**: `005-fix-task-end-time` | **Date**: February 28, 2026 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/005-fix-task-end-time/spec.md`
**Iteration**: 2 — Iteration 1 fixed the wrong layer (UI). Root cause is in the Repository + ViewModel save paths.

## Summary

Three changes are required:

1. **Root bug fix** — `TaskRepositoryImpl.updateTask` unconditionally calls `normaliseToMidnight()` on `dueDate` before writing to Room, stripping the time to 00:00 on every save. `GlobalHistoryViewModel.saveEditTask` applies the same call a second time. Both calls must be removed; the repository must persist the value it receives without transformation.
2. **History screen time pickers** — `TaskEditSheet` in `GlobalHistoryScreen.kt` has only date pickers (no time pickers). Users cannot set H:M from history. Start-time and target-time pickers must be added, mirroring the `EditTaskDialog` pattern already in `HomeScreen.kt`.
3. **Task card start date** — `TaskItem` on the Home screen shows only "Target: …". A "Start: …" line must be added below the title.

No schema change is needed; `TaskEntity.dueDate` and `startDate` already store epoch millis with full precision. The only issue is that the save paths strip the time component before writing.

## Technical Context

**Language/Version**: Kotlin 2.0 (JVM target 11)
**UI**: Jetpack Compose + Material 3 — `@Composable` screens (`HomeScreen.kt`, `GlobalHistoryScreen.kt`)
**Architecture**: MVVM — `HomeViewModel`, `GlobalHistoryViewModel`, `TaskRepositoryImpl`
**DI**: Hilt (constructor injection; `@HiltViewModel`)
**Storage**: Room 2.6 (`TaskDao`, `TaskEntity`) — SQLite, local-only, no network
**Async**: Kotlin Coroutines + `StateFlow` (`viewModelScope.launch`)
**Testing**: JVM unit tests (`src/test/`, `./gradlew testDebugUnitTest`); instrumented tests (`src/androidTest/`, `./gradlew connectedDebugAndroidTest`)
**Target Platform**: Android (minSdk 26, compileSdk 35)
**Performance Goals**: Not applicable — UI action, not a hot path
**Constraints**: No schema migration; no breaking changes to `AddTaskDialog`; `normaliseToMidnight` must remain in all non-save locations
**Scale/Scope**: 4 files modified (2 bug fixes, 1 UI feature, 1 UI enhancement); ~6 new test cases

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design below.*

### 1. Additive Logic (Non-Regression) ✅

| Existing flow | Risk | Verification |
|---|---|---|
| New task creation (`AddTaskDialog`) | None — `addTask()` is not touched; it defaults to 23:59 correctly | `AddTaskDefaultTimeTest.kt` must still pass |
| Home screen task edit (`EditTaskDialog`) | None — save button already passes `dueDate` state variable directly to `onSave`; repository fix makes it work correctly | `EditTaskDialogDefaultTimeTest.kt`, new end-to-end VM test |
| History screen task edit (`TaskEditSheet`) | Low additive risk — adding time pickers does not change the existing date-only path for users who don't interact with time | New time-picker test in `GlobalHistoryScreenTest.kt` |
| Task card display (`TaskItem`) | No breakage — adding a "Start:" label is purely additive | Visual regression confirmed by instrumented test |
| `normaliseToMidnight` in grouping/filtering | None — only the save paths are changed | `GlobalHistoryViewModelTest.kt` must pass unchanged |

### 2. Data Integrity ✅

- **No schema change**: `TaskEntity.dueDate: Long?` and `startDate: Long` already store epoch millis with full H:M:S:ms precision. The bug is that stored values are being truncated at write time, not a schema issue.
- **No migration**: Existing rows with midnight `dueDate` (00:00) are not retroactively changed (DI-002). They may have been intentionally set or are legacy values — both are acceptable.
- **Invariant preserved**: After fix, `dueDate` in Room == the epoch millis value sent by the UI. No intermediate layer transforms the time component.
- **Idempotent write**: `taskDao.updateTask()` with the same value twice produces the same result — no duplication risk.

### 3. Consistency ✅

- Fix strictly follows the UI → ViewModel → Repository → DAO contract.
- Repository is restored to its correct role: thin persistence layer, no business-logic transforms on stored fields.
- `normaliseToMidnight` is retained in all comparison/grouping call sites (see `normaliseToMidnight` Audit in `research.md`).
- New state variables in `TaskEditSheet` (`showStartTimePicker`, `showDueTimePicker`) follow the existing `remember { mutableStateOf(false) }` pattern already used in `EditTaskDialog`.

### 4. Security ✅

- No credentials, API keys, or PII introduced.
- No new dependencies added; no CVE review needed.
- `.gitignore` unchanged.
- No new SQL queries; Room `@Query` parameterized bindings unchanged.
- No sensitive user data logged.

### 5. Testing ✅

- **TDD order enforced**: Failing tests (T004, T005, T009) are written BEFORE the implementation code (T006, T007, T010–T014).
- **Tier 1 (unit)**: `HistoryViewModelSavesExactDueDateTimeTest.kt` — verifies full ViewModel → FakeRepository save path catches the GlobalHistoryViewModel normalisation bug.
- **Tier 2 (instrumented)**: `GlobalHistoryScreenTest.kt` updated with `taskEditSheet_timePicker_savesExactTime` — verifies date + time picker chain on device.
- **Full suite gate**: `./gradlew testDebugUnitTest` and `./gradlew connectedDebugAndroidTest` after every phase.
- **No manual testing substitution**: All scenarios have automated coverage.

*Post-design re-check*: Phase 1 design confirms no schema change, no new dependencies, no layer boundary violations. All gates pass.

## Project Structure

### Documentation (this feature)

```text
specs/005-fix-task-end-time/
├── plan.md          ← this file
├── spec.md          ← revised iteration 2 spec
├── research.md      ← root cause analysis, why iteration 1 failed
├── data-model.md    ← field-level impact analysis (Phase 1 output)
├── quickstart.md    ← build/test commands (Phase 1 output)
├── contracts/       ← save path contracts (Phase 1 output)
├── tasks.md         ← 22 implementation tasks
└── checklists/
    └── requirements.md
```

### Source Code

```text
app/src/main/java/com/flow/
├── data/
│   └── repository/
│       └── TaskRepositoryImpl.kt     ← T007: remove normaliseToMidnight from updateTask
├── presentation/
│   ├── history/
│   │   ├── GlobalHistoryViewModel.kt ← T006: remove normaliseToMidnight from saveEditTask
│   │   └── GlobalHistoryScreen.kt    ← T009–T013: add time pickers to TaskEditSheet
│   └── home/
│       └── HomeScreen.kt             ← T017: add start date/time to TaskItem card

app/src/test/java/com/flow/
├── fake/
│   └── FakeTaskRepository.kt         ← no changes needed (updateTask already records correctly)
├── presentation/
│   ├── history/
│   │   ├── GlobalHistoryViewModelTest.kt          ← existing, must pass unchanged
│   │   ├── HistoryViewModelSavesExactDueDateTimeTest.kt  ← T004: NEW failing VM test (RED)
│   │   └── TaskEditSheetEndTimeTest.kt            ← T002: DELETE (tests utility fn only)
│   └── home/
│       └── HomeScreenEndTimeTest.kt               ← T002: DELETE (tests utility fn only)

app/src/androidTest/java/com/flow/
└── presentation/history/
    └── GlobalHistoryScreenTest.kt    ← T009: NEW failing instrumented test (RED)
```

**Structure Decision**: Android single-project (Option 3 equivalent). No new files or directories at the project root. All changes are within the existing `app/src/` tree.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
