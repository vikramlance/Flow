package com.flow.presentation.home

import com.flow.data.local.TaskEntity
import com.flow.data.repository.TodayProgressState
import com.flow.fake.FakeSettingsRepository
import com.flow.fake.FakeTaskRepository
import com.flow.presentation.home.HomeViewModel
import com.flow.utils.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * TU03 — Unit tests for [HomeViewModel].
 * Verifies that [HomeUiState.homeTasks] is populated from [getHomeScreenTasks],
 * and that [showHelp] / [hideHelp] correctly toggle [HomeUiState.showHelp].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeTaskRepository
    private lateinit var fakeSettings: FakeSettingsRepository

    private val seedTask = TaskEntity(id = 1L, title = "Seed Task")

    @Before
    fun setUp() {
        fakeRepo = FakeTaskRepository()
        fakeSettings = FakeSettingsRepository()
        // Pre-populate so dummy-data injection in init is skipped
        fakeRepo.allTasksFlow.value = listOf(seedTask)
    }

    // ── TU03-a: homeTasks populated from getHomeScreenTasks ──────────────────

    @Test
    fun `homeTasks reflects getHomeScreenTasks flow`() = runTest {
        val tasks = listOf(
            TaskEntity(id = 1L, title = "Recurring", isRecurring = true),
            TaskEntity(id = 2L, title = "Today Task")
        )
        fakeRepo.homeScreenTasksFlow.value = tasks

        val vm = HomeViewModel(fakeRepo, fakeSettings)
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(tasks, vm.uiState.value.homeTasks.map { it.task })
        job.cancel()
    }

    @Test
    fun `homeTasksFlow update propagates to uiState`() = runTest {
        fakeRepo.homeScreenTasksFlow.value = listOf(seedTask)
        val vm = HomeViewModel(fakeRepo, fakeSettings)
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.homeTasks.size)

        // Push a new task into the flow
        val updatedList = listOf(seedTask, TaskEntity(id = 99L, title = "New"))
        fakeRepo.homeScreenTasksFlow.value = updatedList
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.homeTasks.size)
        job.cancel()
    }

    @Test
    fun `todayProgress is reflected in uiState`() = runTest {
        fakeRepo.todayProgressFlow.value = TodayProgressState(totalToday = 4, completedToday = 3)
        val vm = HomeViewModel(fakeRepo, fakeSettings)
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(0.75f, vm.uiState.value.todayProgress, 0.001f)
        job.cancel()
    }

    // ── TU03-b: showHelp / hideHelp ──────────────────────────────────────────

    @Test
    fun `showHelp sets uiState showHelp to true`() = runTest {
        val vm = HomeViewModel(fakeRepo, fakeSettings)
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showHelp)

        vm.showHelp()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showHelp)
        job.cancel()
    }

    @Test
    fun `hideHelp sets uiState showHelp to false`() = runTest {
        val vm = HomeViewModel(fakeRepo, fakeSettings)
        val job = launch { vm.uiState.collect { } }
        vm.showHelp()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showHelp)

        vm.hideHelp()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showHelp)
        job.cancel()
    }

    // ── TU03-c: isFirstLaunch / completeOnboarding ───────────────────────────

    @Test
    fun `isFirstLaunch propagates to uiState`() = runTest {
        fakeSettings.isFirstLaunchFlow.value = true
        val vm = HomeViewModel(fakeRepo, fakeSettings)
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isFirstLaunch)

        vm.completeOnboarding()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isFirstLaunch)
        job.cancel()
    }

    // ── TU03-d: initial loading state ────────────────────────────────────────

    @Test
    fun `initial uiState has isLoading true`() {
        val vm = HomeViewModel(fakeRepo, fakeSettings)
        // Before any emission, the StateFlow returns its initialValue
        assertTrue(vm.uiState.value.isLoading)
    }

    @Test
    fun `uiState isLoading becomes false after first emission`() = runTest {
        val vm = HomeViewModel(fakeRepo, fakeSettings)
        val job = launch { vm.uiState.collect { } }
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        job.cancel()
    }
}
