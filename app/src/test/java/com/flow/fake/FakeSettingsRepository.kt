package com.flow.fake

import com.flow.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake [SettingsRepository] for JVM unit tests.
 */
class FakeSettingsRepository : SettingsRepository {

    val isFirstLaunchFlow = MutableStateFlow(false)
    val hasSeenTutorialFlow = MutableStateFlow(true)
    val defaultTimerMinutesFlow = MutableStateFlow(25)

    override val isFirstLaunch: Flow<Boolean> = isFirstLaunchFlow
    override val hasSeenTutorial: Flow<Boolean> = hasSeenTutorialFlow
    override val defaultTimerMinutes: Flow<Int> = defaultTimerMinutesFlow

    override suspend fun setFirstLaunchCompleted() { isFirstLaunchFlow.value = false }
    override suspend fun setTutorialSeen() { hasSeenTutorialFlow.value = false }
    override suspend fun saveDefaultTimerMinutes(minutes: Int) { defaultTimerMinutesFlow.value = minutes }
}
