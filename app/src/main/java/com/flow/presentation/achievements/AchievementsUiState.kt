package com.flow.presentation.achievements

import com.flow.data.local.AchievementEntity

/**
 * T006 — UI state for [AchievementsScreen].
 *
 * @param earned                All earned [AchievementEntity] rows from the database,
 *                              sorted by [AchievementEntity.earnedAt] descending.
 * @param isHowItWorksExpanded  Whether the "How Achievements Work" expandable section
 *                              is currently open.
 */
data class AchievementsUiState(
    val earned: List<AchievementEntity> = emptyList(),
    val isHowItWorksExpanded: Boolean = false
)
