# Quickstart — 003 Fix Tasks & Analytics

> Developer onboarding for the `003-fix-tasks-analytics` branch.
> Read this before touching any source file.

---

## Branch

```powershell
git checkout 003-fix-tasks-analytics
```

---

## Build & Run

```powershell
# From repo root (D:\Android\Flow)
.\gradlew :app:assembleDebug

# Install on connected device (SM-S936U, serial R5CY305LTGB)
.\gradlew :app:installDebug
```

---

## Run Tests

```powershell
# Unit tests only (fast)
.\gradlew :app:test

# Instrumented tests (requires device connected)
.\gradlew :app:connectedDebugAndroidTest
```

Expected pre-fix baseline: **63 unit / 45 instrumented** all green.

---

## What You're Fixing (5 items)

| ID | Symptom | Root Cause | File(s) |
|----|---------|-----------|---------|
| FR-001 | Progress bar always shows 0% | `dueDate` not normalised to midnight on write | `TaskRepositoryImpl.kt` |
| FR-002 | Task created one day early (UTC-N zones) | `DatePickerState.selectedDateMillis` is UTC midnight | `HomeScreen.kt` × 4, new `DateUtils.kt` |
| FR-003 | Future tasks missing from home screen | DAO query missing `dueDate >= tomorrowStart` clause | `TaskDao.kt`, `HomeUiState.kt`, `HomeViewModel.kt` |
| FR-005/006 | History long-press does nothing (non-recurring); only log-date edit (recurring) | `logId == null` gate blocks all editing | `GlobalHistoryScreen.kt`, `GlobalHistoryViewModel.kt`, `GlobalHistoryUiState.kt` |
| FR-007/008/009 | Analytics is one long scroll with coloured boxes | No pager; no tree icons | `AnalyticsScreen.kt` |

---

## Implementation Order

Work in this order to avoid cascading breaks:

```
1. DateUtils.kt (new)          — no deps, safe to add first
2. TaskRepositoryImpl.kt       — FR-001: normalise dueDate on write
3. TaskDao.kt                  — FR-003: add future-task clause to SQL
4. HomeUiState.kt              — FR-003: split todayTasks / upcomingTasks
5. HomeViewModel.kt            — FR-003: populate new upcomingTasks list
6. HomeScreen.kt               — FR-002: wrap 4 pickers with utcDateToLocalMidnight()
                               — FR-003: add Upcoming section below today's list
7. GlobalHistoryUiState.kt     — FR-005: add editingTask, showActionSheet, actionSheetTarget
8. GlobalHistoryViewModel.kt   — FR-005: openEditTask(), dismissEditTask(), saveEditTask(),
                                          openActionSheet(), dismissActionSheet()
9. GlobalHistoryScreen.kt      — FR-005: two-branch long-press, TaskEditSheet, action sheet
10. AnalyticsScreen.kt         — FR-007/008/009: HorizontalPager + pill tabs + tree icons
11. Tests                      — Unit + instrumented per TR-001/002/003
```

---

## Key Files Map

```
app/src/main/java/com/flow/
├── util/
│   └── DateUtils.kt                          ← NEW
├── data/
│   ├── local/
│   │   └── TaskDao.kt                        ← SQL fix (FR-003)
│   └── repository/
│       └── TaskRepositoryImpl.kt             ← dueDate normalise (FR-001)
└── presentation/
    ├── home/
    │   ├── HomeScreen.kt                     ← 4 date picker fixes (FR-002)
    │   │                                        Upcoming section (FR-003)
    │   ├── HomeViewModel.kt                  ← split today/upcoming lists (FR-003)
    │   └── HomeUiState.kt                    ← new upcomingTasks field (FR-003)
    ├── history/
    │   ├── GlobalHistoryScreen.kt            ← two-branch long-press, TaskEditSheet (FR-005)
    │   ├── GlobalHistoryViewModel.kt         ← openEditTask etc. (FR-005)
    │   └── GlobalHistoryUiState.kt           ← new editing fields (FR-005)
    └── analytics/
        └── AnalyticsScreen.kt               ← tab/pager + tree icons (FR-007/008/009)

app/src/test/java/com/flow/
├── util/
│   └── DateUtilsTest.kt                     ← NEW unit tests (TR-001)
├── data/local/
│   └── TodayProgressTest.kt                 ← extend with write+read round-trip
└── presentation/history/
    └── GlobalHistoryViewModelTest.kt        ← extend with openEditTask scenarios

app/src/androidTest/java/com/flow/
└── data/local/
    └── TaskDaoTest.kt                       ← extend with future-task query (TR-002)
```

---

## Architecture Notes

- All date writes go through **repository layer**; `normaliseToMidnight()` already lives in
  `TaskRepositoryImpl`. Add `utcDateToLocalMidnight()` to `util/DateUtils.kt` (UI-layer concern).
- `HomeViewModel` currently exposes a single `tasks` list; split it into `todayTasks` and
  `upcomingTasks` before routing to the UI. The DAO returns everything; the ViewModel filters.
- `GlobalHistoryViewModel` already has `openEditLog()` for log editing; the new
  `openEditTask()` is a separate action using `repository.getTaskById()` (already exists).
- `AnalyticsViewModel` data is already per-section; only the `AnalyticsScreen` composable
  changes layout — no ViewModel changes needed for FR-007/008/009.
- `HorizontalPager` is available from `androidx.compose.foundation` (Compose BOM 2024.09.02).
  No new dependency required.

---

## Definition of Done

- [ ] All 5 FR items verified manually on SM-S936U (Android 16)
- [ ] `.\gradlew :app:test` — 63+ unit tests green (new tests added for FR-001/002/003)
- [ ] `.\gradlew :app:connectedDebugAndroidTest` — 45+ instrumented tests green
- [ ] No `@Suppress("UNCHECKED_CAST")` or other suppression annotations added
- [ ] No new hardcoded colours — use existing `NeonGreen`, `SurfaceDark`, etc.
