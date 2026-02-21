package com.flow.presentation.analytics

data class AnalyticsUiState(
    val heatMapData: Map<Long, Int> = emptyMap(),   // midnight-epoch → completion count
    val totalCompleted: Int = 0,
    val completedOnTime: Int = 0,
    val missedDeadlines: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
)
