package com.flow.presentation.timer

import android.os.SystemClock
import com.flow.fake.FakeSettingsRepository
import com.flow.utils.MainDispatcherRule
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * T033/US7 -- Unit tests for TimerViewModel settings reactivity.
 *
 * Verifies FR-014: Timer duration updates immediately when settings change
 * while the timer is idle (not running and not paused).
 *
 * Note: tests that call viewModel.start() are excluded because the timer
 * uses an infinite while(true)+delay(250) loop in scheduleTick(); calling
 * advanceUntilIdle() after start() would drain the loop infinitely (OOM).
 * Those scenarios are covered by the Tier-2 instrumented test suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeSettings: FakeSettingsRepository
    private lateinit var viewModel: TimerViewModel

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 0L
        fakeSettings = FakeSettingsRepository()
        fakeSettings.defaultTimerMinutesFlow.value = 25
        viewModel = TimerViewModel(fakeSettings)
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    // -- T033: idle state reacts to settings changes --------------------------

    @Test
    fun settingsChange_whenTimerIsIdle_updatesDisplayedDuration() = runTest {
        advanceUntilIdle()
        assertEquals(25 * 60, viewModel.uiState.value.remainingSeconds)
        fakeSettings.defaultTimerMinutesFlow.value = 10
        advanceUntilIdle()
        assertEquals(
            "Duration should update to new settings value when timer is idle",
            10 * 60,
            viewModel.uiState.value.remainingSeconds
        )
    }

    @Test
    fun initialDuration_matchesDefaultTimerMinutesSetting() = runTest {
        fakeSettings.defaultTimerMinutesFlow.value = 45
        val freshVm = TimerViewModel(fakeSettings)
        advanceUntilIdle()
        assertEquals(45 * 60, freshVm.uiState.value.remainingSeconds)
    }
}
