package com.vikra.willard.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vikra.willard.data.local.DailyProgressEntity
import com.vikra.willard.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    val history: StateFlow<List<DailyProgressEntity>> = repository.getHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentStreak: StateFlow<Int> = repository.getCompletedTaskCount() // Placeholder, need streak logic
        .stateIn(scope = viewModelScope, started = SharingStarted.Lazily, initialValue = 0)

    // Actually, calculateStreak is a suspend function in Repo.
    // We should expose a StateFlow.
    
    // Better implementation:
    // We can compute streak from history flow.
}
