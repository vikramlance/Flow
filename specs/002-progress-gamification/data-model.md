# Data Model: Progress Gamification & Analytics Enhancement

**Feature**: `002-progress-gamification`  
**Created**: 2026-02-22  
**DB version**: 5 → 6  

---

## Existing Entities (modified)

### `tasks` table — `TaskEntity`

One new nullable column added. All existing rows default to `null`.

| Field | Type | Change | Notes |
|-------|------|--------|-------|
| `id` | `Long` | — | existing PK |
| `title` | `String` | — | existing |
| `description` | `String` | — | existing |
| `status` | `TaskStatus` (TEXT) | — | existing enum |
| `dueDate` | `Long?` | — | existing; recurring tasks receive `dueDate = scheduled occurrence date` on refresh |
| `startDate` | `Long` | — | existing; recurring tasks receive `startDate = scheduled occurrence date` on refresh |
| `isRecurring` | `Boolean` | — | existing |
| `createdAt` | `Long` | — | existing |
| `completionTimestamp` | `Long?` | — | existing |
| **`scheduleMask`** | **`Int?`** | **NEW** | Bitmask: bit 0=Mon … bit 6=Sun; `null` = DAILY (backward-compat default) |

**Validation rules**:
- `scheduleMask` must be `null` OR in range `1..127` (at least one bit set).
- `scheduleMask` is only meaningful when `isRecurring = true`; non-recurring tasks always have `null`.

**`refreshRecurringTasks()` extended behaviour**:
- For each recurring task: compute `todayBit = 1 shl (today.dayOfWeek.value - 1)`.
- If `scheduleMask IS NULL OR (scheduleMask AND todayBit) != 0` → reset to TODO, set `startDate = todayMidnight`, set `dueDate = todayMidnight`.
- Otherwise → skip (task not scheduled today; leave in COMPLETED from prior day or skip if already TODO).

---

### `task_completion_log` table — `TaskCompletionLog`

No schema changes. Two fields must become mutable to support US6 history editing:

| Field | Type | Change | Notes |
|-------|------|--------|-------|
| `id` | `Long` | — | existing PK |
| `taskId` | `Long` | — | existing FK |
| `date` | `Long` | **mutable** | midnight-epoch; must be updatable via `updateLog` DAO method |
| `timestamp` | `Long` | **mutable** | accurate timestamp; must be updatable |
| `isCompleted` | `Boolean` | — | existing |

New DAO method required: `suspend fun updateLog(log: TaskCompletionLog)`.

---

## New Entities

### `task_streaks` table — `TaskStreakEntity`

Stores the computed streak per recurring task. Updated after every completion event or log edit (FR-010, DI-001).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `taskId` | `Long` | PK, FK → tasks.id | one row per recurring task |
| `currentStreak` | `Int` | ≥ 0 | consecutive scheduled-day completions up to today |
| `longestStreak` | `Int` | ≥ currentStreak | all-time best run |
| `longestStreakStartDate` | `Long?` | nullable | midnight-epoch of first day of longest run |
| `lastUpdated` | `Long` | non-null | timestamp of last recalculation |

**State transitions**:
- Created on first completion of a recurring task.
- Recalculated from full log on every: (a) completion event, (b) TaskCompletionLog edit, (c) app launch refresh.
- Deletion: removed when the parent task is deleted (cascade).

---

### `achievements` table — `AchievementEntity`

Permanently stored award records (FR-011). One row per earned award instance.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | `Long` | PK autoGenerate | |
| `type` | `AchievementType` (TEXT) | non-null | enum stored as name string |
| `taskId` | `Long?` | nullable FK → tasks.id | set for streak-based awards; null for global awards |
| `earnedAt` | `Long` | non-null | timestamp of earning |
| `periodLabel` | `String?` | nullable | e.g., `"2026"` for Year Finisher |

