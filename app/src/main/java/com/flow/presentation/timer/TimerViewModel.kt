package com.flow.presentation.timer

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    /** Elapsed-realtime anchor set when timer starts or resumes (for ±1 s accuracy, SC-006). */
    private var startAnchorMs: Long = 0L

    /** Remaining seconds captured at the moment of the most recent start/resume. */
    private var remainingAtAnchor: Int = 0

    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            val defaultMinutes = settingsRepository.defaultTimerMinutes.firstOrNull() ?: 25
            val seconds = defaultMinutes * 60
            _uiState.update { it.copy(durationSeconds = seconds, remainingSeconds = seconds) }
        }
    }

    fun setDuration(seconds: Int) {
        if (_uiState.value.isRunning) return
        _uiState.update {
            it.copy(durationSeconds = seconds, remainingSeconds = seconds, isFinished = false, isPaused = false)
        }
    }

    fun start() {
        val s = _uiState.value
        if (s.isRunning || s.isFinished || s.remainingSeconds <= 0) return
        startAnchorMs = SystemClock.elapsedRealtime()
        remainingAtAnchor = s.remainingSeconds
        _uiState.update { it.copy(isRunning = true, isPaused = false, isFinished = false) }
        scheduleTick()
    }

    fun pause() {
        if (!_uiState.value.isRunning) return
        tickJob?.cancel()
        // Compute accurate remaining based on elapsed time since anchor
        val elapsed = ((SystemClock.elapsedRealtime() - startAnchorMs) / 1000).toInt()
        val remaining = maxOf(0, remainingAtAnchor - elapsed)
        _uiState.update { it.copy(isRunning = false, isPaused = true, remainingSeconds = remaining) }
    }

    fun resume() {
        val s = _uiState.value
        if (!s.isPaused || s.isFinished) return
        startAnchorMs = SystemClock.elapsedRealtime()
        remainingAtAnchor = s.remainingSeconds
        _uiState.update { it.copy(isRunning = true, isPaused = false) }
        scheduleTick()
    }

    fun reset() {
        tickJob?.cancel()
        val duration = _uiState.value.durationSeconds
        _uiState.update {
            it.copy(remainingSeconds = duration, isRunning = false, isPaused = false, isFinished = false)
        }
    }

    fun dismiss() {
        tickJob?.cancel()
        val duration = _uiState.value.durationSeconds
        _uiState.update {
            TimerUiState(durationSeconds = duration, remainingSeconds = duration)
        }
    }

    private fun scheduleTick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(250) // Poll every 250 ms for smooth display
                val elapsed = ((SystemClock.elapsedRealtime() - startAnchorMs) / 1000).toInt()
                val remaining = maxOf(0, remainingAtAnchor - elapsed)
                _uiState.update { it.copy(remainingSeconds = remaining) }
                if (remaining == 0) {
                    _uiState.update { it.copy(isRunning = false, isFinished = true) }
                    break
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
    }
}
