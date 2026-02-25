# Research — 003 Fix Tasks & Analytics

> Phase 0 output for `/speckit.plan`. All NEEDS CLARIFICATION items resolved.

---

## R-001 — UTC midnight vs local midnight: DatePicker offset bug (FR-002)

**Question**: `DatePickerState.selectedDateMillis` returns UTC-epoch midnight for the
selected calendar date. When does this cause the wrong local day to be stored?

**Finding**: In UTC-N (negative offset) timezones, `2026-02-22T00:00:00Z` converts to
`2026-02-21T19:00 local` (for UTC-5). Applying `normaliseToMidnight()` to that value
yields **Feb 21 local midnight** — one day early.  
In UTC+N timezones the UTC midnight is *later* in the local clock (e.g. UTC+5:30 gets
`Feb 22, 05:30 local`) — `normaliseToMidnight()` correctly strips the 05:30 → **Feb 22**.  
So the bug manifests in **UTC-N** zones.

**Decision**: Introduce a top-level utility function `utcDateToLocalMidnight(utcMillis: Long): Long`
in a new file `app/src/main/java/com/flow/util/DateUtils.kt`.  
It extracts the (YEAR, MONTH, DAY_OF_MONTH) components from a UTC-timezone Calendar, then
constructs a **local** Calendar with those same components at 00:00:00.000.

```kotlin
// DateUtils.kt
fun utcDateToLocalMidnight(utcMidnight: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMidnight
    }
    return Calendar.getInstance().apply {   // local timezone
        set(Calendar.YEAR,         utcCal.get(Calendar.YEAR))
        set(Calendar.MONTH,        utcCal.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY,  0)
        set(Calendar.MINUTE,       0)
        set(Calendar.SECOND,       0)
        set(Calendar.MILLISECOND,  0)
    }.timeInMillis
}
```

**Call sites**: The four `datePickerState.selectedDateMillis?.let { … }` blocks in
`HomeScreen.kt` (lines 619, 666, 866, 913) must wrap `it` with `utcDateToLocalMidnight(it)`
before assigning to `startDate` / `dueDate`.

**Rationale**: UTC-day components are preserved; local clock is used for actual midnight.  
**Alternatives considered**:
- Replace `DatePicker` with a custom Compose date picker that works in local time — rejected
  (disproportionate scope; Material 3 DatePicker is the app standard).
