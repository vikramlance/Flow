# Internal Contracts: Progress Gamification & Analytics Enhancement

**Feature**: `002-progress-gamification`  
**Created**: 2026-02-22  

This document defines the contracts between architecture layers at every new interaction boundary introduced by this feature.

---

## Contract 1 — ViewModel → Repository: Today Progress

**Consumer**: `HomeViewModel`  
**Provider**: `TaskRepository.getTodayProgress()`  
**Direction**: `Flow` (reactive, push)  
**Type**: `TodayProgressState`

```kotlin
data class TodayProgressState(
    val totalToday: Int,        // tasks with dueDate = today (incl. recurring if scheduled)
    val completedToday: Int,    // completed subset
    val hasAnyTodayTasks: Boolean = totalToday > 0
) {
    val ratio: Float get() = if (totalToday == 0) 0f else completedToday / totalToday.toFloat()
}
```

**Invariants**:
- `completedToday <= totalToday`
- `ratio` is in `[0.0, 1.0]`
- When `hasAnyTodayTasks == false`, the UI renders the neutral empty state (FR-003), not 0%

---

## Contract 2 — ViewModel → Repository: Urgency Level

**Consumer**: `HomeViewModel` (derives per-task urgency from task fields — no repository call needed)  
**Direction**: Pure function on `TaskEntity`  
**Type**: `UrgencyLevel` (domain enum)

```kotlin
enum class UrgencyLevel { NONE, GREEN, YELLOW, ORANGE }

fun TaskEntity.urgencyLevel(today: Long = System.currentTimeMillis()): UrgencyLevel {
    if (isCompleted) return UrgencyLevel.NONE
    val start = startDate
    val end = dueDate ?: return UrgencyLevel.NONE
    if (today > end) return UrgencyLevel.NONE       // overdue — existing style
    val window = (end - start).toFloat()
    if (window <= 0f) return UrgencyLevel.ORANGE    // start == end edge case
    val elapsed = (today - start) / window
    return when {
        elapsed < 0f   -> UrgencyLevel.NONE         // today before start
        elapsed < 0.30f -> UrgencyLevel.GREEN
        elapsed < 0.70f -> UrgencyLevel.YELLOW
        else            -> UrgencyLevel.ORANGE
    }
}
```

**Invariants**:
- Completed tasks → always `NONE` (FR-006)
- No `startDate` (null) is impossible given current entity defaults — but if `dueDate` is null, result is `NONE` (FR-006)
- Overdue tasks (today > dueDate) → `NONE` (existing overdue style, FR-006)

---

## Contract 3 — ViewModel → Repository: Heatmap Data

**Consumer**: `AnalyticsViewModel`  
**Provider**: `TaskRepository.getHeatMapData(startMs: Long, endMs: Long): Flow<Map<Long, Int>>`  
**Direction**: `Flow` (reactive; re-emits when date range or completions change)  
**Key**: midnight-epoch timestamp  
**Value**: count of completions that day (all task types)

**⚠ Architecture note**: `AnalyticsPeriod` is a UI-layer sealed class and MUST NOT cross the ViewModel boundary. The ViewModel calls `period.toDateRange(earliestLogMs)` to produce `(startMs, endMs)` before invoking this method.

**Period → date range mapping** (performed in ViewModel):
| Period | Start (inclusive) | End (inclusive) |
|--------|------------------|----------------|
| `CurrentYear` | Jan 1 00:00:00 current year | Dec 31 23:59:59 current year |
| `Last12Months` | today − 364 days 00:00:00 | today 23:59:59 |
| `SpecificYear(y)` | Jan 1 00:00:00 year y | Dec 31 23:59:59 year y |
| `Lifetime` | earliest `TaskCompletionLog.date` | today 23:59:59 |

---

## Contract 4 — ViewModel → Repository: Forest Data

**Consumer**: `AnalyticsViewModel`  
**Provider**: `TaskRepository.getForestData(startMs: Long, endMs: Long): Flow<Map<Long, List<String>>>`  
**Direction**: `Flow` (reactive)

**⚠ Architecture note**: Same boundary rule as Contract 3 — ViewModel converts `AnalyticsPeriod` via `period.toDateRange(earliestLogMs)` before passing `(startMs, endMs)` to the repository.  
**Key**: midnight-epoch timestamp  
**Value**: list of distinct recurring task titles completed that day  

