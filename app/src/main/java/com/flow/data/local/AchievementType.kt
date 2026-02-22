package com.flow.data.local

/**
 * T005 — Award types for the gamification system.
 */
enum class AchievementType {
    /** Awarded when a recurring task reaches a 10-day streak. */
    STREAK_10,
    /** Awarded when a recurring task reaches a 30-day streak. */
    STREAK_30,
    /** Awarded when a recurring task reaches a 100-day streak. */
    STREAK_100,
    /** Awarded when 10 non-recurring tasks are completed on or before their due date. */
    ON_TIME_10,
    /** Awarded when any task is completed strictly before its due date. */
    EARLY_FINISH,
    /** Awarded when completions are recorded on 365 distinct calendar days within one calendar year. */
    YEAR_FINISHER
}
