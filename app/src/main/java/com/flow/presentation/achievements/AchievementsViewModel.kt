package com.flow.presentation.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * T011 / C-003 — ViewModel for the Achievements screen.
 *
 * Collects earned achievements from the repository and exposes them via [uiState].
 * Provides [toggleHowItWorks] to expand/collapse the "How Achievements Work" section.
 */
@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsUiState())
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()

    init { loadAchievements() }

    private fun loadAchievements() {
        viewModelScope.launch {
            repository.getAchievements().collect { earned ->
                _uiState.update { it.copy(earned = earned) }
            }
        }
    }

    /** Flips the "How Achievements Work" expanded state. */
    fun toggleHowItWorks() {
        _uiState.update { it.copy(isHowItWorksExpanded = !it.isHowItWorksExpanded) }
    }
}
