package com.flow.presentation.achievements

import com.flow.data.local.AchievementType

/**
 * T004 — Presentation-layer metadata for achievement badges.
 *
 * Contains descriptions, visibility classification, emoji, and display names for every
 * [AchievementType]. This is the single source of truth for badge text — [AnalyticsScreen]
 * previously held [achievementEmoji] and [achievementName]; both are moved here.
 */
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

    /**
     * Returns the emoji string for a given achievement type.
     * Moved from [com.flow.presentation.analytics.AnalyticsScreen].
     * Uses encoding-safe `\uXXXX` surrogate-pair escapes (INV-04, updated 2026-02-28).
     */
    fun achievementEmoji(type: AchievementType): String = when (type) {
        AchievementType.STREAK_10     -> "\uD83C\uDF31" // 🌱
        AchievementType.STREAK_30     -> "\uD83C\uDF33" // 🌳
        AchievementType.STREAK_100    -> "\uD83C\uDFC6" // 🏆
        AchievementType.ON_TIME_10    -> "\u23F1\uFE0F" // ⏱️
        AchievementType.EARLY_FINISH  -> "\u26A1"       // ⚡
        AchievementType.YEAR_FINISHER -> "\uD83C\uDFAF" // 🎯
    }

    /**
     * Returns the display name for a given achievement type.
     * Moved from [com.flow.presentation.analytics.AnalyticsScreen].
     */
    fun achievementName(type: AchievementType): String = when (type) {
        AchievementType.STREAK_10     -> "Budding Habit (10 days)"
        AchievementType.STREAK_30     -> "Growing Strong (30 days)"
        AchievementType.STREAK_100    -> "Iron Will (100 days)"
        AchievementType.ON_TIME_10    -> "Punctual (10 on-time)"
        AchievementType.EARLY_FINISH  -> "Early Bird"
        AchievementType.YEAR_FINISHER -> "Year Finisher"
    }
}
