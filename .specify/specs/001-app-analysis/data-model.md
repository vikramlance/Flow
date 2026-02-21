# Data Model: Flow — Productivity & Task Management App

**Branch**: `001-app-analysis` | **Phase**: 1 | **Date**: 2026-02-20

---

## Entities

### 1. `Task` (`tasks` table — existing, Room entity)

Represents a single unit of work created by the user.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `Long` | PK, auto-generate | Immutable after creation |
| `title` | `String` | NOT NULL, non-empty | User-supplied |
| `description` | `String` | NOT NULL, default `""` | Optional notes |
| `status` | `TaskStatus` | NOT NULL, default `TODO` | Enum: `TODO`, `IN_PROGRESS`, `COMPLETED` |
| `startDate` | `Long` | NOT NULL | Epoch ms, midnight of start day; defaults to `now()` |
| `dueDate` | `Long?` | Nullable | Epoch ms of target deadline |
| `isRecurring` | `Boolean` | NOT NULL, default `false` | Marks daily-repeating tasks |
| `createdAt` | `Long` | NOT NULL | Epoch ms, set at insert, immutable |
| `completionTimestamp` | `Long?` | Nullable | Set exactly once when `status → COMPLETED`; immutable thereafter |

**Derived state (not stored)**:
- `isOverdue` = `dueDate != null && dueDate < now() && status != COMPLETED`
- `displayColor` = green (COMPLETED) | yellow (IN_PROGRESS) | orange (overdue) | default (TODO)

**Invariants**:
- `completionTimestamp` MUST only be set when transition is `→ COMPLETED`; it MUST NOT be cleared or overwritten on subsequent edits.
- `startDate` MUST be ≤ `dueDate` when both are provided.
- `status` transitions are: `TODO → IN_PROGRESS`, `TODO → COMPLETED`, `IN_PROGRESS → COMPLETED`, `COMPLETED → TODO` (undo). No other transitions permitted.

**Schema version**: 4 (current). Any new field requires `version = 5` + `Migration(4, 5)`.

---

### 2. `TaskCompletionLog` (`task_logs` table — existing, Room entity)

Records one completion event for a recurring task on a specific calendar day.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `Long` | PK, auto-generate | |
| `taskId` | `Long` | NOT NULL, FK → `tasks.id` | Cascade delete when parent task is deleted |
| `date` | `Long` | NOT NULL | Epoch ms of **midnight** of the calendar day (local timezone) |
| `isCompleted` | `Boolean` | NOT NULL | `true` = marked done; `false` = explicitly marked undone |
| `timestamp` | `Long` | NOT NULL, default `now()` | Wall-clock time of the write; immutable |

**Invariants**:
- Only one log per `(taskId, date)` pair should be active. The DAO uses `OnConflictStrategy.REPLACE` which is acceptable; prefer an explicit upsert.
- `date` MUST always be the **midnight** epoch ms of the local calendar day — never the raw `System.currentTimeMillis()`.
- Cascade delete: when a `Task` is deleted, ALL its `TaskCompletionLog` rows MUST be deleted atomically.

**Streak calculation** (derived, not stored):
```
streak(taskId) = count of consecutive calendar days ending at today
                 where a TaskCompletionLog row exists with isCompleted = true
```

---

### 3. `DailyProgress` (`daily_progress` table — existing, Room entity)

Aggregate summary for one calendar day. Powers the main heat map and the daily progress indicator.

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `date` | `Long` | PK | Epoch ms of **midnight** of the calendar day (local TZ) |
| `tasksCompletedCount` | `Int` | NOT NULL, default 0, ≥ 0 | Count of tasks completed on this day |
| `tasksTotalCount` | `Int` | NOT NULL, default 0, ≥ 0 | Count of tasks active (startDate ≤ day) that day |

**Invariants**:
- `tasksCompletedCount` MUST NOT exceed `tasksTotalCount`.
- Row for a given `date` MUST be upserted whenever a task completion state changes for that day — NOT inserted as a duplicate.
- `date` MUST be midnight epoch (same normalisation as `TaskCompletionLog.date`).

