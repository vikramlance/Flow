# Contracts: API Surfaces — Feature 004

**Phase**: 1 — Design  
**Date**: 2026-02-28

---

## C-001: DateUtils additions (`util/DateUtils.kt`)

### `defaultEndTime(): Long`

```kotlin
/**
 * Returns epoch millis for today at 23:59:00.000 local time.
 * Called once at dialog-open time via `remember { defaultEndTime() }`.
 */
fun defaultEndTime(): Long
```

**Contract**: Result is always `>= normaliseToMidnight(System.currentTimeMillis())` and `< normaliseToMidnight(System.currentTimeMillis()) + 86_400_000`.

### `endTimeForDate(dateMillis: Long): Long`

```kotlin
/**
 * Returns epoch millis for the same calendar day as [dateMillis] at 23:59:00.000 local time.
 * Used when the user selects a different date — time component stays at end-of-day.
 */
fun endTimeForDate(dateMillis: Long): Long
```

**Contract**: `endTimeForDate(x)` and `normaliseToMidnight(x)` always share the same calendar date.

### `mergeDateTime(datePart: Long, timePart: Long): Long`

```kotlin
/**
 * Returns epoch millis for the calendar date of [datePart] at the
 * hour/minute/second of [timePart].
 * Used in AddTaskDialog start-date picker to preserve the original dialog-open
 * time when the user selects a different date.
 */
internal fun mergeDateTime(datePart: Long, timePart: Long): Long
```

**Contract**: `mergeDateTime(d, t)` and `normaliseToMidnight(d)` share the same calendar date. The hour/minute/second of the result equals the hour/minute/second of `timePart`.

---

## C-002: `TaskRepositoryImpl.addTask()` behaviour change

**File**: `data/repository/TaskRepositoryImpl.kt`

### Before (current)

```kotlin
val resolvedStart = if (isRecurring) todayMidnight + 60_000L   // ← WRONG: overrides creation time
                    else normaliseToMidnight(startDate)
```

### After (contract)

```kotlin
// First creation always preserves the caller-supplied startDate.
// Refresh path (refreshRecurringTasks) independently sets 12:01 AM — not addTask.
val resolvedStart = normaliseToSeconds(startDate)   // timezone-safe, no forced override
val resolvedDue   = dueDate?.let { normaliseToSeconds(it) }
```

Where `normaliseToSeconds` keeps the hour/minute components intact (unlike `normaliseToMidnight` which zeros them). Since `startDate` already has the correct time from `System.currentTimeMillis()` at dialog open, only timezone normalization is needed (not midnight normalization).

**Invariant**: `addTask()` MUST NOT override `startDate` or `dueDate` to a fixed time. Time fixing is the exclusive responsibility of `refreshRecurringTasks()`.

---

## C-003: `AchievementsViewModel` (`presentation/achievements/AchievementsViewModel.kt`)

```kotlin
@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init { loadAchievements() }

    private fun loadAchievements() {
        viewModelScope.launch {
            repository.getAllAchievements().collect { earned ->
                _uiState.update { it.copy(earned = earned) }
            }
        }
    }

    fun toggleHowItWorks() {
        _uiState.update { it.copy(isHowItWorksExpanded = !it.isHowItWorksExpanded) }
    }
}
```

**Contract**: `getAllAchievements()` already exists in `TaskRepository` / `AchievementDao`. No new DAO methods needed.

---

## C-004: `AchievementsScreen` composable (`presentation/achievements/AchievementsScreen.kt`)

```kotlin
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel()
)
```

### Layout contract

```
TopAppBar
  └─ Back icon, title "Achievements"

LazyColumn
  ├─ EarnedBadgeCard (one per earned AchievementEntity, sorted by earnedAt DESC)
  │     emoji | name | description | date earned
  ├─ HiddenPlaceholderCard (one per HIDDEN type not yet earned)
  │     "???" | "Keep going!"
  ├─ VisibleUnearned placeholder rows (VISIBLE types not yet earned)
  │     greyed-out emoji | name | "Not yet earned"
  └─ HowItWorksSection (expandable)
        lists all VISIBLE types with name + criterion
        HIDDEN types are NOT listed here
```

**Invariant**: The screen MUST NOT be empty — if zero achievements are earned, unearned VISIBLE placeholders fill the list.

---

## C-005: Navigation additions

### `Routes.kt`

