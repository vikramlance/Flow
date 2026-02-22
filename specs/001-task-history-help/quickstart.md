# Quickstart: App Icon, Task History & Help Access

**Branch**: `001-task-history-help`  
**Date**: 2026-02-21

---

## What This Feature Changes

| Area | Change |
|---|---|
| Home screen | Completed tasks stay visible (neon green); cards never reorder |
| Home screen top bar | +2 icons: History (clock) and Help (?) |
| Onboarding overlay | Tapping outside card dismisses it |
| History screen | New global screen showing all completed tasks with date strip + view mode toggle |
| App icon | Replaced broken PNG with neon green wave adaptive icon |

---

## Verifying P1 (Task Persistence) After Implementation

1. Open the app.
2. Tap a task card once  card turns **yellow** (In Progress). Card stays in same position.
3. Tap the same card again  card turns **neon green** (Completed). Card stays in same position.
4. Verify the card did **not** disappear from the grid.
5. Tap again  card returns to **TODO** (no fill, thick border). Still same position.
6. Complete all 5 tasks. All 5 should remain visible, all neon green, in original order.

---

## Verifying P2 (History Screen) After Implementation

1. Complete at least one task. Note its title and completion time.
2. Tap the **clock icon** in the top bar  opens History screen.
3. Verify the completed task appears in the list.
4. Scroll the date strip at the top. Days with history should have a **neon green dot/tint**.
5. Tap a highlighted date  list filters to that day.
6. Tap the view mode toggle  switches between date-grouped and chronological list.
7. Switch filter mode  "Target Date" groups by the task's target date instead of completion date.

---

## Verifying P3 (App Icon) After Implementation

1. Build and install the debug APK.
2. Check the app icon on the device home screen  should show neon green wave pattern on dark background.
3. If launcher supports icon shape customization, enable circle crop  wave motif should still be visible without critical clipping.

---

## Verifying P4 (Help Icon & Dismissible Onboarding) After Implementation

1. Tap the **?** icon in the top bar  4-step onboarding overlay appears from step 1.
2. While overlay is open, tap the dark backdrop **outside** the card  overlay dismisses immediately.
3. Tap **?** again  overlay re-opens from step 1.
4. This time, tap through all steps and press "Let''s Go!"  overlay closes normally.
5. Fresh install (or clear data): on first launch, the overlay appears automatically. Tap outside  it closes without forcing completion.

---

## Build & Run

```powershell
# From repo root:
.\gradlew assembleDebug
# Then install via Android Studio Run or:
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## Key Files Changed

| File | Change Type |
|---|---|
| `app/src/main/java/com/flow/data/local/TaskDao.kt` | New queries + sort fix |
| `app/src/main/java/com/flow/data/local/TaskCompletionLogDao.kt` | New `getAllCompletedLogs()` |
| `app/src/main/java/com/flow/data/repository/TaskRepository.kt` | 3 new methods |
| `app/src/main/java/com/flow/data/repository/TaskRepositoryImpl.kt` | 3 new implementations |
| `app/src/main/java/com/flow/presentation/home/HomeUiState.kt` | `homeTasks` field, `showHelp` |
| `app/src/main/java/com/flow/presentation/home/HomeViewModel.kt` | New data source, showHelp/hideHelp |
| `app/src/main/java/com/flow/presentation/home/HomeScreen.kt` | Remove filter, +2 top bar icons |
| `app/src/main/java/com/flow/presentation/onboarding/OnboardingFlow.kt` | Dismissible dialog |
| `app/src/main/java/com/flow/presentation/history/GlobalHistoryScreen.kt` | **NEW** |
| `app/src/main/java/com/flow/presentation/history/GlobalHistoryViewModel.kt` | **NEW** |
| `app/src/main/java/com/flow/presentation/history/GlobalHistoryUiState.kt` | **NEW** |
| `app/src/main/java/com/flow/presentation/history/HistoryItem.kt` | **NEW** |
| `app/src/main/java/com/flow/navigation/AppNavGraph.kt` | Wire HISTORY route |
| `app/src/main/res/drawable/ic_launcher_background.xml` | **NEW** |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | **NEW** |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | **NEW** |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | **NEW** |

**DB version**: stays at 5 (no schema changes)
