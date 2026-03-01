# Quickstart: Fix Task End Time Bug — Iteration 2

## Prerequisites

No new dependencies. Existing build environment applies. Requires Android SDK with connected device or AVD for Tier 2 tests.

## Build

```powershell
./gradlew :app:assembleDebug
```

## Tier 1 — Unit Tests (JVM, no device)

```powershell
./gradlew :app:testDebugUnitTest
```

Run after every task. Must be zero failures.

## Tier 2 — Instrumented Tests (requires device or AVD)

```powershell
# Pre-flight device check (Principle VIII)
$adb = "C:\Users\vikra\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices

# Full suite
./gradlew :app:connectedDebugAndroidTest

# History screen only
./gradlew :app:connectedDebugAndroidTest --tests "com.flow.presentation.history.GlobalHistoryScreenTest"
```

## Key Files Modified

| File | Change |
|---|---|
| `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` | T004: remove `normaliseToMidnight` from `updateTask` dueDate save |
| `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt` | T005: remove `normaliseToMidnight` from `saveEditTask` dueDate |
| `app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt` | T009–T013: add start-time and due-time pickers to `TaskEditSheet` |
| `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` | T017: add start date/time label to `TaskItem` card |

## Key Test Files

| File | Purpose |
|---|---|
| `app/src/test/…/HistoryViewModelSavesExactDueDateTimeTest.kt` | NEW — T007: end-to-end VM save path test (History) |
| `app/src/test/…/HomeViewModelSavesExactDueDateTimeTest.kt` | NEW — T006: end-to-end VM save path test (Home) |
| `app/src/androidTest/…/GlobalHistoryScreenTest.kt` | UPDATED — T015: time picker + save assertion |
| `app/src/test/…/TaskEditSheetEndTimeTest.kt` | DELETED — T002: only tested utility fn, not save path |
| `app/src/test/…/HomeScreenEndTimeTest.kt` | DELETED — T002: only tested utility fn, not save path |

## TDD Order

```
T006 (write failing test) → T004 (fix Repository) → T008 (green)
T007 (write failing test) → T005 (fix ViewModel)  → T008 (green)
T015 (write failing test) → T009–T013 (UI pickers)  → T016 (green)
```
