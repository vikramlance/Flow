package com.flow.presentation.analytics

import com.flow.data.local.AchievementEntity
import com.flow.data.local.TaskStreakEntity
import com.flow.data.repository.CurrentYearStats
import com.flow.data.repository.LifetimeStats

/**
 * T025 + T030 + T037 — Analytics screen UI state.
 *
 * Fields added sequentially:
 *  - T025: period selector, heatmap, stats, availableYears, isLoading
 *  - T030: streaks + achievements (gamification)
 *  - T037: forestData + forestTreeCount (forest concept)
 */
data class AnalyticsUiState(
    // ── T025: period & heatmap ────────────────────────────────────────────
    val selectedPeriod: AnalyticsPeriod     = AnalyticsPeriod.CurrentYear,
    val heatMapData: Map<Long, Int>          = emptyMap(),
    val lifetimeStats: LifetimeStats?        = null,
    val currentYearStats: CurrentYearStats?  = null,
    val availableYears: List<Int>            = emptyList(),
    val isLoading: Boolean                   = true,
    val error: String?                       = null,

    // ── Legacy fields (non-regression) ───────────────────────────────────
    val totalCompleted: Int    = 0,
    val completedOnTime: Int   = 0,
    val missedDeadlines: Int   = 0,
    val currentStreak: Int     = 0,
    val bestStreak: Int        = 0,

    // ── T030: gamification ────────────────────────────────────────────────
    val streaks: List<TaskStreakEntity>    = emptyList(),
    val achievements: List<AchievementEntity> = emptyList(),

    // ── T037: forest concept ──────────────────────────────────────────────
    val forestData: Map<Long, List<String>> = emptyMap(),
    val forestTreeCount: Int                = 0
)
