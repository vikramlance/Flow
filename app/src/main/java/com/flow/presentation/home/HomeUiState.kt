package com.flow.presentation.home

import com.flow.data.local.TaskEntity

data class HomeUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val todayProgress: Float = 0f,       // 0.0 – 1.0
    val isFirstLaunch: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null            // non-null triggers error banner
)
