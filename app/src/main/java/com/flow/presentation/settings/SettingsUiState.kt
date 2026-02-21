package com.flow.presentation.settings

data class SettingsUiState(
    val defaultTimerMinutes: Int = 25,
    val isLoading: Boolean = false,
    val error: String? = null
)