**`AchievementType` enum values**:
| Value | Award name | Trigger |
|-------|-----------|---------|
| `STREAK_10` | 🌱 Budding Habit | currentStreak reaches 10 for any recurring task |
| `STREAK_30` | 🌿 Consistent Grower | currentStreak reaches 30 |
| `STREAK_100` | 🌳 Rooted Habit | currentStreak reaches 100 |
| `ON_TIME_10` | ⚡ On-Time Champion | 10 non-recurring tasks completed on or before dueDate |
| `EARLY_FINISH` | 🗓️ Early Finisher | 1 task completed strictly before dueDate |
| `YEAR_FINISHER` | 🏆 Year Finisher | 365 distinct calendar days with ≥ 1 completion in one year |

**Invariants**:
- `STREAK_10` / `STREAK_30` / `STREAK_100` keyed on `(type, taskId)` — one per task per milestone.
- `ON_TIME_10`, `EARLY_FINISH`, `YEAR_FINISHER` keyed on `(type, periodLabel)` — one per calendar year (or null for global).
- `UNIQUE INDEX` on `(type, taskId, periodLabel)` prevents duplicate awards.
- Rows are NEVER deleted after being written (permanent display requirement of FR-011).

---

## UI-Layer Value Types (not persisted)

### `AnalyticsPeriod`

```
sealed class AnalyticsPeriod
  ├── CurrentYear    (1 Jan – 31 Dec, current year)
  ├── Last12Months   (today − 364 days through today, inclusive)
  ├── SpecificYear(year: Int)  (1 Jan – 31 Dec of given year)
  └── Lifetime       (earliest TaskCompletionLog.date through today)
```

### `UrgencyLevel`

Derived from `(today - startDate) / (dueDate - startDate)`:
```
NONE      → task has no startDate or no dueDate, or is completed/overdue
GREEN     → 0–30% elapsed
YELLOW    → 30–70% elapsed
ORANGE    → 70–100% elapsed
```

---

## DB Migration: v5 → v6

```sql
-- 1. Extend tasks table
ALTER TABLE tasks ADD COLUMN scheduleMask INTEGER DEFAULT NULL;

-- 2. Create task_streaks table
CREATE TABLE IF NOT EXISTS task_streaks (
    taskId              INTEGER PRIMARY KEY NOT NULL,
    currentStreak       INTEGER NOT NULL DEFAULT 0,
    longestStreak       INTEGER NOT NULL DEFAULT 0,
    longestStreakStartDate INTEGER,
    lastUpdated         INTEGER NOT NULL
);

-- 3. Create achievements table
CREATE TABLE IF NOT EXISTS achievements (
    id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    type        TEXT NOT NULL,
    taskId      INTEGER,
    earnedAt    INTEGER NOT NULL,
    periodLabel TEXT
);

-- 4. Prevent duplicate achievements
CREATE UNIQUE INDEX IF NOT EXISTS idx_achievements_unique
    ON achievements(type, IFNULL(taskId, -1), IFNULL(periodLabel, ''));
```

---

## Repository Interface Additions

New methods added to `TaskRepository`:

```
// Reactive
fun getTodayProgress(): Flow<TodayProgressState>          // FR-001–FR-004
fun getUrgencyForTask(taskId: Long): Flow<UrgencyLevel>  // FR-005–FR-006 (or derived in VM)
fun getHeatMapData(startMs: Long, endMs: Long): Flow<Map<Long, Int>>  // FR-007–FR-009; ViewModel converts AnalyticsPeriod → (startMs, endMs) via period.toDateRange()
fun getForestData(startMs: Long, endMs: Long): Flow<Map<Long, List<String>>>  // FR-013–FR-014
fun getStreakForTask(taskId: Long): Flow<TaskStreakEntity?>  // FR-010
fun getAchievements(): Flow<List<AchievementEntity>>     // FR-011
fun getLifetimeStats(): Flow<LifetimeStats>              // FR-012
fun getCurrentYearStats(): Flow<CurrentYearStats>        // FR-012

// Commands
suspend fun updateLog(log: TaskCompletionLog)            // FR-015 history editing
suspend fun recalculateStreaks(taskId: Long)             // FR-017 post-edit recalc
suspend fun checkAndAwardAchievements(taskId: Long?)    // FR-011 milestone check
```
