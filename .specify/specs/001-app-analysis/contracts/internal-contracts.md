# Internal Contracts: Flow — Productivity & Task Management App

**Branch**: `001-app-analysis` | **Phase**: 1 | **Date**: 2026-02-20

This document defines the contracts between architectural layers.
All interactions MUST go through these interfaces; no layer bypasses another.

---

## Layer Diagram

```
┌──────────────────────────────────────────────────────┐
│ Presentation Layer                                   │
│  HomeScreen, AnalyticsScreen, HistoryScreen,         │
│  SettingsScreen, TimerPanel, OnboardingFlow          │
└────────────────────┬─────────────────────────────────┘
                     │ observes StateFlow<*UiState>
                     │ calls fun / suspend fun
                     ▼
┌──────────────────────────────────────────────────────┐
│ ViewModel Layer                                      │
│  HomeViewModel, AnalyticsViewModel,                  │
│  TimerViewModel, SettingsViewModel                   │
└────────────────────┬─────────────────────────────────┘
                     │ depends on interface
                     ▼
┌──────────────────────────────────────────────────────┐
│ Repository Interface (Domain contract)               │
│  TaskRepository, SettingsRepository                  │
└────────────────────┬─────────────────────────────────┘
                     │ implemented by
                     ▼
┌──────────────────────────────────────────────────────┐
│ Data Layer                                           │
│  TaskRepositoryImpl, SettingsManager                 │
│  AppDatabase, TaskDao, DailyProgressDao,             │
│  TaskCompletionLogDao, DataStore                     │
└──────────────────────────────────────────────────────┘
```

---

## Contract 1: `TaskRepository` (existing — extend)

```kotlin
interface TaskRepository {

    // ── Queries (reactive) ──────────────────────────────────────────────────

    /** All tasks, ordered: active first, completed last. */
    fun getAllTasks(): Flow<List<TaskEntity>>

    /** History (DailyProgress rows), ordered by date DESC. */
    fun getHistory(): Flow<List<DailyProgressEntity>>

    /** Total COMPLETED tasks, all time. */
    fun getCompletedTaskCount(): Flow<Int>

    /** Streak for a specific recurring task (consecutive days completed). */
    fun getTaskStreak(taskId: Long): Flow<Int>

    /** Full TaskCompletionLog history for a specific task. */
    fun getTaskHistory(taskId: Long): Flow<List<TaskCompletionLog>>

    // ── Commands (suspend) ──────────────────────────────────────────────────

    /** Create a task. Returns the new task ID. */
    suspend fun addTask(
        title: String,
        startDate: Long = normalisedMidnight(),
        dueDate: Long? = null,
        isRecurring: Boolean = false
    ): Long

    /** Replace all mutable fields of a task. completionTimestamp rules apply. */
    suspend fun updateTask(task: TaskEntity)

    /** Transition task to newStatus; enforces FSM rules. */
    suspend fun updateTaskStatus(task: TaskEntity, newStatus: TaskStatus)

    /** Permanently delete a task and all its TaskCompletionLog rows. */
    suspend fun deleteTask(task: TaskEntity)

    // ── Derived / Aggregates ────────────────────────────────────────────────

    /** calculates: Completed tasks for today / total tasks active today. */
    fun getTodayProgressRatio(): Flow<Float>

    /**
     * For recurring tasks: ensure today's row exists with isCompleted=false
     * if not yet actioned today. Called on app open.
     */
    suspend fun refreshRecurringTasks()

    // ── Analytics ───────────────────────────────────────────────────────────

    /** Count of tasks completed on or before their dueDate. */
    suspend fun getCompletedOnTimeCount(): Int

    /** Count of tasks where dueDate passed without COMPLETED status. */
    suspend fun getMissedDeadlineCount(): Int

    /** Best-ever consecutive-day streak across ALL recurring tasks. */
    suspend fun getBestStreak(): Int

    /** Map of date → completion count for heat map rendering. */
    fun getHeatMapData(): Flow<Map<Long, Int>>
}
```

---

## Contract 2: `SettingsRepository` (new — replaces direct `SettingsManager` injection)

```kotlin
interface SettingsRepository {

    /** Emits true until the user has completed onboarding. */
    val isFirstLaunch: Flow<Boolean>

    /** Emits true if the user has NOT yet seen/completed the tutorial. */
    val hasSeenTutorial: Flow<Boolean>

    /** Last-used timer duration in minutes. */
    val defaultTimerMinutes: Flow<Int>

    suspend fun setFirstLaunchCompleted()
    suspend fun setTutorialSeen()
    suspend fun saveDefaultTimerMinutes(minutes: Int)
}
```

