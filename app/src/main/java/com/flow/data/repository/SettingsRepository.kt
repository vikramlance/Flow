package com.flow.data.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Emits true until the user has completed onboarding. */
    val isFirstLaunch: Flow<Boolean>

    /** Emits true if the user has NOT yet seen/completed the tutorial. */
    val hasSeenTutorial: Flow<Boolean>

    /** Last-used timer duration in minutes. */
    val defaultTimerMinutes: Flow<Int>

    suspend fun setFirstLaunchCompleted()
    suspend fun setTutorialSeen()
    suspend fun saveDefaultTimerMinutes(minutes: Int)
}
