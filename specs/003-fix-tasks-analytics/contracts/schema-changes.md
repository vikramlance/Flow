# Internal Contracts — 003 Fix Tasks & Analytics

> Phase 1 output for `/speckit.plan`.
> This app has no REST API. "Contracts" here are DAO query changes, ViewModel function
> signatures, and new Composable interfaces.

---

## C-001 — TaskDao: `getHomeScreenTasks` query (FR-003)

### Before (current — broken)

```sql
SELECT * FROM tasks
WHERE
    isRecurring = 1
    OR (dueDate IS NOT NULL AND dueDate >= :todayStart AND dueDate < :tomorrowStart)
    OR (dueDate IS NOT NULL AND dueDate < :todayStart AND status != 'COMPLETED')
    OR (dueDate IS NULL AND isRecurring = 0
        AND (status != 'COMPLETED'
             OR (completionTimestamp IS NOT NULL AND completionTimestamp >= :todayStart)))
ORDER BY createdAt ASC
```

### After (fixed)

```sql
SELECT * FROM tasks
WHERE
    isRecurring = 1
    OR (dueDate IS NOT NULL AND dueDate >= :todayStart   AND dueDate < :tomorrowStart)
    OR (dueDate IS NOT NULL AND dueDate >= :tomorrowStart AND status != 'COMPLETED')   -- NEW
    OR (dueDate IS NOT NULL AND dueDate < :todayStart    AND status != 'COMPLETED')
    OR (dueDate IS NULL AND isRecurring = 0
        AND (status != 'COMPLETED'
             OR (completionTimestamp IS NOT NULL AND completionTimestamp >= :todayStart)))
ORDER BY createdAt ASC
```

**Method signature**: unchanged — `getHomeScreenTasks(todayStart: Long, tomorrowStart: Long)`.

---

## C-002 — TaskRepositoryImpl: `addTask` write path (FR-001)

```kotlin
// Before
dueDate = dueDate

// After
dueDate = dueDate?.let { normaliseToMidnight(it) }
```

Also apply to `updateTask()` if it reassigns `dueDate` directly.

---

## C-003 — DateUtils.kt (FR-002)

New top-level function (see `data-model.md` for full signature):

```
utcDateToLocalMidnight(utcMidnight: Long): Long
```

**Callers** (HomeScreen.kt — 4 sites):

| Line (approx.) | Picker type | Variable | Change |
|----------------|-------------|----------|--------|
| 619 | AddTask start-date confirm | `startDate = it` | `startDate = utcDateToLocalMidnight(it)` |
| 666 | AddTask due-date confirm | `dueDate = it` | `dueDate = utcDateToLocalMidnight(it)` |
| 866 | EditTask start-date confirm | `startDate = it` | `startDate = utcDateToLocalMidnight(it)` |
| 913 | EditTask due-date confirm | `dueDate = it` | `dueDate = utcDateToLocalMidnight(it)` |

---

## C-004 — GlobalHistoryViewModel: new public functions (FR-005/FR-006)

```kotlin
/** Open the TaskEditSheet for a task entity (non-recurring or recurring "Edit task" path). */
fun openEditTask(taskId: Long)

/** Dismiss the TaskEditSheet without saving. */
fun dismissEditTask()

/** Persist title/date changes from TaskEditSheet; re-normalise dueDate. */
fun saveEditTask(updated: TaskEntity)

/** Show the recurring action sheet. */
fun openActionSheet(item: HistoryItem)

/** Dismiss the action sheet. */
fun dismissActionSheet()
```

---

## C-005 — TaskEditSheet Composable (FR-005/FR-006)

```kotlin
@Composable
fun TaskEditSheet(
    task: TaskEntity,
    onSave: (TaskEntity) -> Unit,
    onDismiss: () -> Unit
)
```

Fields exposed:
- `title` — OutlinedTextField
- `startDate` — date-only picker (utcDateToLocalMidnight applied)
- `dueDate` — date-only picker, optional (utcDateToLocalMidnight applied, nullable)
- Status toggle — `TODO` / `IN_PROGRESS` / `COMPLETED` (FilterChip row, matching `com.flow.data.local.TaskStatus` enum exactly)

No time picker; dates are day-level resolution only (log editing is day-level by design).

---

## C-006 — AnalyticsScreen: tab/pager layout (FR-007/FR-008/FR-009)

```kotlin
@Composable
fun AnalyticsScreen(viewModel: AnalyticsViewModel)
```

**Internal structure change** (no signature change):
- Replace top-level `LazyColumn` with `Column { PillTabRow(...); HorizontalPager(...) }`
- `PagerState(pageCount = 4)` managed with `rememberPagerState`
- Pages: 0 = Graph, 1 = Lifetime, 2 = This Year, 3 = Forest
- `PillTabRow` uses `FilterChip` for each tab; selected chip background = `NeonGreen`,
  text = `Color.Black`; unselected = transparent outline

**ForestHeatmap cell change (FR-008)**:

```kotlin
// Before
Box(modifier = Modifier.size(12.dp).background(color(count)))

// After
Text(
    text  = if (count == 0) "·" else "🌲".repeat(count.coerceAtMost(4)),
    style = MaterialTheme.typography.labelSmall
)
```

**FR-009** — Add above the heatmap grid inside `ForestSection`:

```kotlin
Text(
    text  = "Best streak: ${uiState.bestStreak} days",
    color = NeonGreen,
    style = MaterialTheme.typography.bodySmall
)
```
