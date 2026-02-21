package com.flow.presentation.timer

data class TimerUiState(
    val durationSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false    // triggers audible alert + "Time is up" message
)