---

## Contract 3: `HomeViewModel` → `HomeScreen`

**Exposed state**: `StateFlow<HomeUiState>`
```kotlin
data class HomeUiState(
    val tasks: List<TaskEntity>  = emptyList(),
    val todayProgress: Float     = 0f,       // 0.0 – 1.0
    val isFirstLaunch: Boolean   = false,
    val isLoading: Boolean       = true,
    val error: String?           = null      // non-null triggers error banner
)
```

**Actions (functions called by UI)**:
```
fun completeOnboarding()
fun addTask(title, startDate, dueDate, isRecurring)
fun updateTask(task)
fun updateTaskStatus(task, newStatus)
fun deleteTask(task)
fun getRawTaskStreak(taskId): Flow<Int>
fun getTaskHistory(taskId): Flow<List<TaskCompletionLog>>
```

---

## Contract 4: `AnalyticsViewModel` → `AnalyticsScreen`

**Exposed state**: `StateFlow<AnalyticsUiState>`
```kotlin
data class AnalyticsUiState(
    val heatMapData: Map<Long, Int>  = emptyMap(),  // midnight-epoch → count
    val totalCompleted: Int          = 0,
    val completedOnTime: Int         = 0,
    val missedDeadlines: Int         = 0,
    val currentStreak: Int           = 0,
    val bestStreak: Int              = 0,
    val isLoading: Boolean           = true,
    val error: String?               = null
)
```

**Actions**:
```
(analytics is read-only; no user mutations)
```

---

## Contract 5: `TimerViewModel` → `TimerPanel`

**Exposed state**: `StateFlow<TimerUiState>`
```kotlin
data class TimerUiState(
    val durationSeconds: Int   = 1500,   // default 25 min
    val remainingSeconds: Int  = 1500,
    val isRunning: Boolean     = false,
    val isPaused: Boolean      = false,
    val isFinished: Boolean    = false
)
```

**Actions**:
```
fun setDuration(seconds: Int)
fun start()
fun pause()
fun resume()
fun reset()
fun dismiss()   // clears isFinished, returns to idle
```

---

## Contract 6: Navigation Routes

```kotlin
object Routes {
    const val HOME       = "home"
    const val ANALYTICS  = "analytics"
    const val HISTORY    = "history"
    const val SETTINGS   = "settings"
    const val TASK_STREAK = "task_streak/{taskId}"

    fun taskStreak(taskId: Long) = "task_streak/$taskId"
}
```

Bottom navigation destinations: `HOME`, `ANALYTICS`, `HISTORY`.
Settings accessible via icon in top bar.
Task streak accessed via tap on recurring task's streak indicator.

---

## Contract 7: Timer Alert Side-Effect

When `TimerUiState.isFinished` transitions to `true`:

1. `TimerViewModel` emits a `SideEffect.PlayAlert` (SharedFlow, not StateFlow).
2. `TimerPanel` collects the side-effect and calls `MediaPlayer.start()` or `RingtoneManager.getRingtone().play()`.
3. Platform: if app is backgrounded, `TimerForegroundService` handles the alert via its own `MediaPlayer` instance.
4. The `TimerUiState` is NOT reset automatically — user must tap Dismiss or Reset.

---

## Contract 8: `SettingsViewModel` → `SettingsScreen`

**Exposed state**: `StateFlow<SettingsUiState>`
```kotlin
data class SettingsUiState(
    val defaultTimerMinutes: Int    = 25,
    val isLoading: Boolean          = false,
    val error: String?              = null
)
```

**Actions**:
```
fun saveDefaultTimerMinutes(minutes: Int)
fun replayTutorial()    // writes hasSeenTutorial = false; triggers OnboardingFlow re-launch
```

---

## Invariants Enforced by Contracts

| Invariant | Enforced at |
|---|---|
| `completionTimestamp` set only on `→ COMPLETED` | `TaskRepository.updateTaskStatus` |
| Status FSM (only valid transitions) | `TaskRepository.updateTaskStatus` |
| `date` fields always midnight epoch | `TaskRepositoryImpl` (normalise before write) |
| `DailyProgress` upserted on every completion change | `TaskRepositoryImpl.updateTaskStatus` |
| `TaskCompletionLog` cascade delete | `TaskRepository.deleteTask` |
| Single `TaskCompletionLog` per `(taskId, date)` | DB unique index + `OnConflictStrategy.REPLACE` |
