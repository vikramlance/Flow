package com.flow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val firstLaunchKey = booleanPreferencesKey("is_first_launch")
    private val hasSeenTutorialKey = booleanPreferencesKey("has_seen_tutorial")
    private val defaultTimerMinutesKey = intPreferencesKey("default_timer_minutes")

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[firstLaunchKey] ?: true }

    val hasSeenTutorial: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[hasSeenTutorialKey] ?: false }

    val defaultTimerMinutes: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[defaultTimerMinutesKey] ?: 25 }

    suspend fun setFirstLaunchCompleted() {
        context.dataStore.edit { preferences ->
            preferences[firstLaunchKey] = false
        }
    }

    suspend fun setTutorialSeen() {
        context.dataStore.edit { preferences ->
            preferences[hasSeenTutorialKey] = true
        }
    }

    suspend fun saveDefaultTimerMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[defaultTimerMinutesKey] = minutes
        }
    }
}