**Derived values** (computed in ViewModel from this data):
- `forestTreeCount: Int` = sum of all list sizes across the map
- `forestCellCounts: Map<Long, Int>` = `mapValues { it.value.size }` (for cell density shading)

---

## Contract 5 — ViewModel → Repository: Streak Data

**Consumer**: `AnalyticsViewModel`, `HomeViewModel`  
**Provider**: `TaskRepository.getStreakForTask(taskId: Long): Flow<TaskStreakEntity?>`  
**Direction**: `Flow` per task  

**Recalculation trigger** (command contract):
```kotlin
// Called after: (a) every completion mark, (b) every TaskCompletionLog edit
suspend fun TaskRepository.recalculateStreaks(taskId: Long)

// Implementation calls StreakCalculator.compute(completionDates, schedule)
// then upserts TaskStreakEntity
// then calls checkAndAwardAchievements(taskId)
```

---

## Contract 6 — ViewModel → Repository: Achievements

**Consumer**: `AnalyticsViewModel`  
**Provider**: `TaskRepository.getAchievements(): Flow<List<AchievementEntity>>`  
**Direction**: `Flow`  

**Award write contract**:
```kotlin
suspend fun TaskRepository.checkAndAwardAchievements(taskId: Long?)
```
- Must run inside a transaction.
- Must NOT create duplicate rows — guarded by the `UNIQUE INDEX idx_achievements_unique`.
- On `CONFLICT IGNORE` the insert is silently a no-op (idempotent).

---

## Contract 7 — ViewModel → Repository: History Edit

**Consumer**: `GlobalHistoryViewModel` (renamed or extended)  
**Provider**: `TaskRepository.updateLog(log: TaskCompletionLog)` (new command)  
**Post-conditions after save**:
1. `TaskCompletionLog` row updated with new dates/status.
2. If status changed to `TODO` or `IN_PROGRESS`: `TaskEntity` status updated accordingly → triggers `homeScreenTasks` Flow re-emit.
3. `recalculateStreaks(log.taskId)` is called.
4. Heatmap `Flow` for the old and new dates re-emits (Room reactive).

**Validation** (enforced in ViewModel before calling repository):
- `completionDate` must be ≤ today (FR-018)
- `completionDate` must be ≥ `task.startDate` (FR-018)
- At least one field must have changed (guard against no-op saves)

---

## Contract 8 — Domain Layer: StreakCalculator

**Layer**: `domain/streak/` — pure Kotlin, zero Android imports  
**Input**:
```kotlin
StreakCalculator.compute(
    completionDates: List<Long>,   // midnight-epoch millis; may be unsorted
    scheduleMask: Int?,            // null = DAILY; Int bitmask otherwise
    today: LocalDate = LocalDate.now()
): StreakResult
```
**Output**:
```kotlin
data class StreakResult(
    val currentStreak: Int,           // ≥ 0
    val longestStreak: Int,           // ≥ currentStreak
    val longestStreakStartDate: Long?  // midnight-epoch; null if longestStreak == 0
)
```

**Invariants**:
- `longestStreak >= currentStreak` always
- Pure function — deterministic for same inputs
- No side effects; safe to call from any coroutine

---

## Contract 9 — UI → ViewModel: Period Selector

**Consumer**: `AnalyticsScreen` composable  
**Provider**: `AnalyticsViewModel`  

```kotlin
// ViewModel exposes
val uiState: StateFlow<AnalyticsUiState>

data class AnalyticsUiState(
    val selectedPeriod: AnalyticsPeriod = AnalyticsPeriod.CurrentYear,
    val heatmapData: Map<Long, Int> = emptyMap(),
    val forestData: Map<Long, List<String>> = emptyMap(),   // for Forest section
    val forestTreeCount: Int = 0,
    val streaks: List<TaskStreakEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),
    val lifetimeStats: LifetimeStats? = null,
    val currentYearStats: CurrentYearStats? = null,
    val availableYears: List<Int> = emptyList(),            // for year picker
    val isLoading: Boolean = false
)

// ViewModel accepts
fun onPeriodSelected(period: AnalyticsPeriod)
```

**Shared state guarantee**: `heatmapData` and `forestData` always correspond to the same `selectedPeriod` (both derived from the same period in the same `combine` call — no race condition).
