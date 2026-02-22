# Contracts: Repository & DAO Additions

**Phase**: 1  Design
**Date**: 2026-02-21

---

## TaskDao additions (`data/local/TaskDao.kt`)

### `getHomeScreenTasks(todayStart, tomorrowStart)`

```kotlin
@Query("""
    SELECT * FROM tasks
    WHERE
        isRecurring = 1
        OR (dueDate IS NOT NULL AND dueDate >= :todayStart AND dueDate < :tomorrowStart)
        OR (dueDate IS NOT NULL AND dueDate < :todayStart AND status != 'COMPLETED')
        OR (dueDate IS NULL AND isRecurring = 0
            AND (status != 'COMPLETED' OR (completionTimestamp IS NOT NULL AND completionTimestamp >= :todayStart)))
    ORDER BY createdAt ASC
""")
fun getHomeScreenTasks(todayStart: Long, tomorrowStart: Long): Flow<List<TaskEntity>>
```

### `getCompletedNonRecurringTasks()`

```kotlin
@Query("""
    SELECT * FROM tasks
    WHERE completionTimestamp IS NOT NULL AND isRecurring = 0
    ORDER BY completionTimestamp DESC
""")
fun getCompletedNonRecurringTasks(): Flow<List<TaskEntity>>
```

### `getAllTasks()`  sort order fix

**Before**: `ORDER BY status ASC, createdAt DESC`
**After**: `ORDER BY createdAt ASC`

No callers depend on the sort order (refresh, populate, streak, and progress ratio all work regardless of order).

---

## TaskCompletionLogDao additions (`data/local/TaskCompletionLogDao.kt`)

### `getAllCompletedLogs()`

```kotlin
@Query("SELECT * FROM task_logs WHERE isCompleted = 1 ORDER BY date DESC, timestamp DESC")
fun getAllCompletedLogs(): Flow<List<TaskCompletionLog>>
```

---

## TaskRepository interface additions (`data/repository/TaskRepository.kt`)

```kotlin
/** Home-screen-specific task query. Shows recurring, today, overdue, and uncompleted general tasks. */
fun getHomeScreenTasks(): Flow<List<TaskEntity>>

/** Source A for global history: all recurring task completions (isCompleted=true logs). */
fun getAllCompletedRecurringLogs(): Flow<List<TaskCompletionLog>>

/** Source B for global history: all non-recurring tasks with a completion timestamp. */
fun getCompletedNonRecurringTasks(): Flow<List<TaskEntity>>
```

---

## TaskRepositoryImpl additions (`data/repository/TaskRepositoryImpl.kt`)

```kotlin
override fun getHomeScreenTasks(): Flow<List<TaskEntity>> {
    val now = System.currentTimeMillis()
    val todayStart = normaliseToMidnight(now)
    val tomorrowStart = todayStart + 86_400_000L
    return taskDao.getHomeScreenTasks(todayStart, tomorrowStart)
}

override fun getAllCompletedRecurringLogs(): Flow<List<TaskCompletionLog>> =
    taskCompletionLogDao.getAllCompletedLogs()

override fun getCompletedNonRecurringTasks(): Flow<List<TaskEntity>> =
    taskDao.getCompletedNonRecurringTasks()
```

---

## HomeViewModel changes (`presentation/home/HomeViewModel.kt`)

### Replace data source

```kotlin
// Before
repository.getAllTasks()

// After (for homeTasks in HomeUiState)
repository.getHomeScreenTasks()
```

### New functions

```kotlin
fun showHelp() {
    _helpVisible.value = true
}

fun hideHelp() {
    _helpVisible.value = false
}
```

Where `_helpVisible` is a `MutableStateFlow<Boolean>` combined into `uiState`.

---

## GlobalHistoryViewModel (`presentation/history/GlobalHistoryViewModel.kt`)

```kotlin
@HiltViewModel
class GlobalHistoryViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalHistoryUiState())
    val uiState: StateFlow<GlobalHistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAllCompletedRecurringLogs(),
                repository.getCompletedNonRecurringTasks(),
                repository.getAllTasks()
            ) { logs, nonRecurring, allTasks ->
                buildHistoryItems(logs, nonRecurring, allTasks)
            }.collect { items ->
                _uiState.update { state ->
                    state.copy(
                        allItems = items,
                        datesWithData = items.map { it.completedDayMidnight }.toSet(),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectDate(dateMs: Long?) = _uiState.update { it.copy(selectedDateMidnight = dateMs) }
    fun setViewMode(mode: HistoryViewMode) = _uiState.update { it.copy(viewMode = mode) }
    fun setFilterMode(mode: HistoryFilterMode) = _uiState.update { it.copy(filterMode = mode) }

    // Derived display items (called from Screen composable)
    fun filteredItems(state: GlobalHistoryUiState): List<HistoryItem> {
        val groupKey: (HistoryItem) -> Long? = when (state.filterMode) {
            HistoryFilterMode.COMPLETED_ON -> { item -> item.completedDayMidnight }
            HistoryFilterMode.TARGET_DATE  -> { item -> item.targetDate?.let { normaliseToMidnight(it) } }
        }
        return if (state.selectedDateMidnight == null) {
            state.allItems
        } else {
            state.allItems.filter { groupKey(it) == state.selectedDateMidnight }
        }
    }
}
```

---

## OnboardingFlow contract change (`presentation/onboarding/OnboardingFlow.kt`)

```kotlin
// Before
@Composable
fun OnboardingFlow(onComplete: () -> Unit)
// Uses: Dialog(onDismissRequest = {})    blocks outside-tap

// After
@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,   // called by "Let''s Go!" button
    onDismiss: () -> Unit = onComplete   // called by outside-tap; defaults to same as onComplete
)
// Uses: Dialog(onDismissRequest = onDismiss)
```

---

## Navigation additions

### `Routes.kt`  no new routes needed

`Routes.HISTORY = "history"` already exists, currently wired to `AnalyticsScreen` as placeholder.

### `AppNavGraph.kt` changes

```kotlin
// Before (placeholder):
composable(Routes.HISTORY) {
    AnalyticsScreen(onBack = { navController.popBackStack() })
}

// After:
composable(Routes.HISTORY) {
    GlobalHistoryScreen(onBack = { navController.popBackStack() })
}

// HomeScreen call updated:
composable(Routes.HOME) {
    HomeScreen(
        onNavigateToAnalytics = { navController.navigate(Routes.ANALYTICS) },
        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
        onNavigateToTaskHistory = { taskId -> navController.navigate(Routes.taskStreak(taskId)) },
        onNavigateToHistory = { navController.navigate(Routes.HISTORY) }   // NEW
    )
}
```