**Heat map colour derivation** (not stored):
```
ratio = tasksCompletedCount / tasksTotalCount
0.0        → no activity (grey/empty)
0.0–0.33   → low (light green)
0.33–0.66  → medium (mid green)
0.66–1.0   → high (dark green)
any day with overdue tasks (derived from Task table) → orange tint overlay
```

---

### 4. `AppSettings` (DataStore Preferences — existing)

Lightweight key-value store for app-state flags and user preferences.

| Key | Type | Default | Purpose |
|---|---|---|---|
| `is_first_launch` | `Boolean` | `true` | Controls whether onboarding auto-starts |
| `has_seen_tutorial` | `Boolean` | `false` | Controls tutorial re-play availability |
| `default_timer_minutes` | `Int` | `25` | Remembered last-used timer duration |

**Location**: `SettingsManager` (existing singleton, injected via Hilt).

---

## State Transitions

### Task Status FSM

```
┌─────────────────────────────────────────────────┐
│                                                 │
│   ┌──────┐  start work   ┌─────────────┐        │
│   │  TODO│ ──────────── ▶│ IN_PROGRESS │        │
│   └──────┘               └─────────────┘        │
│      │                         │                │
│      │ complete directly        │ complete       │
│      ▼                         ▼                │
│   ┌────────────────────────────────┐            │
│   │           COMPLETED           │            │
│   └────────────────────────────────┘            │
│           │ undo                                │
│           └────────────────────────▶ TODO       │
└─────────────────────────────────────────────────┘
```

- `completionTimestamp` is SET on any transition `→ COMPLETED`.
- `completionTimestamp` is CLEARED on `COMPLETED → TODO` (undo).
- For **recurring** tasks: `completionTimestamp` on the `Task` entity tracks
  the most recent completion only. Full history lives in `TaskCompletionLog`.

---

## Relationships

```
Task (1) ──────────────── (0..N) TaskCompletionLog
  └── recurring=true tasks only; logs deleted on task delete

DailyProgress — computed from Task + TaskCompletionLog; no FK constraint
AppSettings   — standalone; no FK
```

---

## Migration Plan

### Current: version 4 → Next: version 5

Required additions:
- `AppSettings`: add `has_seen_tutorial` and `default_timer_minutes` keys to DataStore (no Room migration needed — DataStore is schemaless).
- `Task` table: no new columns planned for this iteration.
- `task_logs`: add explicit UNIQUE constraint on `(taskId, date)` to prevent duplicates.

```sql
-- Migration 4 → 5
-- Add unique index to prevent duplicate logs per (taskId, date)
CREATE UNIQUE INDEX IF NOT EXISTS idx_task_logs_task_date
  ON task_logs(taskId, date);
```

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_task_logs_task_date
            ON task_logs(taskId, date)
        """)
    }
}
```

Remove `fallbackToDestructiveMigration()` and register `MIGRATION_4_5` in `AppModule.provideAppDatabase`.

---

## UI State Models (per screen)

Concrete `UiState` objects, derived from DB streams by each ViewModel.

### `HomeUiState`
```
data class HomeUiState(
    val tasks: List<TaskEntity>   = emptyList(),   // active tasks, sorted
    val todayProgress: Float      = 0f,            // 0.0–1.0
    val isFirstLaunch: Boolean    = false,
    val isLoading: Boolean        = true,
    val error: String?            = null           // non-null triggers error banner
)
```

### `AnalyticsUiState`
```
data class AnalyticsUiState(
    val heatMapData: Map<Long, Int>     = emptyMap(),   // midnight-epoch → completion count
    val totalCompleted: Int              = 0,
    val completedOnTime: Int             = 0,
    val missedDeadlines: Int             = 0,
    val currentStreak: Int               = 0,
    val bestStreak: Int                  = 0,
    val isLoading: Boolean               = true,
    val error: String?                   = null
)
```

### `TimerUiState`
```
data class TimerUiState(
    val durationSeconds: Int    = 25 * 60,
    val remainingSeconds: Int   = 25 * 60,
    val isRunning: Boolean      = false,
    val isPaused: Boolean       = false,
    val isFinished: Boolean     = false    // triggers sound + message
)
```

### `SettingsUiState`
```
data class SettingsUiState(
    val defaultTimerMinutes: Int    = 25,
    val isLoading: Boolean          = false,
    val error: String?              = null
)
```
