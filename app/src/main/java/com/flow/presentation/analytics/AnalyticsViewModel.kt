package com.flow.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: TaskRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow<AnalyticsPeriod>(AnalyticsPeriod.CurrentYear)

    /** Expose the current period for the UI to read (e.g., for the year chip label). */
    val selectedPeriod: StateFlow<AnalyticsPeriod> = _selectedPeriod

    fun onPeriodSelected(period: AnalyticsPeriod) {
        _selectedPeriod.value = period
    }

    // ── Earliest log (used for Lifetime start + availableYears) ─────────────
    private val earliestLogMs: kotlinx.coroutines.flow.Flow<Long> = flow {
        emit(repository.getEarliestCompletionDate() ?: 0L)
    }

    // ── Period-aware heatmap + forest data ──────────────────────────────────
    private val periodData = combine(_selectedPeriod, earliestLogMs) { period, earliest ->
        period to earliest
    }.flatMapLatest { (period, earliest) ->
        val (startMs, endMs) = period.toDateRange(earliestLogMs = earliest)
        combine(
            repository.getHeatMapData(startMs, endMs),
            repository.getForestData(startMs, endMs)
        ) { heatMap, forest ->
            Triple(heatMap, forest, startMs to endMs)
        }
    }

    // ── Main state ───────────────────────────────────────────────────────────
    val uiState: StateFlow<AnalyticsUiState> = combine(
        _selectedPeriod,
        earliestLogMs,
        periodData,
        repository.getAchievements(),
        repository.getCompletedTaskCount()
    ) { period, earliest, (heatMap, forest, _), achievements, totalCompleted ->
        val (startMs, endMs) = period.toDateRange(earliestLogMs = earliest)

        // Compute available years from earliest log year through now
        val nowYear   = Calendar.getInstance().get(Calendar.YEAR)
        val startYear = if (earliest > 0L) {
            Calendar.getInstance().apply { timeInMillis = earliest }.get(Calendar.YEAR)
        } else nowYear
        val availableYears = (startYear..nowYear).toList()

        // Aggregate stats (suspend calls — run here in combine's context)
        // These are fast in-memory computations via DAO counts
        val completedOnTime   = repository.getCompletedOnTimeCount()
        val missedDeadlines   = repository.getMissedDeadlineCount()
        val currentStreakVal  = repository.calculateCurrentStreak()
        val bestStreak        = repository.getBestStreak()
        val lifetimeStats     = repository.getLifetimeStats()
        val currentYearStats  = repository.getCurrentYearStats()

        val forestTreeCount = forest.values.sumOf { it.size }

        AnalyticsUiState(
            selectedPeriod   = period,
            heatMapData      = heatMap,
            lifetimeStats    = lifetimeStats,
            currentYearStats = currentYearStats,
            availableYears   = availableYears,
            isLoading        = false,
            // Legacy fields
            totalCompleted   = totalCompleted,
            completedOnTime  = completedOnTime,
            missedDeadlines  = missedDeadlines,
            currentStreak    = currentStreakVal,
            bestStreak       = bestStreak,
            // T030
            achievements     = achievements,
            // T037
            forestData       = forest,
            forestTreeCount  = forestTreeCount
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalyticsUiState()
    )
}