- Use `TimeZone.getDefault().getOffset(millis)` arithmetic — rejected (doesn't handle DST correctly).

---

## R-002 — dueDate normalisation on write (FR-001)

**Question**: Why does `getTodayProgress()` return 0 even when tasks are due today?

**Finding**: `getTodayProgress()` calls `taskDao.getTasksDueOn(normaliseToMidnight(now))`
which uses `WHERE dueDate = :todayMidnight` (exact equality). However `addTask()` in
`TaskRepositoryImpl` stores `dueDate = dueDate` (the raw caller value). When the caller
passes a DatePicker UTC midnight that has been further modified by the time picker
(e.g., hour=23), the stored value is `todayMidnight + 82_800_000` — not equal to
`todayMidnight`. Zero rows match.

**Decision**: Normalise `dueDate` to local midnight in `addTask()` — mirror the existing
treatment of `startDate`:

```kotlin
dueDate = dueDate?.let { normaliseToMidnight(it) }
```

`normaliseToMidnight()` already exists at the bottom of `TaskRepositoryImpl.kt`; no new
deps needed.

**Also fix `updateTask()`**: Check if `updateTask()` also sets raw `dueDate` — yes, same
pattern. Same one-liner fix needed there.

**Rationale**: The DAO queries use exact midnight equality; normalise at the repository
boundary (not in the DAO) to keep the write contract simple.  
**Alternatives considered**:
- Change `WHERE dueDate = :todayMidnight` to a range (`>= todayStart AND < tomorrowStart`)
  — valid but deferred; query already has the range pattern elsewhere. Keeping exact equality
  is simpler while the write path is fixed.

---

## R-003 — Future tasks missing from home screen (FR-003)

**Question**: Why do tasks with `dueDate > tomorrowStart` not appear on the Home screen?

**Finding**: `TaskDao.getHomeScreenTasks(todayStart, tomorrowStart)` SQL covers four clauses:
1. `isRecurring = 1` (today's recurring)
2. Today non-recurring: `dueDate >= todayStart AND dueDate < tomorrowStart`
3. Overdue: `dueDate < todayStart AND status != 'COMPLETED'`
4. Undated: `dueDate IS NULL AND isRecurring = 0`

**Missing clause**: `dueDate >= tomorrowStart AND status != 'COMPLETED'` (future dated).

**Decision**: Add the clause. Also add `tomorrowStart` as a second named parameter
(`getHomeScreenTasks(todayStart: Long, tomorrowStart: Long)` — already present — just
extend the query).

**UI impact**: The Upcoming section (FR-003 UI) surfaces only *future* tasks; "today"
tasks remain in the existing top section. `HomeUiState.kt` needs two task lists: `todayTasks`
and `upcomingTasks`. `HomeViewModel` splits the DB result into the two buckets by comparing
`dueDate` to `tomorrowStart`.

**Rationale**: Minimal query change; split in ViewModel keeps the DAO simple.

---

## R-004 — History long-press for non-recurring tasks (FR-005/FR-006)

**Question**: What happens when the user long-presses a non-recurring task in History?

**Finding**: Current code: `item.logId?.let { viewModel.openEditLog(…) }`. Non-recurring
tasks have `logId = null` → the `let` block is never entered → nothing happens.

**Decision**: Two-branch long-press handler:
- `item.isRecurring = true` → show a bottom-sheet action picker with two options:
  - "Edit this day" → calls existing `viewModel.openEditLog(log)` (keeps `HistoryEditDialog`)
  - "Edit task" → calls new `viewModel.openEditTask(taskId)`
- `item.isRecurring = false` → directly call `viewModel.openEditTask(taskId)` (no sheet needed)

**New ViewModel additions** (`GlobalHistoryViewModel`):
```kotlin
fun openEditTask(taskId: Long)
fun dismissEditTask()
fun saveEditTask(updated: TaskEntity)
```

**New UiState fields** (`GlobalHistoryUiState`):
```kotlin
val editingTask: TaskEntity? = null        // non-null = TaskEditSheet open
val showActionSheet: Boolean = false       // recurring long-press → action picker
val actionSheetTarget: HistoryItem? = null // which item triggered the sheet
```

**New Composable**: `TaskEditSheet` — a `ModalBottomSheet` with fields for title,
startDate, dueDate (date-picker only, no time picker needed here), and status toggle.

**Rationale**: Additive — existing `HistoryEditDialog` (log-date picker) is untouched for
recurring "Edit this day" path. New sheet is purely additional.

---

## R-005 — Analytics tab/pager redesign (FR-007/FR-008/FR-009)

**Question**: What layout best delivers the tab/pager one-section-at-a-time experience?

**Finding**: Existing `AnalyticsScreen.kt` is a single `LazyColumn` with all sections
stacked. `AnalyticsViewModel` already has per-section data (`lifetimeStats`,
`currentYearStats`, `forestData`, `heatMapData`).

**Decision**:
- Use `HorizontalPager` + `TabRow` with `FilterChip`-style pill switcher (not Material `Tab`)
  to match the neon-green aesthetic (user Q5 answer: "pill FilterChip switcher, neon-green active").
- Four pages in order: **Graph** (heatmap), **Lifetime**, **This Year**, **Forest**.
- Each page is a full-screen `Column` wrapped in a `LazyColumn`; sections expand naturally.
- `HorizontalPager` to use `PagerState` from `accompanist-pager` (already used elsewhere)
  or Compose Foundation 1.4+ built-in pager — check which is in the project.

**Forest cell fix (FR-008)**: Replace `Box(modifier = Modifier.background(color))` cells
with `Text("🌲".repeat(treeCount.coerceAtMost(4)))` for 1-4 trees, where `treeCount =
forestData[dayMidnight]?.size ?: 0`. Empty cells show a faint dot or nothing.

**FR-009 — streak in Forest**: Forest section already has a `ForestSection` composable
that accesses `forestData`. Add a `Text("Longest streak: $bestStreak days")` above the
heatmap grid (data already in `AnalyticsUiState.bestStreak`).

**Pager library check**: Search `libs.versions.toml` for `pager` / `accompanist`.

**Rationale**: `HorizontalPager` matches the "tab/pager, one section at a time" requirement
and is natively supported in Compose Foundation since 1.4 (AGP 8.5 → Compose BOM 2024.x).
