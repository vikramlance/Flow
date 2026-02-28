# Data Model — Feature 004: Task UI Polish & Scheduling

**Branch**: `004-task-ui-scheduling` | **Date**: 2026-02-28  
**Phase**: 1 — Design

---

## Summary

No database schema changes in this feature. All new types live in the presentation layer.
`scheduleMask` already exists in `tasks` table (added feature 002). No `@Database` version bump.

---

## Existing Entities (unchanged schema)

### `TaskEntity` (`data/local/TaskEntity.kt`)

No column changes.

| Field | Type | Relevant notes |
|-------|------|----------------|
| `scheduleMask` | `Int?` | `null` = every day. `1–127` = bitmask bit 0=Mon … bit 6=Sun. Already exists. |
| `startDate` | `Long` | UTC millis. Default behaviour changes in `TaskRepositoryImpl.addTask()` — see contracts. |
| `dueDate` | `Long?` | UTC millis. Dialog default changes to today 23:59 — no schema change. |

### `AchievementEntity` (`data/local/AchievementEntity.kt`)

No column changes. Descriptions and visibility are presentation-layer constants.

### `AchievementType` (`data/local/AchievementType.kt`)

No enum changes. Used as key in new `AchievementMeta` object.

---

## New Types — Presentation Layer Only

### `AchievementVisibility` (new enum)

**File**: `presentation/achievements/AchievementVisibility.kt`

```kotlin
enum class AchievementVisibility {
    /** Shown to user before earning — name and criteria visible. */
    VISIBLE,
    /** Hidden until earned — shows "??? — Keep going!" placeholder. */
    HIDDEN
}
```

### `AchievementMeta` (new object)

**File**: `presentation/achievements/AchievementMeta.kt`

```kotlin
object AchievementMeta {
    /**
     * One-sentence description of how each achievement is earned.
     * Displayed on earned badge cards and in the "How Achievements Work" section.
     */
    val descriptions: Map<AchievementType, String> = mapOf(
        AchievementType.STREAK_10     to "Complete a recurring task 10 days in a row.",
        AchievementType.STREAK_30     to "Complete a recurring task 30 days in a row.",
        AchievementType.STREAK_100    to "Complete a recurring task 100 days in a row.",
        AchievementType.ON_TIME_10    to "Complete 10 tasks on or before their target date.",
        AchievementType.EARLY_FINISH  to "Complete any task before its target date.",
        AchievementType.YEAR_FINISHER to "Record completions on 365 distinct days within one calendar year."
    )

    /**
     * Achievement types that are intentionally hidden until earned.
     * Their criteria are never shown to the user before unlocking.
     */
    val hiddenTypes: Set<AchievementType> = setOf(
        AchievementType.YEAR_FINISHER
    )

    /** Returns the visibility of a given achievement type. */
    fun visibilityOf(type: AchievementType): AchievementVisibility =
        if (type in hiddenTypes) AchievementVisibility.HIDDEN
        else AchievementVisibility.VISIBLE
}
```

### `AchievementsUiState` (new data class)

**File**: `presentation/achievements/AchievementsUiState.kt`

```kotlin
data class AchievementsUiState(
    /** All earned achievements from the database. */
    val earned: List<AchievementEntity> = emptyList(),
    /** Whether the "How Achievements Work" section is expanded. */
    val isHowItWorksExpanded: Boolean = false
)
```

### DateUtils additions

**File**: `util/DateUtils.kt` (additions only — no existing code changed)

```kotlin
/**
 * Returns epoch millis for today at 23:59:00.000 in the device's local timezone.
 * Used as the default end time when the New Task / Edit Task dialog opens.
 */
fun defaultEndTime(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Returns epoch millis for a given [dateMillis] calendar day at 23:59:00.000 local time.
 * Used when the user picks a different date — time stays at end-of-day.
 */
fun endTimeForDate(dateMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = dateMillis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
```

---

## Removed / Relocated Logic

| Item | Current location | New location | Reason |
|------|-----------------|-------------|--------|
| `AchievementsSection` composable | `AnalyticsScreen.kt` lines 288–327 | `presentation/achievements/AchievementsScreen.kt` | US2: dedicated screen |
| `achievementEmoji()` | `AnalyticsScreen.kt` | `presentation/achievements/AchievementMeta.kt` | Centralise achievement metadata |
| `achievementName()` | `AnalyticsScreen.kt` | `presentation/achievements/AchievementMeta.kt` | Same; also add `descriptions` |

---

## State Transitions Updated

### Task default times on creation

```
Dialog opens
  └─ isRecurring = false OR first creation of recurring:
       startDate = System.currentTimeMillis()   ← exact clock time
       dueDate   = defaultEndTime()             ← today 23:59

  └─ On save → addTask() stores startDate/dueDate as passed (timezone-normalised only)

Daily refresh (refreshRecurringTasks):
  └─ startDate = todayMidnight + 60_000L        ← 12:01 AM
       dueDate = todayMidnight + 86_340_000L    ← 11:59 PM
```

### Schedule mask validation

```
isRecurring = true
  └─ scheduleMask = null  → treated as 127 (all days) on save
  └─ scheduleMask = 0     → BLOCKED by ViewModel validation (error shown)
  └─ scheduleMask in 1..127 → saved as-is

isRecurring = false
  └─ scheduleMask always saved as null
```
