package com.flow.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    val uiState: StateFlow<AnalyticsUiState> = combine(
        repository.getHeatMapData(),
        repository.getCompletedTaskCount()
    ) { heatMap, totalCompleted ->
        val completedOnTime  = repository.getCompletedOnTimeCount()
        val missedDeadlines  = repository.getMissedDeadlineCount()
        val currentStreak    = repository.calculateCurrentStreak()
        val bestStreak       = repository.getBestStreak()
        AnalyticsUiState(
            heatMapData      = heatMap,
            totalCompleted   = totalCompleted,
            completedOnTime  = completedOnTime,
            missedDeadlines  = missedDeadlines,
            currentStreak    = currentStreak,
            bestStreak       = bestStreak,
            isLoading        = false
        )
    }.stateIn(
        scope   = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState()
    )
}