```kotlin
object Routes {
    const val HOME         = "home"
    const val ANALYTICS    = "analytics"
    const val HISTORY      = "history"
    const val ACHIEVEMENTS = "achievements"   // NEW
    const val SETTINGS     = "settings"
    const val TASK_STREAK  = "task_streak/{taskId}"

    fun taskStreak(taskId: Long) = "task_streak/$taskId"
}
```

### `AppNavGraph.kt` addition

```kotlin
composable(Routes.ACHIEVEMENTS) {
    AchievementsScreen(onBack = { navController.popBackStack() })
}
```

### `HomeScreen.kt` — new parameter + top bar icon

```kotlin
// New lambda parameter (6th nav destination):
onNavigateToAchievements: () -> Unit,

// Top bar — insert between History and Help:
IconButton(onClick = onNavigateToAchievements) {
    Icon(Icons.Default.EmojiEvents, contentDescription = "Achievements")
}
```

**Icon note**: `Icons.Default.EmojiEvents` is in `material-icons-extended` (already on classpath via BOM). No new dependency.

---

## C-006: `ScheduleDaySelector` composable

**File**: `presentation/home/HomeScreen.kt` (private composable) or extracted to `ui/components/ScheduleDaySelector.kt`

```kotlin
@Composable
internal fun ScheduleDaySelector(
    mask: Int,           // current bitmask 1–127; caller must default to 127
    onMaskChange: (Int) -> Unit,
    isError: Boolean     // true when mask == 0 and user attempted to save
)
```

### Layout contract

```
Row (quick-selects)
  ├─ TextButton("Every day")  → onMaskChange(127)
  ├─ TextButton("Weekdays")   → onMaskChange(0b0011111)  // Mon–Fri = bits 0–4
  └─ TextButton("Weekends")   → onMaskChange(0b1100000)  // Sat–Sun = bits 5–6

FlowRow (day chips)
  Mon | Tue | Wed | Thu | Fri | Sat | Sun
  Each: FilterChip(selected = mask has this bit, onClick = toggle bit)

ErrorText (visible only when isError)
  "Select at least one day"
```

**Bitmask convention** (from `DayMask.kt`): bit 0 = Monday, bit 6 = Sunday.

---

## C-007: `AddTaskDialog` changes (default times)

**File**: `presentation/home/HomeScreen.kt` — `AddTaskDialog` composable

```kotlin
// BEFORE
var startDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
var dueDate   by remember { mutableStateOf<Long?>(null) }

// AFTER
val dialogOpenTime = remember { System.currentTimeMillis() }
var startDate      by remember { mutableLongStateOf(dialogOpenTime) }
var dueDate        by remember { mutableLongStateOf(defaultEndTime()) }

// When TARGET date is changed via picker, preserve 23:59 end time:
dueDate = endTimeForDate(utcDateToLocalMidnight(it))

// When START date is changed via picker, preserve the original clock time:
startDate = mergeDateTime(datePart = utcDateToLocalMidnight(it), timePart = dialogOpenTime)
```

### `mergeDateTime` helper (`util/DateUtils.kt`)

```kotlin
/**
 * Returns epoch millis combining the calendar date from [datePart] with the
 * hour/minute/second components from [timePart].
 * Used when the user picks a different start date: the time-of-day captured
 * at dialog open (dialogOpenTime) is preserved, only the date portion changes.
 */
internal fun mergeDateTime(datePart: Long, timePart: Long): Long
```

**Contract**: `startDate` captures exact dialog-open time, never re-sampled. When the user picks a different start date, only the calendar date changes — the time-of-day component stays at the original capture time (spec Edge Case: "the time component MUST stay at the exact clock time captured when the dialog opened"). `dueDate` defaults to today 23:59 and tracks end-of-day for any date the user picks.

---

## C-008: AnalyticsScreen removals

**File**: `presentation/analytics/AnalyticsScreen.kt`

Remove:
- Import `com.flow.data.local.AchievementType` (if no longer used after removal)
- `if (uiState.achievements.isNotEmpty()) { item { AchievementsSection(...) } }` (line 116–117)
- `fun AchievementsSection(...)` composable (lines 288–309)
- `internal fun achievementEmoji(...)` (lines 310–318) → **move** to `AchievementMeta.kt`
- `private fun achievementName(...)` (lines 320–327) → **move** to `AchievementMeta.kt`

**Verify**: `AnalyticsUiState` may still hold `achievements: List<AchievementEntity>` for the `AnalyticsViewModel` — check if any other Analytics tab uses it. If not, remove from `AnalyticsUiState` too.
