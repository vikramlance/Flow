package com.flow.data.repository

import com.flow.data.local.SettingsManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsManager: SettingsManager
) : SettingsRepository {

    override val isFirstLaunch: Flow<Boolean>
        get() = settingsManager.isFirstLaunch

    override val hasSeenTutorial: Flow<Boolean>
        get() = settingsManager.hasSeenTutorial

    override val defaultTimerMinutes: Flow<Int>
        get() = settingsManager.defaultTimerMinutes

    override suspend fun setFirstLaunchCompleted() =
        settingsManager.setFirstLaunchCompleted()

    override suspend fun setTutorialSeen() =
        settingsManager.setTutorialSeen()

    override suspend fun saveDefaultTimerMinutes(minutes: Int) =
        settingsManager.saveDefaultTimerMinutes(minutes)
}
